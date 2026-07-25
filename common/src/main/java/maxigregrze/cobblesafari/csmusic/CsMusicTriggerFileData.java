package maxigregrze.cobblesafari.csmusic;

import java.util.ArrayList;
import java.util.List;

/**
 * Gson DTO for a trigger file {@code data/<ns>/csmusic/definition/*.json}. A file holds a list of
 * cumulable rules; each rule matches when all declared conditions ({@link When}) hold.
 */
public class CsMusicTriggerFileData {

    public List<Rule> rules = new ArrayList<>();

    public static class Rule {
        /** Explicit csmusic id (exclusive with {@link #tag}). */
        public String music;
        /** Random pool by csmusic tag (exclusive with {@link #music}). */
        public String tag;
        public int priority = 1;
        public When when = new When();
    }

    public static class When {
        public String dimension;
        public String biome;
        public String biome_tag;
        /** never | any | in_battle | wild | npc | pvp (default: any). Folds the old battle_type in. */
        public String battle;
        public String species;
        public String form;
        /**
         * Naturally generated structure the player must stand in: a structure id
         * ({@code minecraft:village_plains}), a structure tag ({@code #minecraft:village}),
         * or {@code *} / {@code any} for any structure. Omitted ⇒ no constraint.
         */
        public String structure;
        /**
         * Piece of a naturally generated structure the player must stand in, as a pattern over the
         * piece id where {@code *} matches any run of characters (including {@code /}). Jigsaw
         * pieces are identified by their template ({@code minecraft:village/plains/houses/...}),
         * legacy pieces by their piece type ({@code minecraft:mscorridor}). Omitted ⇒ no constraint.
         */
        public String structure_piece;
    }
}
