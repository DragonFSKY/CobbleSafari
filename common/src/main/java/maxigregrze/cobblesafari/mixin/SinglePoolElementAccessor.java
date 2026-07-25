package maxigregrze.cobblesafari.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected {@code template} of {@link SinglePoolElement} so csmusic can name a jigsaw
 * piece (a village house is {@code minecraft:village/plains/houses/...}). Vanilla offers no getter,
 * and the public {@code StructurePoolElement.CODEC} route would need RegistryOps - its codec embeds
 * a {@code Holder<StructureProcessorList>} - plus an allocation per piece.
 *
 * <p>Also applies to {@code LegacySinglePoolElement}, which extends this class and is what the
 * vanilla village pools actually use.</p>
 */
@Mixin(SinglePoolElement.class)
public interface SinglePoolElementAccessor {

    @Accessor("template")
    Either<ResourceLocation, StructureTemplate> getTemplate();
}
