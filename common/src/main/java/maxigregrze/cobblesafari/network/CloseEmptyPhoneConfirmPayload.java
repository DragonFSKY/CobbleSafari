package maxigregrze.cobblesafari.network;

import maxigregrze.cobblesafari.CobbleSafari;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client order to close an open empty-phone confirmation, sent when the pending fill
 * expires server-side. Mirrors {@link CloseTpAcceptPayload}: no field, the screen identity is
 * enough.
 */
public record CloseEmptyPhoneConfirmPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseEmptyPhoneConfirmPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CobbleSafari.MOD_ID, "close_empty_phone_confirm"));

    public static final StreamCodec<FriendlyByteBuf, CloseEmptyPhoneConfirmPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new CloseEmptyPhoneConfirmPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
