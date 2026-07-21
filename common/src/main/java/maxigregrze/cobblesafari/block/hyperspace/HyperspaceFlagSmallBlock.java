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
 * Small Hyperspace flag: a thin, dyeable adapter over the shared {@link HyperspaceDirectionalBlock}
 * (wall-mounted, no collision). All dye behaviour is delegated to {@link HyperspaceFlagDye}; this
 * class only wires the properties and the interaction so the shared base stays un-dyeable for the
 * barrier / railing / shutters that reuse it.
 */
public class HyperspaceFlagSmallBlock extends HyperspaceDirectionalBlock {

    private final VoxelShape shape;

    public HyperspaceFlagSmallBlock(Properties properties, VoxelShape shape) {
        super(properties, shape, true, false);
        this.shape = shape;
        registerDefaultState(HyperspaceFlagDye.undyed(defaultBlockState()));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(props -> new HyperspaceFlagSmallBlock(props, this.shape));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        HyperspaceFlagDye.appendProperties(builder);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return HyperspaceFlagDye.interact(stack, level, player, List.of(pos));
    }
}
