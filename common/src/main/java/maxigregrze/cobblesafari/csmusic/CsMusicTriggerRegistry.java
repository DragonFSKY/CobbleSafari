package maxigregrze.cobblesafari.csmusic;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory set of compiled {@link CsMusicRule}s loaded from {@code csmusic/definition/*.json}.
 * Order is not significant - the resolver sorts by priority.
 */
public final class CsMusicTriggerRegistry {

    private static final List<CsMusicRule> RULES = new ArrayList<>();
    /** Whether any loaded rule constrains a structure piece. Computed at load, read in the sweep. */
    private static boolean usesPieces;

    private CsMusicTriggerRegistry() {}

    /**
     * True if at least one loaded rule constrains a structure piece. Lets the structure tracker skip
     * collecting the 40-80 pieces of a start when nothing needs them. Stable for the server's
     * lifetime: trigger files are only loaded in {@code DimensionEvents.onServerStarted}.
     */
    public static boolean usesPieces() {
        return usesPieces;
    }

    /**
     * True if the player currently matches at least one rule that can only play during a battle
     * ({@link CsMusicCondition#requiresBattle()}) - i.e. "a csmusic track matches the fight".
     * Drives suppression of Cobblemon's native battle music.
     */
    public static boolean hasMatchingBattleRule(ServerPlayer player) {
        for (CsMusicRule rule : RULES) {
            if (rule.condition().requiresBattle() && rule.condition().matches(player)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        RULES.clear();
        usesPieces = false;
    }

    public static void addAll(List<CsMusicRule> rules) {
        RULES.addAll(rules);
        usesPieces = RULES.stream().anyMatch(r -> r.condition().structurePiece() != null);
    }

    public static List<CsMusicRule> all() {
        return Collections.unmodifiableList(RULES);
    }

    public static int size() {
        return RULES.size();
    }
}
