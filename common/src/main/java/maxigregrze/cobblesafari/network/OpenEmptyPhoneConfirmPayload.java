package maxigregrze.cobblesafari.network;

import maxigregrze.cobblesafari.CobbleSafari;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client order to open the empty-phone confirmation for a given Rotom.
 *
 * <p>{@code speciesId} and {@code aspects} are what the screen renders the model from: the aspect
 * set already carries both the shiny flag and the alternate form, so no party lookup is needed
 * client-side. {@code rotomIsShiny} is transported separately because it drives the warning
 * <em>text</em>, not the model.
 */
public record OpenEmptyPhoneConfirmPayload(
        String rotomName,
        int rotomLevel,
        boolean rotomIsShiny,
        String speciesId,
        List<String> aspects
) implements CustomPacketPayload {

    private static final int MAX_NAME_LEN = 128;
    private static final int MAX_SPECIES_LEN = 256;
    private static final int MAX_ASPECTS = 32;
    private static final int MAX_ASPECT_LEN = 64;

    public static final CustomPacketPayload.Type<OpenEmptyPhoneConfirmPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CobbleSafari.MOD_ID, "open_empty_phone_confirm"));

    public static final StreamCodec<FriendlyByteBuf, OpenEmptyPhoneConfirmPayload> STREAM_CODEC = StreamCodec.of(
            OpenEmptyPhoneConfirmPayload::write,
            OpenEmptyPhoneConfirmPayload::read
    );

    private static void write(FriendlyByteBuf buf, OpenEmptyPhoneConfirmPayload payload) {
        buf.writeUtf(payload.rotomName, MAX_NAME_LEN);
        buf.writeInt(payload.rotomLevel);
        buf.writeBoolean(payload.rotomIsShiny);
        buf.writeUtf(payload.speciesId, MAX_SPECIES_LEN);
        int count = Math.min(payload.aspects.size(), MAX_ASPECTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(payload.aspects.get(i), MAX_ASPECT_LEN);
        }
    }

    private static OpenEmptyPhoneConfirmPayload read(FriendlyByteBuf buf) {
        String name = buf.readUtf(MAX_NAME_LEN);
        int level = buf.readInt();
        boolean shiny = buf.readBoolean();
        String speciesId = buf.readUtf(MAX_SPECIES_LEN);
        int count = Math.min(buf.readVarInt(), MAX_ASPECTS);
        List<String> aspects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            aspects.add(buf.readUtf(MAX_ASPECT_LEN));
        }
        return new OpenEmptyPhoneConfirmPayload(name, level, shiny, speciesId, List.copyOf(aspects));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
