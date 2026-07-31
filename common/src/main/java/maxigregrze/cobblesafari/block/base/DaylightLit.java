package maxigregrze.cobblesafari.block.base;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Shared behaviour for blocks that light up whenever the sky is dark - i.e. exactly when a player
 * could sleep.
 *
 * <p>{@code Properties.lightLevel} is baked at registration and reads only the {@link BlockState},
 * never the world or the time, so the emission has to ride on a blockstate property. Flipping that
 * property on every placed block is what this class centralises: the condition, the light curve and
 * the body of {@code randomTick}, so the four concrete Lit* blocks stay one-liners.</p>
 *
 * <p>The update is driven by the <em>random tick</em>, which costs nothing new - the game already
 * walks these positions. The trade-off is that a façade lights up over a minute or two rather than
 * all at once; that is deliberate, not a bug.</p>
 */
public final class DaylightLit {

    /** Light emitted while lit. */
    public static final int LIGHT_LEVEL = 10;

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private DaylightLit() {}

    /**
     * True when the sky is dark enough that a player could sleep. {@code isDay()} already folds in
     * the weather: a thunderstorm raises {@code skyDarken}, which is the very mechanism vanilla uses
     * to allow sleeping during the day, so no separate storm test is needed.
     */
    public static boolean shouldBeLit(Level level) {
        return !level.isDay();
    }

    /** Light curve to hand to {@code Properties.lightLevel}. */
    public static int lightLevel(BlockState state) {
        return Boolean.TRUE.equals(state.getValue(LIT)) ? LIGHT_LEVEL : 0;
    }

    /** Body of {@code randomTick}: re-sync the state with the sky, writing only on a change. */
    public static void tick(BlockState state, ServerLevel level, BlockPos pos) {
        boolean wanted = shouldBeLit(level);
        if (Boolean.TRUE.equals(state.getValue(LIT)) != wanted) {
            level.setBlock(pos, state.setValue(LIT, wanted), Block.UPDATE_ALL);
        }
    }
}
