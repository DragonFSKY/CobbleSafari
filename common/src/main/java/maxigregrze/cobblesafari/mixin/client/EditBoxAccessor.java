package maxigregrze.cobblesafari.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private scroll/selection state of {@link EditBox} so screens can render the
 * box's text themselves (vanilla's renderWidget hardcodes a drop shadow; the NeoForge-only
 * setTextShadow patch cannot be used from common code).
 */
@Mixin(EditBox.class)
public interface EditBoxAccessor {

    @Accessor("displayPos")
    int getDisplayPos();

    @Accessor("highlightPos")
    int getHighlightPos();
}
