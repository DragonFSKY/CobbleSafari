package maxigregrze.cobblesafari.block.base;

import net.minecraft.util.StringRepresentable;

/**
 * Where a block sits inside a vertical run of connected peers. Derived, never chosen: a lone
 * block is {@link #SINGLE}, and stacking one on top of it turns the pair into
 * {@link #BOTTOM} + {@link #TOP}.
 */
public enum VerticalSegment implements StringRepresentable {
    SINGLE("single"),
    BOTTOM("bottom"),
    MIDDLE("middle"),
    TOP("top");

    private final String name;

    VerticalSegment(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static VerticalSegment of(boolean peerBelow, boolean peerAbove) {
        if (peerBelow && peerAbove) {
            return MIDDLE;
        }
        if (peerBelow) {
            return TOP;
        }
        return peerAbove ? BOTTOM : SINGLE;
    }
}
