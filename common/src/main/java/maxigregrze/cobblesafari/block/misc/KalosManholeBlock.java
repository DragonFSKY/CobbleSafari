package maxigregrze.cobblesafari.block.misc;

import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Manhole cover shaped like a trapdoor. Only exists because TrapDoorBlock's constructor is protected. */
public class KalosManholeBlock extends TrapDoorBlock {
    public KalosManholeBlock(BlockSetType type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }
}
