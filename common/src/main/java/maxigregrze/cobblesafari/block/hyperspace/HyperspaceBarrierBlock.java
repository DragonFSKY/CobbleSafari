package maxigregrze.cobblesafari.block.hyperspace;

import com.mojang.serialization.MapCodec;
import maxigregrze.cobblesafari.block.base.BlockShapeUtils;
import maxigregrze.cobblesafari.block.base.HorizontalModelBlock;
import maxigregrze.cobblesafari.init.ModItems;
import maxigregrze.cobblesafari.manager.BannedItemsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Hyperspace road barrier. Fence-like: the selection box is the visible 1-block-tall model,
 * but the collision box is 1.5 blocks tall so it can't be jumped over.
 *
 * <p>Right-clicking with the Tinkhammer toggles {@link #BORDER}: the model, selection box and
 * collision box all slide 7.5px toward the block's back face (away from {@code FACING}), so the
 * barrier sits on the edge between two blocks instead of in the middle of its cell. Hammering
 * again resets it. Both positions rotate with the block's facing. Repositioning is refused for
 * players who can't build (adventure/spectator) or in dimensions where the dimensional-ban
 * config forbids block breaking.</p>
 */
public class HyperspaceBarrierBlock extends HorizontalModelBlock {

    public static final BooleanProperty BORDER = BooleanProperty.create("border");

    // Shapes authored for NORTH (matching the model); border variants are shifted 7.5px toward
    // the back (+Z), which straddles the rear block edge (z: 14.5 .. 16.5).
    private static final VoxelShape SELECTION_NORTH = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape COLLISION_NORTH = Block.box(0, 0, 7, 16, 24, 9);
    private static final VoxelShape SELECTION_BORDER_NORTH = Block.box(0, 0, 14.5, 16, 16, 16.5);
    private static final VoxelShape COLLISION_BORDER_NORTH = Block.box(0, 0, 14.5, 16, 24, 16.5);

    private static final Map<Direction, VoxelShape> SELECTION =
            BlockShapeUtils.precompute(SELECTION_NORTH, Direction.NORTH);
    private static final Map<Direction, VoxelShape> COLLISION =
            BlockShapeUtils.precompute(COLLISION_NORTH, Direction.NORTH);
    private static final Map<Direction, VoxelShape> SELECTION_BORDER =
            BlockShapeUtils.precompute(SELECTION_BORDER_NORTH, Direction.NORTH);
    private static final Map<Direction, VoxelShape> COLLISION_BORDER =
            BlockShapeUtils.precompute(COLLISION_BORDER_NORTH, Direction.NORTH);

    public HyperspaceBarrierBlock(Properties properties) {
        super(properties, Settings.builder()
                .shape(SELECTION_NORTH)
                .authoredFacing(Direction.NORTH)
                .build());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BORDER, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(HyperspaceBarrierBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BORDER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(BORDER) ? SELECTION_BORDER : SELECTION;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(BORDER) ? COLLISION_BORDER : COLLISION;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return tinkhammerInteract(stack, state, level, pos, player);
    }

    /**
     * Shared Tinkhammer logic - invoked from the block and from
     * {@link maxigregrze.cobblesafari.item.TinkhammerItem} so sneaking with the tool still
     * reaches the handler when vanilla skips block activation.
     */
    public static ItemInteractionResult tinkhammerInteract(ItemStack stack, BlockState state, Level level,
                                                           BlockPos pos, @Nullable Player player) {
        if (!stack.is(ModItems.TINKHAMMER)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Repositioning counts as a build action: refuse it for adventure/spectator players and
        // in dimensions whose dimensional-ban config forbids block breaking (creative bypasses,
        // matching DimensionalBanEventHandler).
        if (player == null || !player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }
        if (!player.isCreative() && !BannedItemsManager.isBlockBreakingAllowed(level.dimension())) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("cobblesafari.ban.block_breaking_banned"));
            }
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(BORDER), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
