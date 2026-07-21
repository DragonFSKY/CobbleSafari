package maxigregrze.cobblesafari.chat;

import java.util.List;

/**
 * One step of a {@link ChatConversationDefinition} questline (base step or a step of a
 * {@link RepeatableSeriesDefinition}).
 *
 * <p>{@code unlockingAdvancement} gates the step's <em>visibility</em>: until the player owns that
 * advancement the step is not started and not shown at all, and any {@code waitNextDay} owed by the
 * previous step only starts counting from the moment it is unlocked (unlock first, then wait a reset).
 * An advancement id that no datapack defines is treated as already unlocked (fail-open) so a typo can
 * never permanently brick a questline; the reason is logged once per check.
 *
 * <p>Gating of the <em>task</em> is decided by which field is present, in priority order: a step is
 * <em>item-gated</em> when {@code requiredItems} is non-empty (progress = required items currently held
 * out of the total, consumed on claim), else <em>statistic-gated</em> when {@code statistic} is set
 * (progress = stat delta from a per-player baseline, re-snapshotted each time the step becomes active),
 * otherwise <em>advancement-gated</em> (progress = advancement criteria ratio).
 *
 * <p>{@code unlockApp} is applied at the start of the step; {@code rewardSkin}/{@code rewardSkinTag}/
 * {@code rewardPersonalTrade}/{@code rewardPersonalTradeTag} are granted on claim. When a tag-based
 * reward is requested but the pool is exhausted, {@code fallbackReward} (a loot-table id) is granted
 * instead (defaulting to the global rotom fallback pool), and {@code fallbackMessagesAfter} — when
 * non-empty — replaces {@code messagesAfter} for that step (including in the transcript afterwards).
 */
public record ChatStepDefinition(
        List<String> messagesBefore,
        List<String> messagesAfter,
        List<String> fallbackMessagesAfter,
        String unlockingAdvancement, // nullable advancement gating this step's visibility
        String advancement, // nullable (statistic-gated steps have none)
        String statistic, // nullable (only statistic-gated steps)
        int statisticAmount, // only meaningful when statistic-gated
        String rewardItems, // nullable loot-table id (always granted)
        String rewardPersonalTrade, // nullable GTS unique-offer template id
        String rewardPersonalTradeTag, // nullable GTS unique-offer tag (random pick) granted on claim
        String unlockApp, // nullable rotom-phone app id unlocked at the start of this step
        String rewardSkin, // nullable rotom-phone skin id granted on claim
        String rewardSkinTag, // nullable rotom-phone skin tag (random pick) granted on claim
        String fallbackReward, // nullable loot-table id used when a tag reward is exhausted
        List<ItemReq> requiredItems, // non-null (empty = not item-gated); consumed on claim
        boolean waitNextDay) {

    /** One line of an item-gathering objective: {@code count} of {@code itemId} to hold, then consume. */
    public record ItemReq(String itemId, int count) {}

    /** Item-gated takes priority over stat/advancement gating (mutually exclusive at load). */
    public boolean isItemGated() {
        return requiredItems != null && !requiredItems.isEmpty();
    }

    public boolean isStatGated() {
        return statistic != null && !statistic.isEmpty();
    }

    public boolean hasUnlockingAdvancement() {
        return unlockingAdvancement != null && !unlockingAdvancement.isEmpty();
    }

    public boolean hasAdvancement() {
        return advancement != null && !advancement.isEmpty();
    }

    public boolean hasRewardItems() {
        return rewardItems != null && !rewardItems.isEmpty();
    }

    public boolean hasRewardPersonalTrade() {
        return rewardPersonalTrade != null && !rewardPersonalTrade.isEmpty();
    }

    public boolean hasRewardPersonalTradeTag() {
        return rewardPersonalTradeTag != null && !rewardPersonalTradeTag.isEmpty();
    }

    public boolean hasUnlockApp() {
        return unlockApp != null && !unlockApp.isEmpty();
    }

    public boolean hasRewardSkin() {
        return rewardSkin != null && !rewardSkin.isEmpty();
    }

    public boolean hasRewardSkinTag() {
        return rewardSkinTag != null && !rewardSkinTag.isEmpty();
    }

    public boolean hasFallbackReward() {
        return fallbackReward != null && !fallbackReward.isEmpty();
    }

    public boolean hasFallbackMessagesAfter() {
        return fallbackMessagesAfter != null && !fallbackMessagesAfter.isEmpty();
    }

    /**
     * The after-messages to display for this step: the fallback variant when the step resolved through
     * the exhausted-pool fallback and one is authored, the normal ones otherwise.
     */
    public List<String> messagesAfter(boolean fallbackTriggered) {
        return (fallbackTriggered && hasFallbackMessagesAfter()) ? fallbackMessagesAfter : messagesAfter;
    }
}
