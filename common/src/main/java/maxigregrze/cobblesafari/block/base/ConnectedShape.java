package maxigregrze.cobblesafari.block.base;

import net.minecraft.core.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The silhouette a set of horizontal connections resolves to, plus the rotation that carries the
 * canonically-drawn model onto it.
 *
 * <p>This is the Java mirror of the table {@code .private/generate_connected_blockstates.py}
 * derives for the blockstate JSON. <strong>The two must agree</strong>: the script decides which
 * model and {@code y} a state renders with, this class decides which collision box it gets, and a
 * divergence puts the hitbox somewhere the model is not. Both are built the same way - by rotating
 * a declared arm set - and both assert that the 16 states are covered exactly once.</p>
 *
 * <p>Arms are declared as drawn: {@link #END} points north (reaching {@code z = 0}),
 * {@link #CORNER} north + east, {@link #TEE} north + east + west (the gap faces south).</p>
 */
public enum ConnectedShape {

    NONE(Set.of()),
    END(Set.of(Direction.NORTH)),
    STRAIGHT(Set.of(Direction.NORTH, Direction.SOUTH)),
    CORNER(Set.of(Direction.NORTH, Direction.EAST)),
    TEE(Set.of(Direction.NORTH, Direction.EAST, Direction.WEST)),
    CROSS(Set.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));

    /** A silhouette plus the clockwise rotation, in degrees, that orients it. */
    public record Oriented(ConnectedShape shape, int yDegrees) {}

    /** Clockwise, viewed from above - the mod's north=0, east=90, south=180, west=270 convention. */
    private static final Direction[] CYCLE = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final Map<Set<Direction>, Oriented> TABLE = buildTable();

    private final Set<Direction> arms;

    ConnectedShape(Set<Direction> arms) {
        this.arms = arms;
    }

    /** The arm set this silhouette is drawn with, before any rotation. */
    public Set<Direction> arms() {
        return arms;
    }

    /** The silhouette and rotation for a set of live connections. */
    public static Oriented classify(boolean north, boolean east, boolean south, boolean west) {
        return TABLE.get(armSet(north, east, south, west));
    }

    private static Set<Direction> armSet(boolean north, boolean east, boolean south, boolean west) {
        java.util.EnumSet<Direction> set = java.util.EnumSet.noneOf(Direction.class);
        if (north) {
            set.add(Direction.NORTH);
        }
        if (east) {
            set.add(Direction.EAST);
        }
        if (south) {
            set.add(Direction.SOUTH);
        }
        if (west) {
            set.add(Direction.WEST);
        }
        return set;
    }

    private static Set<Direction> rotate(Set<Direction> arms, int steps) {
        java.util.EnumSet<Direction> out = java.util.EnumSet.noneOf(Direction.class);
        for (Direction d : arms) {
            out.add(CYCLE[(indexOf(d) + steps) % 4]);
        }
        return out;
    }

    private static int indexOf(Direction direction) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == direction) {
                return i;
            }
        }
        throw new IllegalArgumentException("not a horizontal direction: " + direction);
    }

    private static Map<Set<Direction>, Oriented> buildTable() {
        Map<Set<Direction>, Oriented> table = new HashMap<>();
        for (ConnectedShape shape : values()) {
            for (int steps = 0; steps < 4; steps++) {
                // Lowest rotation wins, so the canonical orientation keeps y = 0.
                table.putIfAbsent(rotate(shape.arms, steps), new Oriented(shape, steps * 90));
            }
        }
        if (table.size() != 16) {
            throw new IllegalStateException(
                    "ConnectedShape table is inconsistent: expected 16 connection states, got " + table.size());
        }
        return Map.copyOf(table);
    }
}
