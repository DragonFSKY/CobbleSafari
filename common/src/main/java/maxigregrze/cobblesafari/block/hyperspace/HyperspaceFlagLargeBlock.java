package maxigregrze.cobblesafari.block.hyperspace;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Large Hyperspace flag: a thin, dyeable adapter over the reusable 3-cell multiblock
 * {@link HyperspaceTriBlock}. Dyeing any cell recolours the whole column. All dye behaviour is
 * delegated to {@link HyperspaceFlagDye}; {@link HyperspaceTriBlock} itself is left generic
 * (untouched) so it can back future multiblocks — the bottom cell is recomputed here from the
 * public {@link HyperspaceTriBlock#PART} property.
 */
public class HyperspaceFlagLargeBlock extends HyperspaceTriBlock {

    private final boolean wallMounted;
    private final boolean hasCollision;
    private final VoxelShape shape;

    public HyperspaceFlagLargeBlock(Properties properties, boolean wallMounted, boolean hasCollision, VoxelShape shape) {
        super(properties, wallMounted, hasCollision, shape);
        this.wallMounted = wallMounted;
        this.hasCollision = hasCollision;
        this.shape = shape;
        registerDefaultState(HyperspaceFlagDye.undyed(defaultBlockState()));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(props -> new HyperspaceFlagLargeBlock(props, this.wallMounted, this.hasCollision, this.shape));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        HyperspaceFlagDye.appendProperties(builder);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos bottom = switch (state.getValue(PART)) {
            case BOTTOM -> pos;
            case CENTER -> pos.below();
            case TOP -> pos.below(2);
        };
        return HyperspaceFlagDye.interact(stack, level, player, List.of(bottom, bottom.above(), bottom.above(2)));
    }
}
