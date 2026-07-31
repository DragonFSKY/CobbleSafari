package maxigregrze.cobblesafari.csmusic;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A named music area within a dimension: a set of boxes, optional tags, and an optional csmusic id
 * to play inside them. {@code priority} is set at creation (default {@code defaultAreaPriority} from
 * config) and drives arbitration against trigger rules.
 *
 * <p>A {@code null} {@link #musicId} makes the area <b>purely geometric</b>: it emits no music source
 * of its own and exists only as a target for the {@code when.area} / {@code when.area_tag} rule axes.
 * {@link #tags} let one rule cover a whole family of areas (every town, every cave, …).</p>
 */
public record CsMusicArea(String id,
                          @Nullable String musicId,
                          Set<String> tags,
                          boolean activated,
                          int priority,
                          List<CsMusicBox> boxes) {

    public boolean hasMusic() {
        return musicId != null && !musicId.isBlank();
    }

    public boolean hasTag(String tag) {
        return tag != null && tags.contains(tag);
    }

    public CsMusicArea withMusic(@Nullable String m) {
        return new CsMusicArea(id, m, tags, activated, priority, boxes);
    }

    public CsMusicArea withTags(Set<String> t) {
        return new CsMusicArea(id, musicId, copyTags(t), activated, priority, boxes);
    }

    /**
     * Defensive copy that <b>keeps insertion order</b> - {@code Set.copyOf} does not, and the tag
     * order is what admins see in {@code area list} / {@code info} and in the area file.
     */
    public static Set<String> copyTags(Set<String> tags) {
        return tags.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public CsMusicArea withActivated(boolean v) {
        return new CsMusicArea(id, musicId, tags, v, priority, boxes);
    }

    public CsMusicArea withBoxes(List<CsMusicBox> b) {
        return new CsMusicArea(id, musicId, tags, activated, priority, List.copyOf(b));
    }

    public CsMusicArea withPriority(int p) {
        return new CsMusicArea(id, musicId, tags, activated, p, boxes);
    }

    public boolean contains(int x, int y, int z) {
        for (CsMusicBox box : boxes) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
