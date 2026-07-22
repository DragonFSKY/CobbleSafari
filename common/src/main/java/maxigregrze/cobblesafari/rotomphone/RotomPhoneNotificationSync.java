package maxigregrze.cobblesafari.rotomphone;

import maxigregrze.cobblesafari.chat.ChatConversationDefinition;
import maxigregrze.cobblesafari.chat.ChatConversationRegistry;
import maxigregrze.cobblesafari.chat.ChatConversationService;
import maxigregrze.cobblesafari.data.ChatProgressSavedData;
import maxigregrze.cobblesafari.data.GtsSavedData;
import maxigregrze.cobblesafari.network.RotomPhoneNotificationPayload;
import maxigregrze.cobblesafari.platform.Services;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and sends {@link RotomPhoneNotificationPayload} — the notification-dot snapshot. Mirrors
 * {@link ChatConversationSync}, but where the contact list is configuration this is volatile state:
 * it is pushed on phone open and after every chat/GTS mutation, and polled by the open phone screen.
 *
 * <p>Everything here is read-only: {@link ChatConversationService#hasPendingAttention} never creates
 * a progress entry, never advances a questline and never marks the save dirty.
 */
public final class RotomPhoneNotificationSync {

    private RotomPhoneNotificationSync() {}

    public static void syncToPlayer(ServerPlayer player) {
        List<String> pending = new ArrayList<>();
        ChatProgressSavedData data = ChatProgressSavedData.get(player.server);
        if (data != null) {
            for (ChatConversationDefinition c : ChatConversationRegistry.getAllSorted()) {
                if (ChatConversationRegistry.isUnlockedByPlayer(player, c)
                        && ChatConversationService.hasPendingAttention(player, c, data)) {
                    pending.add(c.id());
                }
            }
        }
        boolean gtsPending =
                !GtsSavedData.get(player.server).findSuccessesByRecipient(player.getUUID()).isEmpty();
        Services.PLATFORM.sendPayloadToPlayer(player, new RotomPhoneNotificationPayload(pending, gtsPending));
    }
}
