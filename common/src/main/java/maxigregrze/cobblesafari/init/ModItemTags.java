package maxigregrze.cobblesafari.init;

import maxigregrze.cobblesafari.CobbleSafari;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {

    public static final TagKey<Item> TRAPS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CobbleSafari.MOD_ID, "traps"));

    /** All balm variants (thrown boss-damage items); purged from participants when a fight ends. */
    public static final TagKey<Item> BALM = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(CobbleSafari.MOD_ID, "balm"));

    private ModItemTags() {}
}
