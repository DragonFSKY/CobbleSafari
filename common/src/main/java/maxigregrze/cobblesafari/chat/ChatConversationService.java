package maxigregrze.cobblesafari.chat;

import maxigregrze.cobblesafari.CobbleSafari;
import maxigregrze.cobblesafari.compat.EntryFeeHelper;
import maxigregrze.cobblesafari.data.ChatProgressSavedData;
import maxigregrze.cobblesafari.data.ChatProgressSavedData.Phase;
import maxigregrze.cobblesafari.data.ChatProgressSavedData.ProgressEntry;
import maxigregrze.cobblesafari.data.ChatProgressSavedData.ResolvedSeries;
import maxigregrze.cobblesafari.data.RotomPhoneUnlockSavedData;
import maxigregrze.cobblesafari.gts.GtsService;
import maxigregrze.cobblesafari.init.ModStats;
import maxigregrze.cobblesafari.network.ChatAppResultPayload;
import maxigregrze.cobblesafari.rotomphone.RotomPhoneConfigSync;
import maxigregrze.cobblesafari.rotomphone.RotomPhoneSkinDefinition;
import maxigregrze.cobblesafari.rotomphone.RotomPhoneSkinRegistry;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side logic for the Rotom Phone chat questlines.
 *
 * <p>A conversation runs through its base {@code steps}; if it declares {@code repeatableStepsLists},
 * it then plays weight-rolled repeatable series as continuations (see {@link RepeatableSeriesDefinition}).
 * The {@link ProgressEntry} tracks the active section (base or the active series) plus a history of
 * resolved series for the transcript.
 */
public final class ChatConversationService {

    private ChatConversationService() {}

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String DEFAULT_FALLBACK = "cobblesafari:gifts/rotom_fallback";

    public enum ClaimResult { SUCCESS, NOT_FOUND, NOT_IN_TASK, NOT_COMPLETE }

    /** Resolved task progress for the bubble. {@code den == 0} = binary bar, no counter. */
    public record ProgressInfo(int num, int den, boolean done) {}

    // ---------------------------------------------------------------- active section helpers

    /** Steps of the active section: base steps while {@code !baseComplete}, else the active series (or empty = idle). */
    private static List<ChatStepDefinition> activeSteps(ChatConversationDefinition conv, ProgressEntry e) {
        if (!e.baseComplete) {
            return conv.steps();
        }
        if (e.activeSeriesId == null || e.activeSeriesId.isEmpty()) {
            return List.of();
        }
        RepeatableSeriesDefinition s = conv.series(e.activeSeriesId);
        return s == null ? List.of() : s.steps();
    }

    /**
     * Repairs an entry against the current definition (migration / datapack drift). Idempotent; sets
     * dirty only on change. Guarantees a coherent, playable state with no out-of-range indices.
     */
    private static void sanitize(ChatConversationDefinition conv, ProgressEntry e, ChatProgressSavedData data) {
        boolean dirty = false;
        if (e.activeSeriesId == null) {
            e.activeSeriesId = "";
            dirty = true;
        }
        // Old save that finished the base conversation in the pre-142 system.
        if (!e.baseComplete && e.phase == Phase.DONE) {
            e.baseComplete = true;
            e.activeSeriesId = "";
            dirty = true;
        }
        // Active series id no longer exists → drop to idle (a new one rolls at open/reset).
        if (e.baseComplete && !e.activeSeriesId.isEmpty() && conv.series(e.activeSeriesId) == null) {
            e.activeSeriesId = "";
            e.phase = Phase.DONE;
            e.claimed = true;
            e.messageIndex = 0;
            e.stepIndex = 0;
            e.seriesFallbackSteps.clear();
            dirty = true;
        }
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        if (!steps.isEmpty()) {
            if (e.stepIndex >= steps.size()) {
                e.stepIndex = steps.size() - 1;
                dirty = true;
            }
            if (e.stepIndex < 0) {
                e.stepIndex = 0;
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    // ---------------------------------------------------------------- state read

    /** Builds the per-player transcript snapshot for one conversation (null if unknown). */
    public static ChatAppResultPayload.StateData buildState(ServerPlayer player, String convId) {
        ChatConversationDefinition conv = ChatConversationRegistry.get(convId);
        if (conv == null) {
            return null;
        }
        ChatProgressSavedData data = ChatProgressSavedData.get(player.server);
        ProgressEntry e = data.getOrInit(player.getUUID(), convId);
        long today = LocalDate.now(ZONE).toEpochDay();
        RandomSource rng = player.server.overworld().getRandom();
        sanitize(conv, e, data);
        tickUnlockGate(player, conv, e, data, today);
        maybeRollIdle(conv, e, today, rng, data, player);

        List<ChatStepDefinition> active = activeSteps(conv, e);
        if (!active.isEmpty()) {
            applyStepStartEffects(player, active.get(Math.min(e.stepIndex, active.size() - 1)));
        }

        List<ChatAppResultPayload.StepView> views = new ArrayList<>();
        if (!e.baseComplete) {
            appendActiveSection(views, player, conv.steps(), e, data, e.baseFallbackSteps);
        } else {
            for (int i = 0; i < conv.steps().size(); i++) {
                appendCompletedStep(views, conv.steps().get(i), e.baseFallbackSteps.contains(i));
            }
            for (ResolvedSeries rs : e.history) {
                if (rs.doDisapear && today > rs.resolvedEpochDay) {
                    continue; // hidden from the day after resolution
                }
                RepeatableSeriesDefinition s = conv.series(rs.seriesId);
                if (s == null) {
                    continue;
                }
                if (rs.completed) {
                    for (int i = 0; i < s.steps().size(); i++) {
                        appendCompletedStep(views, s.steps().get(i), rs.fallbackSteps.contains(i));
                    }
                } else {
                    int failIdx = Math.min(Math.max(0, rs.stepReached), s.steps().size() - 1);
                    for (int i = 0; i < failIdx; i++) {
                        appendCompletedStep(views, s.steps().get(i), rs.fallbackSteps.contains(i));
                    }
                    appendFailedStep(views, s.steps().get(failIdx), s.failMessage());
                }
            }
            if (e.activeSeriesId != null && !e.activeSeriesId.isEmpty()) {
                RepeatableSeriesDefinition s = conv.series(e.activeSeriesId);
                if (s != null) {
                    appendActiveSection(views, player, s.steps(), e, data, e.seriesFallbackSteps);
                }
            }
        }
        int currentIdx = Math.max(0, views.size() - 1);
        return new ChatAppResultPayload.StateData(convId, currentIdx, e.phase.ordinal(), e.claimed, views);
    }

    private static void appendActiveSection(List<ChatAppResultPayload.StepView> views, ServerPlayer player,
                                            List<ChatStepDefinition> steps, ProgressEntry e, ChatProgressSavedData data,
                                            java.util.Set<Integer> fallbackSteps) {
        if (steps.isEmpty()) {
            return;
        }
        int current = Math.min(Math.max(0, e.stepIndex), steps.size() - 1);
        // The held step (WAIT_UNLOCK / WAIT_NEXT_DAY) is the current, finished step — rendered fully as a
        // completed step by the phase block below; the next (not-yet-started) step is simply not present.
        for (int i = 0; i <= current; i++) {
            ChatStepDefinition step = steps.get(i);
            if (i < current) {
                appendCompletedStep(views, step, fallbackSteps.contains(i));
                continue;
            }
            String titleKey = taskTitleKey(step);
            List<String> after = step.messagesAfter(fallbackSteps.contains(i));
            int beforeSize = step.messagesBefore().size();
            int afterSize = after.size();
            ProgressInfo info = computeProgress(player, step, e, data);
            int phase = e.phase.ordinal();
            int beforeShown;
            int afterShown;
            boolean taskVisible;
            boolean done;
            if (phase == Phase.BEFORE.ordinal()) {
                beforeShown = Math.min(e.messageIndex, beforeSize);
                afterShown = 0;
                taskVisible = false;
                done = info.done();
            } else if (phase == Phase.TASK.ordinal()) {
                beforeShown = beforeSize;
                afterShown = 0;
                taskVisible = true;
                done = info.done();
            } else if (phase == Phase.AFTER.ordinal()) {
                beforeShown = beforeSize;
                afterShown = Math.min(e.messageIndex, afterSize);
                taskVisible = true;
                done = true;
            } else { // WAIT_UNLOCK / WAIT_NEXT_DAY / DONE — the finished step, shown fully
                beforeShown = beforeSize;
                afterShown = afterSize;
                taskVisible = true;
                done = true;
            }
            views.add(new ChatAppResultPayload.StepView(
                    step.messagesBefore(), after, titleKey,
                    info.num(), info.den(), done, step.hasRewardItems(), step.hasRewardPersonalTrade(),
                    beforeShown, afterShown, taskVisible, true, false));
        }
    }

    private static void appendCompletedStep(List<ChatAppResultPayload.StepView> views, ChatStepDefinition step,
                                            boolean fallbackTriggered) {
        List<String> after = step.messagesAfter(fallbackTriggered);
        views.add(new ChatAppResultPayload.StepView(
                step.messagesBefore(), after, taskTitleKey(step),
                1, 0, true, step.hasRewardItems(), step.hasRewardPersonalTrade(),
                step.messagesBefore().size(), after.size(), true, false, false));
    }

    private static void appendFailedStep(List<ChatAppResultPayload.StepView> views, ChatStepDefinition step, String failMessage) {
        List<String> after = (failMessage == null || failMessage.isEmpty()) ? List.of() : List.of(failMessage);
        views.add(new ChatAppResultPayload.StepView(
                step.messagesBefore(), after, taskTitleKey(step),
                0, 0, false, false, false,
                step.messagesBefore().size(), after.size(), true, false, true));
    }

    /** Computes the task progress; lazily snapshots a stat-gated step's baseline if unset. */
    public static ProgressInfo computeProgress(ServerPlayer player, ChatStepDefinition step,
                                               ProgressEntry e, ChatProgressSavedData data) {
        return computeProgress(player, step, e, data, true);
    }

    /**
     * @param snapshotBaseline when {@code false}, an unset stat baseline is left alone and the step
     *     simply reads as 0 progress — the exact value the snapshot would have produced
     *     ({@code now - now}), but without writing to the save. Used by the read-only notification
     *     path ({@link #hasPendingAttention}), which runs for every conversation on every poll.
     */
    private static ProgressInfo computeProgress(ServerPlayer player, ChatStepDefinition step,
                                                ProgressEntry e, ChatProgressSavedData data,
                                                boolean snapshotBaseline) {
        if (step.isItemGated()) {
            int total = 0;
            int held = 0;
            for (ChatStepDefinition.ItemReq req : step.requiredItems()) {
                total += req.count();
                Item item = EntryFeeHelper.resolveItem(req.itemId());
                if (item == Items.AIR) {
                    // Broken/removed item id → contributes 0 held, so the step can never be claimed.
                    CobbleSafari.LOGGER.warn("[Chat] required item '{}' is not a registered item", req.itemId());
                    continue;
                }
                held += Math.min(EntryFeeHelper.countItemInInventory(player, item), req.count());
            }
            return new ProgressInfo(held, total, held >= total);
        }

        if (step.isStatGated()) {
            ResourceLocation parsed = ResourceLocation.tryParse(step.statistic());
            ResourceLocation statId = parsed == null ? null : BuiltInRegistries.CUSTOM_STAT.get(parsed);
            if (statId == null) {
                CobbleSafari.LOGGER.warn("[Chat] statistic '{}' is not a registered custom stat", step.statistic());
                return new ProgressInfo(0, step.statisticAmount(), false);
            }
            long now = ModStats.value(player, statId);
            if (e.statBaseline == Long.MIN_VALUE) {
                if (!snapshotBaseline) {
                    return new ProgressInfo(0, step.statisticAmount(), false);
                }
                e.statBaseline = now;
                data.setDirty();
            }
            long cur = Math.max(0L, now - e.statBaseline);
            int amount = step.statisticAmount();
            int num = (int) Math.min(cur, amount);
            return new ProgressInfo(num, amount, cur >= amount);
        }

        ResourceLocation advId = ResourceLocation.tryParse(step.advancement());
        if (advId == null) {
            return new ProgressInfo(0, 0, false);
        }
        AdvancementHolder holder = player.server.getAdvancements().get(advId);
        if (holder == null) {
            CobbleSafari.LOGGER.warn("[Chat] advancement '{}' referenced by a conversation does not exist", advId);
            return new ProgressInfo(0, 0, false);
        }
        AdvancementProgress pr = player.getAdvancements().getOrStartProgress(holder);
        int completed = count(pr.getCompletedCriteria());
        int total = completed + count(pr.getRemainingCriteria());
        boolean done = pr.isDone();
        if (total > 1) {
            return new ProgressInfo(completed, total, done);
        }
        return new ProgressInfo(done ? 1 : 0, 0, done);
    }

    private static int count(Iterable<String> it) {
        int n = 0;
        for (String ignored : it) {
            n++;
        }
        return n;
    }

    private static String taskTitleKey(ChatStepDefinition step) {
        if (step.isItemGated()) {
            return "gui.cobblesafari.rotomphone.chat.task.gather.title";
        }
        if (step.isStatGated()) {
            ResourceLocation statId = ResourceLocation.tryParse(step.statistic());
            return statId == null ? "" : "stat." + statId.getNamespace() + "." + statId.getPath();
        }
        ResourceLocation advId = ResourceLocation.tryParse(step.advancement());
        if (advId == null) {
            return "";
        }
        String path = advId.getPath().replace('/', '.');
        String base = advId.getNamespace().equals("minecraft") ? path : advId.getNamespace() + "." + path;
        return "advancements." + base + ".title";
    }

    // ---------------------------------------------------------------- notification dot (read-only)

    /**
     * Whether this conversation currently asks for the player's attention: unread messages, or a task
     * whose objective is met and whose reward has not been claimed yet.
     *
     * <p><strong>Strictly read-only.</strong> It peeks the entry instead of creating one, never
     * sanitizes, never rolls a series, never snapshots a stat baseline and never marks the save dirty,
     * because it runs for every conversation on every notification poll. A held step
     * ({@link Phase#WAIT_NEXT_DAY} / {@link Phase#WAIT_UNLOCK}) is deliberately <em>not</em> pending:
     * there is nothing new to read until the hold is released.
     */
    public static boolean hasPendingAttention(ServerPlayer player, ChatConversationDefinition conv,
                                              ChatProgressSavedData data) {
        ProgressEntry e = data.peek(player.getUUID(), conv.id());
        if (e == null) {
            // Never opened: the very first step is waiting to be read. Peeking (rather than
            // getOrInit) is what keeps the poll from materialising an entry per conversation.
            return !conv.steps().isEmpty();
        }
        return switch (e.phase) {
            // activeSteps is itself pure; an entry left incoherent by datapack drift resolves to an
            // empty section here (no dot) and is repaired by sanitize on the real open path.
            case BEFORE, AFTER -> !activeSteps(conv, e).isEmpty();
            case TASK -> isTaskClaimable(player, conv, e, data);
            case WAIT_NEXT_DAY, WAIT_UNLOCK -> false;
            case DONE -> hasEligibleSeries(conv, e, player);
        };
    }

    /** Objective met and reward not taken — i.e. the task bar is showing "Complete". */
    private static boolean isTaskClaimable(ServerPlayer player, ChatConversationDefinition conv,
                                           ProgressEntry e, ChatProgressSavedData data) {
        if (e.claimed) {
            return false;
        }
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        if (steps.isEmpty()) {
            return false;
        }
        int idx = Math.max(0, Math.min(e.stepIndex, steps.size() - 1));
        return computeProgress(player, steps.get(idx), e, data, false).done();
    }

    /**
     * Read-only probe: would {@link #maybeRollIdle} start a series if the app were opened right now?
     *
     * <p>Needed because {@link #onDailyReset} walks offline players and therefore cannot roll an
     * advancement-gated series ({@code isSeriesUnlocked} excludes them when the player is unknown).
     * Without this, a series that became eligible would sit there with no dot to prompt the player to
     * open the app. The probe never rolls anything — {@code maybeRollIdle} still does that on open.
     */
    private static boolean hasEligibleSeries(ChatConversationDefinition conv, ProgressEntry e, ServerPlayer player) {
        if (!e.baseComplete || !conv.usesRepeatables()
                || e.activeSeriesId == null || !e.activeSeriesId.isEmpty()) {
            return false;
        }
        for (RepeatableSeriesDefinition s : conv.repeatableStepsLists()) {
            if (s.weight() > 0 && isSeriesEligible(s, e, player)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- mutations

    /** Persists the revealed-message position; flips BEFORE→TASK once all before-messages are shown. */
    public static void setMessageIndex(ServerPlayer player, String convId, int newIndex) {
        ChatConversationDefinition conv = ChatConversationRegistry.get(convId);
        if (conv == null) {
            return;
        }
        ChatProgressSavedData data = ChatProgressSavedData.get(player.server);
        ProgressEntry e = data.getOrInit(player.getUUID(), convId);
        sanitize(conv, e, data);
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        if (steps.isEmpty()) {
            return;
        }
        ChatStepDefinition step = steps.get(Math.min(e.stepIndex, steps.size() - 1));
        if (e.phase == Phase.BEFORE) {
            int max = step.messagesBefore().size();
            if (newIndex >= max) {
                e.phase = Phase.TASK;
                e.messageIndex = 0;
            } else {
                e.messageIndex = Math.max(0, newIndex);
            }
            data.setDirty();
        } else if (e.phase == Phase.AFTER) {
            int max = step.messagesAfter(fallbackSetFor(e).contains(e.stepIndex)).size();
            e.messageIndex = Math.max(0, Math.min(newIndex, max));
            data.setDirty();
        }
    }

    public static ClaimResult tryClaim(ServerPlayer player, String convId) {
        ChatConversationDefinition conv = ChatConversationRegistry.get(convId);
        if (conv == null) {
            return ClaimResult.NOT_FOUND;
        }
        ChatProgressSavedData data = ChatProgressSavedData.get(player.server);
        ProgressEntry e = data.getOrInit(player.getUUID(), convId);
        sanitize(conv, e, data);
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        if (steps.isEmpty() || e.phase != Phase.TASK || e.claimed) {
            return ClaimResult.NOT_IN_TASK;
        }
        ChatStepDefinition step = steps.get(Math.min(e.stepIndex, steps.size() - 1));
        ProgressInfo info = computeProgress(player, step, e, data);
        if (!info.done()) {
            return ClaimResult.NOT_COMPLETE;
        }
        // Item objectives consume the exact required counts atomically; a failure here means the player
        // no longer holds them (dropped between the GUI check and the click) → benign, nothing consumed.
        if (step.isItemGated() && !consumeRequiredItems(player, step)) {
            return ClaimResult.NOT_COMPLETE;
        }
        if (giveRewards(player, step)) {
            fallbackSetFor(e).add(e.stepIndex);
        }
        e.claimed = true;
        e.phase = Phase.AFTER;
        e.messageIndex = 0;
        // A series counts as completed the moment the last reward of the series is obtained.
        if (e.baseComplete && !e.activeSeriesId.isEmpty() && e.stepIndex >= steps.size() - 1) {
            RepeatableSeriesDefinition s = conv.series(e.activeSeriesId);
            if (s != null) {
                e.completedOnce.add(e.activeSeriesId); // unlocks series that list it as prerequisite
                if (s.isUnique()) {
                    e.completedUnique.add(e.activeSeriesId);
                }
            }
        }
        data.setDirty();
        return ClaimResult.SUCCESS;
    }

    /** Called once the client finished streaming the AFTER messages. */
    public static void onAfterMessagesDone(ServerPlayer player, String convId) {
        ChatConversationDefinition conv = ChatConversationRegistry.get(convId);
        if (conv == null) {
            return;
        }
        ChatProgressSavedData data = ChatProgressSavedData.get(player.server);
        ProgressEntry e = data.getOrInit(player.getUUID(), convId);
        sanitize(conv, e, data);
        if (e.phase != Phase.AFTER) {
            return;
        }
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        if (steps.isEmpty()) {
            return;
        }
        ChatStepDefinition step = steps.get(Math.min(e.stepIndex, steps.size() - 1));
        // Hold this step (as the visible, completed step) if a gate on the next step or its own
        // waitNextDay is owed; otherwise advance now. The reset does the actual waitNextDay advance.
        long today = LocalDate.now(ZONE).toEpochDay();
        leaveCurrentStep(conv, e, player, today, player.server.overworld().getRandom(), step.waitNextDay());
        data.setDirty();
    }

    // ---------------------------------------------------------------- section / series progression

    /** Advances past the current step; rolls into / between repeatable series, or finishes. */
    private static void advanceAfterStep(ChatConversationDefinition conv, ProgressEntry e, long today,
                                         RandomSource rng, ServerPlayer player) {
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        if (steps.isEmpty()) {
            return; // idle
        }
        int idx = e.stepIndex;
        if (idx + 1 < steps.size()) {
            e.stepIndex = idx + 1;
            startStep(steps.get(e.stepIndex), e);
            return;
        }
        if (!e.baseComplete) {
            e.baseComplete = true;
            if (conv.usesRepeatables()) {
                startRolledSeries(conv, e, today, rng, player);
            } else {
                finishIdle(conv, e);
            }
        } else {
            recordResolved(conv, e, true, today);
            startRolledSeries(conv, e, today, rng, player);
        }
    }

    /**
     * From the idle state, roll a series if one is eligible (replenishment / first-roll after migration).
     * Only mutates when a series is actually started, to avoid marking the save dirty on every poll when
     * the pool is genuinely exhausted.
     */
    private static void maybeRollIdle(ChatConversationDefinition conv, ProgressEntry e, long today,
                                      RandomSource rng, ChatProgressSavedData data, ServerPlayer player) {
        if (e.baseComplete && (e.activeSeriesId == null || e.activeSeriesId.isEmpty())
                && conv.usesRepeatables() && e.phase == Phase.DONE) {
            String id = rollSeries(conv, e, rng, player);
            if (!id.isEmpty()) {
                e.activeSeriesId = id;
                e.seriesStartEpochDay = today;
                e.stepIndex = 0;
                e.seriesFallbackSteps.clear();
                startStep(conv.series(id).steps().get(0), e);
                data.setDirty();
            }
        }
    }

    private static void startRolledSeries(ChatConversationDefinition conv, ProgressEntry e, long today,
                                          RandomSource rng, ServerPlayer player) {
        String id = rollSeries(conv, e, rng, player);
        if (id.isEmpty()) {
            finishIdle(conv, e);
            return;
        }
        e.activeSeriesId = id;
        e.seriesStartEpochDay = today;
        e.stepIndex = 0;
        e.seriesFallbackSteps.clear();
        startStep(conv.series(id).steps().get(0), e);
    }

    private static void finishIdle(ChatConversationDefinition conv, ProgressEntry e) {
        e.activeSeriesId = "";
        e.seriesFallbackSteps.clear();
        e.phase = Phase.DONE;
        e.messageIndex = 0;
        e.claimed = true;
        e.stepIndex = Math.max(0, conv.steps().size() - 1);
    }

    /**
     * Weighted pick among eligible series; {@code ""} if none eligible. A series is excluded when it is
     * an already-completed unique, when its {@code prerequisite} has never been completed by this player
     * (the loader guarantees the prerequisite exists and that the graph is acyclic, so a pool can never
     * be deadlocked by prerequisites alone), or when its first step's {@code unlockingAdvancement} is not
     * yet owned.
     *
     * @param player the player being rolled for, or {@code null} when unknown (offline daily reset); a
     *     null player conservatively excludes every advancement-gated series, leaving the conversation
     *     idle until {@link #maybeRollIdle} rolls again with the player online
     */
    private static String rollSeries(ChatConversationDefinition conv, ProgressEntry e, RandomSource rng,
                                     ServerPlayer player) {
        List<RepeatableSeriesDefinition> pool = new ArrayList<>();
        for (RepeatableSeriesDefinition s : conv.repeatableStepsLists()) {
            if (isSeriesEligible(s, e, player)) {
                pool.add(s);
            }
        }
        if (pool.isEmpty()) {
            return "";
        }
        int total = 0;
        for (RepeatableSeriesDefinition s : pool) {
            total += s.weight();
        }
        if (total <= 0) {
            return "";
        }
        int r = rng.nextInt(total);
        for (RepeatableSeriesDefinition s : pool) {
            r -= s.weight();
            if (r < 0) {
                return s.id();
            }
        }
        return pool.get(pool.size() - 1).id();
    }

    /**
     * Whether {@code s} may be offered to {@code player} right now: not an already-completed unique,
     * prerequisite satisfied, and entry advancement owned. Shared by the weighted roll and by the
     * read-only {@link #hasEligibleSeries} probe so the two can never drift apart.
     */
    private static boolean isSeriesEligible(RepeatableSeriesDefinition s, ProgressEntry e, ServerPlayer player) {
        if (s.isUnique() && e.completedUnique.contains(s.id())) {
            return false;
        }
        if (s.hasPrerequisite() && !e.completedOnce.contains(s.prerequisite())) {
            return false;
        }
        return isSeriesUnlocked(s, player);
    }

    /**
     * Whether a series may be offered at all: its first step's {@code unlockingAdvancement} acts as the
     * series' entry condition, so a gated series is never rolled before the player owns it (rather than
     * being rolled and then held). Gates on later steps still hold mid-series as usual.
     */
    private static boolean isSeriesUnlocked(RepeatableSeriesDefinition s, ServerPlayer player) {
        ChatStepDefinition first = s.step(0);
        if (first == null || !first.hasUnlockingAdvancement()) {
            return true;
        }
        return player != null && hasAdvancement(player, first.unlockingAdvancement());
    }

    private static void recordResolved(ChatConversationDefinition conv, ProgressEntry e, boolean completed, long today) {
        if (e.activeSeriesId == null || e.activeSeriesId.isEmpty()) {
            return;
        }
        RepeatableSeriesDefinition s = conv.series(e.activeSeriesId);
        ResolvedSeries rs = new ResolvedSeries();
        rs.seriesId = e.activeSeriesId;
        rs.completed = completed;
        rs.stepReached = e.stepIndex;
        rs.resolvedEpochDay = today;
        rs.doDisapear = s != null && s.doDisapear();
        rs.fallbackSteps.addAll(e.seriesFallbackSteps);
        e.appendHistory(rs, today);
        if (completed) {
            e.completedOnce.add(e.activeSeriesId);
            if (s != null && s.isUnique()) {
                e.completedUnique.add(e.activeSeriesId);
            }
        }
    }

    /** Starts a fresh step at its BEFORE messages. Holds are decided before advancing, never here. */
    private static void startStep(ChatStepDefinition step, ProgressEntry e) {
        e.phase = Phase.BEFORE;
        e.messageIndex = 0;
        e.claimed = false;
        e.pendingWaitNextDay = false;
        e.statBaseline = step.isStatGated() ? Long.MIN_VALUE : 0L; // lazy snapshot for stat-gated
    }

    /** The step following the current one in the active section, or {@code null} if it is the last. */
    private static ChatStepDefinition nextStepInSection(ChatConversationDefinition conv, ProgressEntry e) {
        List<ChatStepDefinition> steps = activeSteps(conv, e);
        int next = e.stepIndex + 1;
        return next >= 0 && next < steps.size() ? steps.get(next) : null;
    }

    /**
     * Decides what happens once the current step's after-messages are done. The current step is
     * <em>held</em> (kept as the visible, completed step — {@code stepIndex} unchanged) while a condition
     * is pending, and only when everything is clear do we advance. Ordering matches the spec: an
     * {@code unlockingAdvancement} gate on the <em>next</em> step is waited out first, then any
     * {@code waitNextDay} owed by the finished step; the actual advance happens on the next reset (for
     * {@code waitNextDay}) via {@link #onDailyReset}, or immediately when neither condition applies.
     */
    private static void leaveCurrentStep(ChatConversationDefinition conv, ProgressEntry e, ServerPlayer player,
                                         long today, RandomSource rng, boolean wantWaitNextDay) {
        ChatStepDefinition next = nextStepInSection(conv, e);
        if (next != null && next.hasUnlockingAdvancement()
                && (player == null || !hasAdvancement(player, next.unlockingAdvancement()))) {
            e.phase = Phase.WAIT_UNLOCK;
            e.pendingWaitNextDay = wantWaitNextDay; // the owed wait only begins once the gate opens
            return;
        }
        if (wantWaitNextDay) {
            e.phase = Phase.WAIT_NEXT_DAY;
            e.pendingWaitNextDay = false;
            return;
        }
        advanceAfterStep(conv, e, today, rng, player);
    }

    /** True while the current step is held (finished but not yet advanced past). */
    private static boolean isHeld(ProgressEntry e) {
        return e.phase == Phase.WAIT_UNLOCK || e.phase == Phase.WAIT_NEXT_DAY;
    }

    /**
     * Resolves a {@link Phase#WAIT_UNLOCK} hold once the player obtains the <em>next</em> step's
     * {@code unlockingAdvancement}. Requires an online player, so it runs on the app-open/poll path
     * rather than in the daily reset (which also walks offline players); a gate that opens while the
     * player is offline is simply picked up the next time they open the conversation. When the gate opens
     * it either begins the owed {@code waitNextDay} (→ {@link Phase#WAIT_NEXT_DAY}, released by the next
     * reset) or advances immediately.
     */
    private static void tickUnlockGate(ServerPlayer player, ChatConversationDefinition conv,
                                       ProgressEntry e, ChatProgressSavedData data, long today) {
        if (e.phase != Phase.WAIT_UNLOCK) {
            return;
        }
        ChatStepDefinition next = nextStepInSection(conv, e);
        // Gate still shut → keep waiting. An absent next step or a removed gate resolves as satisfied.
        if (next != null && next.hasUnlockingAdvancement() && !hasAdvancement(player, next.unlockingAdvancement())) {
            return;
        }
        if (e.pendingWaitNextDay) {
            e.phase = Phase.WAIT_NEXT_DAY;
            e.pendingWaitNextDay = false;
        } else {
            advanceAfterStep(conv, e, today, player.server.overworld().getRandom(), player);
        }
        data.setDirty();
    }

    /** Ids already reported as unusable, so a broken gate warns once instead of on every poll. */
    private static final java.util.Set<String> WARNED_ADVANCEMENTS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Whether the player owns {@code idStr}. An unparseable or unknown advancement id is treated as
     * <em>already unlocked</em> (fail-open) and logged once: a datapack typo must never leave a
     * questline permanently stuck behind a gate that can only ever be closed.
     */
    private static boolean hasAdvancement(ServerPlayer player, String idStr) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) {
            warnBadGate(idStr, "is not a valid advancement id");
            return true;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder == null) {
            warnBadGate(idStr, "does not exist on this server");
            return true;
        }
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void warnBadGate(String idStr, String reason) {
        if (WARNED_ADVANCEMENTS.add(idStr)) {
            CobbleSafari.LOGGER.warn("[Chat] unlockingAdvancement '{}' {} — the step is treated as unlocked",
                    idStr, reason);
        }
    }

    /** Idempotent step-start side effects: unlock the app declared by the step, resync if changed. */
    private static void applyStepStartEffects(ServerPlayer player, ChatStepDefinition step) {
        if (step == null || !step.hasUnlockApp()) {
            return;
        }
        RotomPhoneUnlockSavedData store = RotomPhoneUnlockSavedData.get(player.server);
        if (store != null && store.unlockApp(player.getUUID(), step.unlockApp())) {
            RotomPhoneConfigSync.syncToPlayer(player);
        }
    }

    // ---------------------------------------------------------------- rewards

    /** The fallback-step set of the section the entry is currently playing. */
    private static java.util.Set<Integer> fallbackSetFor(ProgressEntry e) {
        return e.baseComplete ? e.seriesFallbackSteps : e.baseFallbackSteps;
    }

    /**
     * Consumes an item-gated step's {@code requiredItems}, all-or-nothing: it first resolves and verifies
     * every requirement against the current inventory, and only when all are satisfied does it remove the
     * exact counts. Because everything runs on one server tick there is no interleaving between the check
     * and the removal. The loader guarantees no duplicate item ids, so per-item counting is exact.
     *
     * @return {@code true} if every requirement was removed; {@code false} (nothing consumed) if the
     *     player no longer holds enough or an item id is broken
     */
    private static boolean consumeRequiredItems(ServerPlayer player, ChatStepDefinition step) {
        List<ChatStepDefinition.ItemReq> reqs = step.requiredItems();
        Item[] items = new Item[reqs.size()];
        for (int i = 0; i < reqs.size(); i++) {
            ChatStepDefinition.ItemReq req = reqs.get(i);
            Item item = EntryFeeHelper.resolveItem(req.itemId());
            if (item == Items.AIR) {
                CobbleSafari.LOGGER.warn("[Chat] cannot consume broken required item '{}'", req.itemId());
                return false;
            }
            if (EntryFeeHelper.countItemInInventory(player, item) < req.count()) {
                return false; // dropped/used between the poll and the claim
            }
            items[i] = item;
        }
        for (int i = 0; i < reqs.size(); i++) {
            if (!EntryFeeHelper.removeItemsFromInventory(player, items[i], reqs.get(i).count())) {
                // Unreachable: counts were just verified on the same tick. Log loudly if it ever happens.
                CobbleSafari.LOGGER.error("[Chat] failed to remove {}x {} after verification",
                        reqs.get(i).count(), reqs.get(i).itemId());
                return false;
            }
        }
        player.inventoryMenu.broadcastChanges(); // push the inventory change to the client
        return true;
    }

    /** @return true if a tag reward was requested but exhausted, i.e. the loot fallback was granted. */
    private static boolean giveRewards(ServerPlayer player, ChatStepDefinition step) {
        MinecraftServer server = player.server;
        if (step.hasRewardItems()) {
            grantLootTable(player, step.rewardItems());
        }
        // Explicit (specific) rewards: attempted, but do not drive the tag fallback.
        if (step.hasRewardPersonalTrade()) {
            GtsService.AddPersonalOfferOutcome r =
                    GtsService.addPersonalOffer(server, player.getUUID(), step.rewardPersonalTrade());
            if (r.result() != GtsService.AddPersonalOfferResult.SUCCESS) {
                CobbleSafari.LOGGER.warn("[Chat] personal trade reward '{}' failed: {}",
                        step.rewardPersonalTrade(), r.result());
            }
        }
        if (step.hasRewardSkin()) {
            giveSpecificSkin(player, step.rewardSkin());
        }

        // Tag rewards: dedup-aware; if requested and none granted, fall back to a loot pool.
        boolean tagRequested = step.hasRewardSkinTag() || step.hasRewardPersonalTradeTag();
        boolean tagGranted = false;
        if (step.hasRewardPersonalTradeTag()) {
            tagGranted |= givePersonalTradeByTag(server, player, step.rewardPersonalTradeTag());
        }
        if (step.hasRewardSkinTag()) {
            tagGranted |= giveSkinByTag(player, step.rewardSkinTag());
        }
        if (tagRequested && !tagGranted) {
            grantLootTable(player, step.hasFallbackReward() ? step.fallbackReward() : DEFAULT_FALLBACK);
            return true;
        }
        return false;
    }

    private static boolean givePersonalTradeByTag(MinecraftServer server, ServerPlayer player, String tag) {
        GtsService.AddPersonalOfferOutcome r = GtsService.addPersonalOfferByTag(server, player.getUUID(), tag);
        if (r.result() != GtsService.AddPersonalOfferResult.SUCCESS) {
            CobbleSafari.LOGGER.info("[Chat] personal trade tag '{}' granted nothing ({})", tag, r.result());
            return false;
        }
        return true;
    }

    private static boolean giveSkinByTag(ServerPlayer player, String tag) {
        String skinId = pickRandomSkinForTag(player, tag);
        return skinId != null && unlockSkin(player, skinId);
    }

    private static void giveSpecificSkin(ServerPlayer player, String skinId) {
        if (RotomPhoneSkinRegistry.getSkin(skinId) == null) {
            CobbleSafari.LOGGER.warn("[Chat] reward skin '{}' is not a registered skin", skinId);
            return;
        }
        unlockSkin(player, skinId);
    }

    /** @return true if the skin was newly unlocked (false if already owned). */
    private static boolean unlockSkin(ServerPlayer player, String skinId) {
        RotomPhoneUnlockSavedData store = RotomPhoneUnlockSavedData.get(player.server);
        if (store != null && store.unlockSkin(player.getUUID(), skinId)) {
            RotomPhoneConfigSync.syncToPlayer(player);
            return true;
        }
        return false;
    }

    /** Random skin carrying {@code tag} not yet unlocked by the player; null if none remain. */
    private static String pickRandomSkinForTag(ServerPlayer player, String tag) {
        List<RotomPhoneSkinDefinition> pool = new ArrayList<>();
        for (RotomPhoneSkinDefinition skin : RotomPhoneSkinRegistry.getSkinsByTag(tag)) {
            if (!RotomPhoneSkinRegistry.isUnlockedByPlayer(player, skin)) {
                pool.add(skin);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(player.serverLevel().getRandom().nextInt(pool.size())).getId();
    }

    private static void grantLootTable(ServerPlayer player, String tableIdStr) {
        ResourceLocation tableId = ResourceLocation.tryParse(tableIdStr);
        if (tableId == null) {
            CobbleSafari.LOGGER.warn("[Chat] invalid loot table id: {}", tableIdStr);
            return;
        }
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, tableId);
        LootTable table = player.server.reloadableRegistries().getLootTable(key);
        if (table == LootTable.EMPTY) {
            CobbleSafari.LOGGER.warn("[Chat] reward loot table not found: {}", tableId);
            return;
        }
        ServerLevel level = player.serverLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(stack) || !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    // ---------------------------------------------------------------- daily reset

    /** Natural daily rollover (used by the scheduler): never artificially forces timed-series expiry. */
    public static void onDailyReset(MinecraftServer server) {
        onDailyReset(server, false);
    }

    /**
     * Advances WAIT_NEXT_DAY steps, fails overdue timed series, and replenishes idle pools. Idempotent.
     *
     * @param forceTimedExpiry when {@code true}, every active incomplete timed series fails regardless of
     *     its start date (manual "roll to next day"); when {@code false}, only genuinely overdue series
     *     ({@code seriesStartEpochDay < today}) fail.
     */
    public static void onDailyReset(MinecraftServer server, boolean forceTimedExpiry) {
        ChatProgressSavedData data = ChatProgressSavedData.get(server);
        if (data == null) {
            return;
        }
        long today = LocalDate.now(ZONE).toEpochDay();
        RandomSource rng = server.overworld().getRandom();
        for (Map.Entry<UUID, Map<String, ProgressEntry>> pe : data.all().entrySet()) {
            // Resolve the player when they are online so advancement-gated series can be evaluated by the
            // rolls below; offline players stay null (gated series are then skipped and picked up by
            // maybeRollIdle on their next open).
            ServerPlayer online = server.getPlayerList().getPlayer(pe.getKey());
            for (Map.Entry<String, ProgressEntry> ce : pe.getValue().entrySet()) {
                ProgressEntry e = ce.getValue();
                ChatConversationDefinition conv = ChatConversationRegistry.get(ce.getKey());
                if (conv == null) {
                    continue;
                }
                sanitize(conv, e, data);

                // 1. timed series past their deadline → fail and roll the next. A series still held back
                //    (unlock gate / owed wait) has not started being playable, so it cannot expire yet.
                if (e.baseComplete && e.activeSeriesId != null && !e.activeSeriesId.isEmpty() && !isHeld(e)) {
                    RepeatableSeriesDefinition s = conv.series(e.activeSeriesId);
                    if (s != null && s.isTimed()) {
                        boolean rewardComplete = e.stepIndex >= s.steps().size() - 1 && e.claimed;
                        boolean overdue = e.seriesStartEpochDay != Long.MIN_VALUE
                                && e.seriesStartEpochDay < today;
                        if (!rewardComplete && (forceTimedExpiry || overdue)) {
                            recordResolved(conv, e, false, today);
                            startRolledSeries(conv, e, today, rng, online);
                            e.lastResetEpochDay = today;
                            continue;
                        }
                    }
                }

                // 2. A waitNextDay step advances to the next step on the next reset (natural daily rollover,
                //    safari reset, or a manual /cobblesafari reset system|hard). The wait only *begins* once
                //    any owed unlock gate has opened (WAIT_UNLOCK → WAIT_NEXT_DAY): "unlock first, then one
                //    reset". A held gate (WAIT_UNLOCK) never advances here — it resolves on the app-open path.
                if (e.phase == Phase.WAIT_NEXT_DAY) {
                    advanceAfterStep(conv, e, today, rng, online);
                    e.lastResetEpochDay = today;
                    continue;
                }

                // 3. WAIT_UNLOCK is resolved on the app-open path (it needs the online player).
                if (e.phase == Phase.WAIT_UNLOCK) {
                    continue;
                }

                // 4. idle pool replenishment (e.g. datapack added series). Advancement-gated series are
                //    skipped only for offline players and picked up by maybeRollIdle on their next open.
                if (e.baseComplete && (e.activeSeriesId == null || e.activeSeriesId.isEmpty())
                        && conv.usesRepeatables() && e.phase == Phase.DONE) {
                    startRolledSeries(conv, e, today, rng, online);
                    e.lastResetEpochDay = today;
                }
            }
        }
        data.setDirty();
    }

    /** Daily scheduler mirroring {@code GtsService.tickDailyScheduler} (configurable reset hour). */
    public static void tickDailyScheduler(MinecraftServer server) {
        ChatProgressSavedData data = ChatProgressSavedData.get(server);
        if (data == null) {
            return;
        }
        long todayEpoch = LocalDate.now(ZONE).toEpochDay();
        long last = data.getLastDailyResetEpochDay();
        if (last < 0) {
            data.setLastDailyResetEpochDay(todayEpoch - 1);
            return;
        }
        int resetHour = maxigregrze.cobblesafari.config.MiscConfig.getDailySystemResetHour();
        boolean pastResetHour = LocalTime.now(ZONE).getHour() >= resetHour;
        boolean shouldRun = (todayEpoch > last && pastResetHour) || (todayEpoch > last + 1);
        if (!shouldRun) {
            return;
        }
        onDailyReset(server);
        data.setLastDailyResetEpochDay(todayEpoch);
    }
}
