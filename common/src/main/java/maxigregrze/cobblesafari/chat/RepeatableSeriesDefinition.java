package maxigregrze.cobblesafari.chat;

import java.util.List;

/**
 * A repeatable step-series declared inside a {@link ChatConversationDefinition}'s
 * {@code repeatableStepsLists}. Once the conversation's base steps are finished, one series is picked
 * at random (by {@code weight}) and played as a direct continuation of the conversation; when it
 * resolves, another is rolled.
 *
 * <ul>
 *   <li>{@code isUnique} — once completed (last reward obtained), this series is never offered again.</li>
 *   <li>{@code isTimed} — must be fully completed before the next daily reset after it starts, else it
 *       fails. The loader forbids {@code waitNextDay} steps inside a timed series.</li>
 *   <li>{@code failMessage} — lang key shown as a transition bubble when a timed series fails.</li>
 *   <li>{@code doDisapear} — once resolved, this series' messages are hidden from the transcript from
 *       the next daily reset on (kept in memory for {@code isUnique}, just not sent to the client).</li>
 *   <li>An {@code unlockingAdvancement} on the <em>first</em> step acts as the series' entry condition:
 *       the series is not rolled at all until the player owns that advancement. Gates on later steps
 *       hold the series mid-run instead.</li>
 *   <li>{@code prerequisite} — id of another series of the same conversation that the player must have
 *       <em>completed at least once</em> before this one can be rolled. Works with unique and
 *       non-unique series alike. The loader rejects unknown ids, self-references and cycles.</li>
 * </ul>
 */
public record RepeatableSeriesDefinition(
        String id,
        int weight,
        boolean isUnique,
        boolean isTimed,
        String failMessage,
        boolean doDisapear,
        String prerequisite, // nullable id of a series that must have been completed once
        List<ChatStepDefinition> steps) {

    public boolean hasFailMessage() {
        return failMessage != null && !failMessage.isEmpty();
    }

    public boolean hasPrerequisite() {
        return prerequisite != null && !prerequisite.isEmpty();
    }

    public ChatStepDefinition step(int index) {
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return steps.get(index);
    }
}
