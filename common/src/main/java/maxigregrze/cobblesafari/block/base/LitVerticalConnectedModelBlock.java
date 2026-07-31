package maxigregrze.cobblesafari.block.base;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link VerticalConnectedModelBlock} that also carries {@link DaylightLit#LIT}: every segment of
 * a column lights up on its own, so a three-high bay glows over its whole height.
 */
public class LitVerticalConnectedModelBlock extends VerticalConnectedModelBlock {

    public LitVerticalConnectedModelBlock(Properties properties, Settings settings) {
        super(properties, settings);
        this.registerDefaultState(this.defaultBlockState().setValue(DaylightLit.LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(props -> new LitVerticalConnectedModelBlock(props, this.settings));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DaylightLit.LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        // Placed after dark, lit at once: waiting for a random tick would read as a bug.
        return state == null ? null : state.setValue(DaylightLit.LIT, DaylightLit.shouldBeLit(context.getLevel()));
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DaylightLit.tick(state, level, pos);
    }
}
