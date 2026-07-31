package maxigregrze.cobblesafari.rotomphone;

import maxigregrze.cobblesafari.block.misc.OnlineFeaturePcBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derogation to the Rotom-Phone possession check for the Online Feature PC blocks.
 *
 * <p>The GTS / Wonder Trade / Union Room app payloads are gated server-side by
 * {@link RotomPhoneServerHandler#hasPhone(ServerPlayer)} so a forged payload from a phone-less
 * client is dropped. The Online Feature PC blocks open the very same app screens <b>without</b>
 * requiring a phone, so that gate has to let their users through - otherwise the screen opens and
 * never receives its first snapshot, leaving an empty shell.</p>
 *
 * <p>The derogation stays server-authoritative: using the block records where it happened, and every
 * later check re-validates that the player is still next to a real, still-existing PC block of the
 * matching kind in the same dimension. A client that never used a block gets nothing.</p>
 */
public final class OnlinePcAccess {

    /** Max squared distance between the player and the PC block for the grant to stay valid. */
    private static final double MAX_USE_DISTANCE_SQR = 64.0;

    private record Grant(ResourceKey<Level> dimension, BlockPos pos, OnlineFeaturePcBlock.Kind kind) {}

    private static final Map<UUID, Grant> GRANTS = new ConcurrentHashMap<>();

    private OnlinePcAccess() {}

    /** Records that {@code player} just used an Online Feature PC of {@code kind} at {@code pos}. */
    public static void grant(ServerPlayer player, OnlineFeaturePcBlock.Kind kind, BlockPos pos) {
        GRANTS.put(player.getUUID(), new Grant(player.level().dimension(), pos.immutable(), kind));
    }

    /** Drops the grant; call on disconnect so the map never outlives the session. */
    public static void clear(UUID playerId) {
        GRANTS.remove(playerId);
    }

    /**
     * {@code true} when the player may use the given app without a phone, i.e. they used an Online
     * Feature PC of that kind and are still standing next to it.
     */
    public static boolean isUsingOnlinePc(ServerPlayer player, OnlineFeaturePcBlock.Kind kind) {
        Grant grant = GRANTS.get(player.getUUID());
        if (grant == null || grant.kind() != kind || !grant.dimension().equals(player.level().dimension())) {
            return false;
        }
        BlockPos pos = grant.pos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_USE_DISTANCE_SQR) {
            return false;
        }
        BlockState state = player.level().getBlockState(pos);
        return state.getBlock() instanceof OnlineFeaturePcBlock pc && pc.getKind() == kind;
    }

    /**
     * Shared entry gate for the phone apps: either the player owns a phone, or they are using the
     * matching Online Feature PC block.
     */
    public static boolean canUseApp(ServerPlayer player, OnlineFeaturePcBlock.Kind kind) {
        return RotomPhoneServerHandler.hasPhone(player) || isUsingOnlinePc(player, kind);
    }
}
