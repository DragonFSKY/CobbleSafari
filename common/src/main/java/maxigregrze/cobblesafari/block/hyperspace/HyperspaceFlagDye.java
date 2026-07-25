package maxigregrze.cobblesafari.block.hyperspace;

import maxigregrze.cobblesafari.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.List;

/**
 * Shared dyeing logic for the two Hyperspace flags (small &amp; large). Holds the
 * blockstate properties and the whole interaction gesture so the flag blocks stay pure
 * adapters with no dye logic of their own.
 *
 * <p>{@link #DYED} selects the model (default single texture vs. colored+overlay), while
 * {@link #COLOR} feeds the client block-color handler on {@code tintindex 0}. Both live in
 * the blockstate - no block entity, NBT or network packet is needed (persistence and sync
 * are automatic).</p>
 */
public final class HyperspaceFlagDye {

    public static final BooleanProperty DYED = BooleanProperty.create("dyed");
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    private HyperspaceFlagDye() {
    }

    /** Register the dye properties - call from a dyeable flag's {@code createBlockStateDefinition}. */
    public static void appendProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DYED, COLOR);
    }

    /** Pin a state to the "not dyed" default ({@code dyed=false, color=white}) for registerDefaultState. */
    public static BlockState undyed(BlockState state) {
        return state.setValue(DYED, false).setValue(COLOR, DyeColor.WHITE);
    }

    /**
     * Apply a dye (any vanilla {@link DyeItem}) to every {@code cell}, or reset them to the
     * default look when holding the Tinkhammer. A dye is consumed in survival; the Tinkhammer
     * (a tool) is not. Returns {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} for any other item.
     */
    public static ItemInteractionResult interact(ItemStack stack, Level level, Player player, List<BlockPos> cells) {
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide()) {
                for (BlockPos cell : cells) {
                    recolor(level, cell, true, dye.getDyeColor());
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (stack.is(ModItems.TINKHAMMER)) {
            if (!level.isClientSide()) {
                for (BlockPos cell : cells) {
                    recolor(level, cell, false, null);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static void recolor(Level level, BlockPos pos, boolean dyed, DyeColor color) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(DYED)) {
            return;
        }
        BlockState next = state.setValue(DYED, dyed);
        if (color != null) {
            next = next.setValue(COLOR, color);
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
    }
}
