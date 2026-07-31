package maxigregrze.cobblesafari.csmusic;

import maxigregrze.cobblesafari.config.DimensionalMusicConfig;
import maxigregrze.cobblesafari.config.DimensionalMusicData;
import maxigregrze.cobblesafari.network.SetCsMusicPayload;
import maxigregrze.cobblesafari.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Server-side music arbitration. Per player, picks the winning track between the boss override
 * (which <b>always</b> wins) and the set of trigger rules + areas (highest priority, deterministic
 * tiebreak). Server-authoritative; sends a packet only on change, choosing a transition
 * (fade / outro / cut / synced-crossfade when the winner is the child of the current track).
 *
 * <p>The periodic sweep runs every {@code arbitrationIntervalTicks} ticks - transitions fade over
 * ~1 s, so a few ticks of granularity are inaudible. Boss transitions bypass the cadence entirely
 * and update immediately.</p>
 */
public final class DimensionalMusicManager {

    private static final Random RANDOM = new Random();
    private static final int DEFAULT_ARBITRATION_INTERVAL = 5;

    /** Everything the arbiter remembers about one player. Removed wholesale on disconnect. */
    private static final class PlayerState {
        /** Last csmusic id sent ("" = silence, null = nothing sent yet). */
        @Nullable String lastSent;
        /** "Boss" override (csmusic id) while the fight lasts. */
        @Nullable String bossOverride;
        /** Exit mode to apply on the next send (otherwise chosen). One-shot. */
        @Nullable Integer nextMode;
        /** Under a {@code /csmusic debug} override: normal arbitration is suspended. */
        boolean debug;
        /** Last dimension seen, to hard-cut (not fade) across a dimension change. */
        @Nullable String lastDimension;
        /** Latch for random tag pools: the winning source key and the id it resolved to. */
        @Nullable String latchedKey;
        @Nullable String latchedId;
    }

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private DimensionalMusicManager() {}

    public record SourceInfo(String source, String csmusicId, int priority, boolean winner) {}

    private record MusicSource(String key, int priority, @Nullable String musicId, @Nullable String poolTag) {}

    private static final Comparator<MusicSource> SOURCE_ORDER =
            Comparator.comparingInt(MusicSource::priority).reversed().thenComparing(MusicSource::key);

    private static PlayerState state(UUID uuid) {
        return STATES.computeIfAbsent(uuid, k -> new PlayerState());
    }

    // --- Per-tick sweep --------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if ((server.getTickCount() % arbitrationInterval()) != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayer(player);
        }
    }

    private static int arbitrationInterval() {
        DimensionalMusicData cfg = DimensionalMusicConfig.data;
        int interval = cfg == null ? DEFAULT_ARBITRATION_INTERVAL : cfg.arbitrationIntervalTicks;
        return Math.max(1, interval);
    }

    private static void updatePlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerState st = state(uuid);
        if (st.debug) {
            return; // debug override: leave whatever the debug command last sent untouched
        }
        String dimNow = player.level().dimension().location().toString();
        String dimPrev = st.lastDimension;
        st.lastDimension = dimNow;
        boolean dimChanged = dimPrev != null && !dimPrev.equals(dimNow);

        // Failsafe: a boss override always wins arbitration, so a missed end-of-fight hook would
        // pin this player's music forever. Drop it as soon as they are no longer in a fight.
        if (st.bossOverride != null
                && !maxigregrze.cobblesafari.csboss.BossBattleManager.isParticipant(uuid)) {
            st.bossOverride = null;
        }
        CsMusicDefinition boss = CsMusicRegistry.get(st.bossOverride).orElse(null);
        CsMusicDefinition winner = boss != null ? boss : resolveWinner(player, st);

        String desiredId = winner != null ? winner.id() : "";
        if (Objects.equals(st.lastSent, desiredId)) {
            st.nextMode = null;
            return;
        }

        CsMusicDefinition prev = CsMusicRegistry.get(st.lastSent).orElse(null);
        int mode;
        if (dimChanged && prev != null) {
            // Crossing a dimension boundary (portal, dungeon exit, cross-dim /tp) is a discontinuity:
            // hard-cut the outgoing track. Entering from silence still fades in.
            mode = SetCsMusicPayload.MODE_CUT;
        } else if (st.nextMode != null) {
            mode = st.nextMode;
        } else if (crossfadeRelated(prev, winner)) {
            mode = SetCsMusicPayload.MODE_CROSSFADE; // musically related tracks: synced crossfade
        } else {
            mode = defaultExitMode(prev, winner);
        }
        st.nextMode = null;
        send(player, st, winner, mode);
    }

    // --- Winner resolution -----------------------------------------------------

    @Nullable
    private static CsMusicDefinition resolveWinner(ServerPlayer player, PlayerState st) {
        if (!musicEnabled()) {
            clearLatch(st);
            return null;
        }
        List<MusicSource> sources = collectSources(player);
        if (sources.isEmpty()) {
            clearLatch(st);
            return null;
        }
        sources.sort(SOURCE_ORDER);
        for (MusicSource source : sources) {
            CsMusicDefinition def = resolveSource(st, source);
            if (def != null) {
                return def;
            }
        }
        clearLatch(st);
        return null;
    }

    private static List<MusicSource> collectSources(ServerPlayer player) {
        return collectSources(new CsMusicEvalContext(player));
    }

    private static List<MusicSource> collectSources(CsMusicEvalContext ctx) {
        List<MusicSource> list = new ArrayList<>();
        // Areas first: rules may carry an area axis, and the very same resolved list then feeds the
        // implicit sources below - the area store is walked once per sweep, as before. Insertion
        // order is irrelevant, SOURCE_ORDER sorts by priority then key.
        for (CsMusicArea area : ctx.areasHere()) {
            if (area.hasMusic()) {
                list.add(new MusicSource("area:" + area.id(), area.priority(), area.musicId(), null));
            }
        }
        for (CsMusicRule rule : CsMusicTriggerRegistry.all()) {
            if (rule.condition().matches(ctx)) {
                list.add(new MusicSource(rule.source(), rule.priority(), rule.musicId(), rule.poolTag()));
            }
        }
        return list;
    }

    /** Resolves a source to a definition, applying the random-pool latch (updates it for the winner). */
    @Nullable
    private static CsMusicDefinition resolveSource(PlayerState st, MusicSource source) {
        if (source.poolTag() != null) {
            if (source.key().equals(st.latchedKey) && st.latchedId != null) {
                CsMusicDefinition held = CsMusicRegistry.get(st.latchedId).orElse(null);
                if (held != null && held.hasTag(source.poolTag())) {
                    return held; // keep the same pick while this pool stays the winner
                }
            }
            List<CsMusicDefinition> pool = CsMusicRegistry.byTag(source.poolTag());
            if (pool.isEmpty()) {
                return null;
            }
            CsMusicDefinition picked = pool.get(RANDOM.nextInt(pool.size()));
            st.latchedKey = source.key();
            st.latchedId = picked.id();
            return picked;
        }
        CsMusicDefinition def = CsMusicRegistry.get(source.musicId()).orElse(null);
        if (def != null) {
            st.latchedKey = source.key();
            st.latchedId = def.id();
        }
        return def;
    }

    /** Read-only resolution (no latch mutation) for {@code /csmusic current}. */
    @Nullable
    private static CsMusicDefinition peekResolve(PlayerState st, MusicSource source) {
        if (source.poolTag() != null) {
            if (source.key().equals(st.latchedKey) && st.latchedId != null) {
                CsMusicDefinition held = CsMusicRegistry.get(st.latchedId).orElse(null);
                if (held != null && held.hasTag(source.poolTag())) {
                    return held;
                }
            }
            List<CsMusicDefinition> pool = CsMusicRegistry.byTag(source.poolTag());
            return pool.isEmpty() ? null : pool.get(0);
        }
        return CsMusicRegistry.get(source.musicId()).orElse(null);
    }

    private static void clearLatch(PlayerState st) {
        st.latchedKey = null;
        st.latchedId = null;
    }

    private static boolean musicEnabled() {
        DimensionalMusicData cfg = DimensionalMusicConfig.data;
        return cfg == null || cfg.enabled;
    }

    private static int defaultExitMode(@Nullable CsMusicDefinition prev, @Nullable CsMusicDefinition winner) {
        if (prev != null && prev.hasOutro() && winner == null) {
            return SetCsMusicPayload.MODE_OUTRO;
        }
        return SetCsMusicPayload.MODE_FADE;
    }

    /**
     * True when two tracks are musically related, so the transition is a <b>synced crossfade</b>
     * (the incoming track starts at the outgoing track's loop playhead): the winner is the child of
     * the current track, the current track is the child of the winner (return to parent), or both
     * declare the same parent (siblings). Related tracks must share the same loop duration.
     */
    private static boolean crossfadeRelated(@Nullable CsMusicDefinition prev, @Nullable CsMusicDefinition winner) {
        if (prev == null || winner == null) {
            return false;
        }
        if (winner.hasParent() && winner.parent().equals(prev.id())) {
            return true; // parent -> child
        }
        if (prev.hasParent() && prev.parent().equals(winner.id())) {
            return true; // child -> parent
        }
        return prev.hasParent() && winner.hasParent() && winner.parent().equals(prev.parent()); // siblings
    }

    // --- /csmusic current ------------------------------------------------------

    /** Areas the player currently stands in, so {@code /csmusic current} can explain a rule that
     * carries an area axis but never fires (wrong id, wrong tag, area disabled, box misplaced). */
    public static List<CsMusicArea> areasHereFor(ServerPlayer player) {
        return new CsMusicEvalContext(player).areasHere();
    }

    public static List<SourceInfo> describeSourcesFor(ServerPlayer player) {
        List<SourceInfo> result = new ArrayList<>();
        PlayerState st = state(player.getUUID());
        CsMusicDefinition boss = CsMusicRegistry.get(st.bossOverride).orElse(null);
        if (boss != null) {
            result.add(new SourceInfo("boss", boss.id(), Integer.MAX_VALUE, true));
        }
        List<MusicSource> sources = collectSources(player);
        sources.sort(SOURCE_ORDER);

        String winnerKey = null;
        if (boss == null) {
            for (MusicSource source : sources) {
                if (peekResolve(st, source) != null) {
                    winnerKey = source.key();
                    break;
                }
            }
        }
        for (MusicSource source : sources) {
            CsMusicDefinition def = peekResolve(st, source);
            String id = def != null
                    ? def.id()
                    : (source.poolTag() != null ? "#" + source.poolTag() : String.valueOf(source.musicId()));
            boolean winner = boss == null && source.key().equals(winnerKey);
            result.add(new SourceInfo(source.key(), id, source.priority(), winner));
        }
        return result;
    }

    // --- Boss hooks ------------------------------------------------------------

    public static void onBossStart(Collection<ServerPlayer> aliveParticipants, String csmusicId) {
        if (!CsMusicRegistry.has(csmusicId)) {
            return;
        }
        for (ServerPlayer p : aliveParticipants) {
            PlayerState st = state(p.getUUID());
            // A boss override already present means this is a phase-to-phase switch. If the next
            // phase's music is related to the current phase's music (parent/child/sibling), do a
            // synced crossfade; otherwise hard-cut to the new phase.
            if (st.bossOverride != null) {
                CsMusicDefinition prev = CsMusicRegistry.get(st.bossOverride).orElse(null);
                CsMusicDefinition next = CsMusicRegistry.get(csmusicId).orElse(null);
                st.nextMode = crossfadeRelated(prev, next)
                        ? SetCsMusicPayload.MODE_CROSSFADE
                        : SetCsMusicPayload.MODE_CUT;
            }
            st.bossOverride = csmusicId;
            updatePlayer(p); // immediate: a boss transition must not wait for the sweep cadence
        }
    }

    public static void onBossWin(Collection<ServerPlayer> aliveParticipants) {
        for (ServerPlayer p : aliveParticipants) {
            endBossFor(p, SetCsMusicPayload.MODE_OUTRO);
        }
    }

    public static void onBossLossOrLeave(ServerPlayer player) {
        endBossFor(player, SetCsMusicPayload.MODE_CUT);
    }

    private static void endBossFor(ServerPlayer player, int mode) {
        PlayerState st = state(player.getUUID());
        String bid = st.bossOverride;
        if (bid == null) {
            return;
        }
        st.bossOverride = null;
        if (bid.equals(st.lastSent)) {
            st.nextMode = mode;
        }
        updatePlayer(player);
    }

    public static void onPlayerDisconnect(UUID uuid) {
        STATES.remove(uuid);
    }

    /** Server shutdown: this state is static and would otherwise outlive an integrated server. */
    public static void clearAll() {
        STATES.clear();
    }

    // --- Debug (test commands) -------------------------------------------------

    /**
     * Sends a track directly to the player, bypassing the normal arbitration (which is suspended
     * for this player until {@link #debugStop}). Used by {@code /csmusic debug play|crossfade}.
     */
    public static void debugPlay(ServerPlayer player, CsMusicDefinition def, int mode, int startMs) {
        PlayerState st = state(player.getUUID());
        st.debug = true;
        SetCsMusicPayload payload = SetCsMusicPayload.track(
                def.id(), def.intro(), def.loop(), def.outro(), mode, startMs);
        Services.PLATFORM.sendPayloadToPlayer(player, payload);
        st.lastSent = def.id();
    }

    /** Clears the debug override and silences the player. */
    public static void debugStop(ServerPlayer player) {
        PlayerState st = state(player.getUUID());
        st.debug = false;
        Services.PLATFORM.sendPayloadToPlayer(player, SetCsMusicPayload.silence(SetCsMusicPayload.MODE_CUT));
        st.lastSent = null;
    }

    // --- Send ------------------------------------------------------------------

    private static void send(ServerPlayer player, PlayerState st, @Nullable CsMusicDefinition def, int mode) {
        SetCsMusicPayload payload = def != null
                ? SetCsMusicPayload.track(def.id(), def.intro(), def.loop(), def.outro(), mode)
                : SetCsMusicPayload.silence(mode);
        Services.PLATFORM.sendPayloadToPlayer(player, payload);
        st.lastSent = def != null ? def.id() : "";
    }
}
