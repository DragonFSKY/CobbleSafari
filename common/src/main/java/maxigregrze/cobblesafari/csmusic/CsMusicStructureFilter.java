package maxigregrze.cobblesafari.csmusic;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * Compiled {@code when.structure} filter: any structure ({@code *}), one structure id, or one
 * structure tag ({@code #ns:path}). Only naturally generated structures can ever match - the
 * lookup reads worldgen structure references (see {@link CsMusicStructureTracker}).
 */
public record CsMusicStructureFilter(
        Kind kind,
        @Nullable ResourceLocation id,
        @Nullable TagKey<Structure> tag
) {
    /** Which of the three accepted json forms this filter was compiled from. */
    public enum Kind { ANY, ID, TAG }

    private static final CsMusicStructureFilter ANY_STRUCTURE =
            new CsMusicStructureFilter(Kind.ANY, null, null);

    /** Parses the json value; returns {@code null} when the raw string is not a valid filter. */
    @Nullable
    public static CsMusicStructureFilter fromJson(String raw) {
        String s = raw.trim();
        if ("*".equals(s) || "any".equalsIgnoreCase(s)) {
            return ANY_STRUCTURE;
        }
        if (s.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(s.substring(1).toLowerCase(Locale.ROOT));
            return rl == null ? null
                    : new CsMusicStructureFilter(Kind.TAG, null, TagKey.create(Registries.STRUCTURE, rl));
        }
        ResourceLocation rl = ResourceLocation.tryParse(s.toLowerCase(Locale.ROOT));
        return rl == null ? null : new CsMusicStructureFilter(Kind.ID, rl, null);
    }

    /** True if a structure start with this id and these tags satisfies the filter. */
    boolean accepts(ResourceLocation structureId, Set<TagKey<Structure>> structureTags) {
        return switch (kind) {
            case ANY -> true;
            case ID -> structureId.equals(id);
            case TAG -> structureTags.contains(tag);
        };
    }
}
