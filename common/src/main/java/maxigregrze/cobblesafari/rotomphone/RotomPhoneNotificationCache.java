package maxigregrze.cobblesafari.rotomphone;

import maxigregrze.cobblesafari.network.RotomPhoneNotificationPayload;

import java.util.Set;

/**
 * Client cache of the notification dots synced from the server. Mirrors
 * {@link RotomPhoneClientCache} / {@link ChatConversationClientCache}: a static holder is enough
 * since only one phone is open at a time.
 *
 * <p>No reset on disconnect is needed: the server pushes a fresh snapshot in
 * {@code RotomPhoneServerHandler.openPhone} <em>before</em> the open payload, so the dots are always
 * rewritten from the current world before any screen that reads them can render.
 */
public final class RotomPhoneNotificationCache {

    private static Set<String> pendingConvIds = Set.of();
    private static boolean gtsPending;
    private static boolean wonderPending;

    private RotomPhoneNotificationCache() {}

    public static void apply(RotomPhoneNotificationPayload payload) {
        pendingConvIds = Set.copyOf(payload.pendingConvIds());
        gtsPending = payload.gtsPending();
        wonderPending = payload.wonderPending();
    }

    /** True if this conversation has unread messages or a claimable reward. */
    public static boolean isConversationPending(String convId) {
        return convId != null && pendingConvIds.contains(convId);
    }

    /**
     * True if any unlocked conversation is pending - drives the chat app icon dot. The server only
     * ever lists conversations this player has unlocked, so a non-empty set is the whole condition.
     */
    public static boolean isAnyConversationPending() {
        return !pendingConvIds.isEmpty();
    }

    public static boolean isGtsPending() {
        return gtsPending;
    }

    /**
     * True if a Wonder Trade event is running that this player has not opened the Wonder app for since
     * its current instance started - drives the Wonder app icon dot.
     */
    public static boolean isWonderPending() {
        return wonderPending;
    }
}
