package maxigregrze.cobblesafari.network;

import maxigregrze.cobblesafari.CobbleSafari;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client snapshot of the Rotom Phone notification dots. Fully derived state (nothing is
 * persisted): the ids of the unlocked conversations that currently have unread messages or a
 * claimable reward, plus whether the GTS holds at least one Pokémon waiting to be taken back.
 *
 * <p>Kept separate from {@link ChatConversationSyncPayload} on purpose: the contact list is
 * configuration (sent at join and on datapack reload) while this is volatile state refreshed while
 * the phone is open.
 */
public record RotomPhoneNotificationPayload(List<String> pendingConvIds, boolean gtsPending)
        implements CustomPacketPayload {

    /** Upper bound on the decoded list; guards the codec against a malformed payload. */
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_ID_LEN = 128;

    public static final CustomPacketPayload.Type<RotomPhoneNotificationPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(CobbleSafari.MOD_ID, "rotom_phone_notification"));

    public static final StreamCodec<FriendlyByteBuf, RotomPhoneNotificationPayload> STREAM_CODEC =
            StreamCodec.of(RotomPhoneNotificationPayload::write, RotomPhoneNotificationPayload::read);

    private static void write(FriendlyByteBuf buf, RotomPhoneNotificationPayload p) {
        int n = Math.min(p.pendingConvIds.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(p.pendingConvIds.get(i), MAX_ID_LEN);
        }
        buf.writeBoolean(p.gtsPending);
    }

    private static RotomPhoneNotificationPayload read(FriendlyByteBuf buf) {
        int n = Math.min(Math.max(0, buf.readVarInt()), MAX_ENTRIES);
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(MAX_ID_LEN));
        }
        return new RotomPhoneNotificationPayload(ids, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
