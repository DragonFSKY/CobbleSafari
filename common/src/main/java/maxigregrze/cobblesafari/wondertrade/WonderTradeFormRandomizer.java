package maxigregrze.cobblesafari.wondertrade;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Injects a random cosmetic form into a Wonder Trade pool line when the datapack left it unspecified.
 *
 * <p>Only covers Cobblemon features whose {@code default} is a fixed value. Features defaulting to
 * {@code "random"} (Unown letters, Burmy cloaks, Shellos seas, Deerling seasons, Oricorio styles,
 * Alcremie creams and decorations, Tatsugiri textures, Arbok belly patterns, Gyarados eye colour)
 * are already rolled by Cobblemon itself and are deliberately absent from the table below.
 *
 * <p>Regional forms are never rolled here: they are spelled out as their own pool entries
 * ({@code vulpix alolan}, {@code pikachu region_bias=alola}), so the datapack stays authoritative.
 */
public final class WonderTradeFormRandomizer {

    /** One randomisable cosmetic axis: a PokemonProperties key and every value it may take. */
    private record FormRule(String key, List<String> choices) {}

    private static final Map<String, List<FormRule>> RULES = buildRules();

    private WonderTradeFormRandomizer() {}

    /**
     * Returns {@code speciesLine} with one {@code key=value} appended per rule the line does not
     * already pin down. Species with no rule, and lines that already specify every axis, are
     * returned unchanged.
     */
    public static String apply(String speciesLine, RandomSource random) {
        if (speciesLine == null || speciesLine.isBlank()) {
            return speciesLine;
        }
        String trimmed = speciesLine.trim();
        String[] tokens = trimmed.split("\\s+");
        String speciesId = tokens[0].toLowerCase(Locale.ROOT);
        int colon = speciesId.indexOf(':');
        if (colon >= 0) {
            speciesId = speciesId.substring(colon + 1);
        }
        List<FormRule> rules = RULES.get(speciesId);
        if (rules == null) {
            return speciesLine;
        }
        Set<String> alreadySet = new HashSet<>();
        for (int i = 1; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            alreadySet.add((eq >= 0 ? tokens[i].substring(0, eq) : tokens[i]).toLowerCase(Locale.ROOT));
        }
        StringBuilder line = new StringBuilder(trimmed);
        for (FormRule rule : rules) {
            if (alreadySet.contains(rule.key())) {
                continue;
            }
            List<String> choices = rule.choices();
            line.append(' ').append(rule.key()).append('=')
                    .append(choices.get(random.nextInt(choices.size())));
        }
        return line.toString();
    }

    private static void put(Map<String, List<FormRule>> map, String key, List<String> choices, String... species) {
        FormRule rule = new FormRule(key, choices);
        for (String s : species) {
            map.computeIfAbsent(s, k -> new ArrayList<>()).add(rule);
        }
    }

    /**
     * Keys and choice lists are copied verbatim from {@code data/cobblemon/species_features/*.json};
     * the species of each row come from the matching {@code species_feature_assignments} entry.
     */
    private static Map<String, List<FormRule>> buildRules() {
        Map<String, List<FormRule>> map = new HashMap<>();

        put(map, "striped", List.of("red", "blue", "white"), "basculin");

        put(map, "vivillon_wings", List.of(
                "icy-snow", "polar", "tundra", "continental", "garden", "elegant", "meadow", "modern",
                "marine", "archipelago", "high-plains", "sandstorm", "river", "monsoon", "savanna",
                "sun", "ocean", "jungle", "fancy", "poke-ball", "inferno", "void", "forsaken"),
                "vivillon");

        // "eternal" (AZ's Floette) is excluded on purpose; Cobblemon's exclusive "pink" is kept.
        put(map, "flower", List.of("red", "yellow", "orange", "blue", "white", "pink"),
                "flabebe", "floette", "florges");

        // Rockruff, Lycanroc and Minior are among the 174 species Cobblemon ships data but no model
        // for; these two rules only take visible effect once a species sidemod supplies the assets.
        put(map, "wolf_form", List.of("midday", "midnight", "dusk"), "rockruff", "lycanroc");

        put(map, "core_color", List.of("red", "orange", "yellow", "green", "blue", "indigo", "violet"),
                "minior");

        put(map, "tea_authenticity", List.of("phony", "antique"), "sinistea", "polteageist");
        // matcha_authenticity declares four values for two species: two belong to each.
        put(map, "matcha_authenticity", List.of("counterfeit", "artisan"), "poltchageist");
        put(map, "matcha_authenticity", List.of("unremarkable", "masterpiece"), "sinistcha");

        put(map, "maushold_family", List.of("three", "four"), "tandemaus", "maushold");
        put(map, "landsnake_form", List.of("two-segment", "three-segment"), "dunsparce", "dudunsparce");

        // Caterpie, Metapod and Oddish declare the feature but ship no valencian resolver variation.
        put(map, "valencian", List.of("true", "false"), "butterfree");
        put(map, "valencian", List.of("true", "false"), "gloom", "vileplume", "bellossom");

        put(map, "mooshtank", List.of("false", "red", "brown"), "miltank");

        // One list per species: the `tree` feature declares 15 woods, but each species renders its
        // own subset, and Phantump/Trevenant render none at all. Offering a wood with no resolver
        // would silently inflate the odds of a plain-looking Pokemon.
        put(map, "tree", List.of(
                "none", "oak", "birch", "darkoak", "acacia", "azalea", "swamp", "jungle", "spruce",
                "mangrove", "cherry"),
                "torterra");
        put(map, "tree", List.of(
                "none", "oak", "birch", "darkoak", "acacia", "jungle", "spruce", "mangrove", "cherry",
                "apricorn", "crimson", "warped", "saccharine"),
                "komala");
        put(map, "tree", List.of(
                "none", "oak", "birch", "darkoak", "acacia", "jungle", "spruce", "mangrove", "cherry",
                "crimson", "warped", "saccharine"),
                "timburr");

        put(map, "netherite_coating", List.of(
                "none", "stage1", "stage2", "stage3", "stage4", "stage5", "stage6", "stage7", "full"),
                "gholdengo");

        put(map, "magikarp_jump", List.of(
                "standard", "apricot-stripes", "apricot-tiger", "apricot-zebra", "black-forehead",
                "black-mask", "blue-raindrops", "blue-saucy", "brown-stripes", "brown-tiger",
                "brown-zebra", "calico-orange-gold", "calico-orange-white", "calico-orange-white-black",
                "calico-white-orange", "gray-bubbles", "gray-diamonds", "gray-patches", "orange-dapples",
                "orange-forehead", "orange-mask", "orange-orca", "orange-two-tone", "pink-dapples",
                "pink-orca", "pink-two-tone", "purple-bubbles", "purple-diamonds", "purple-patches",
                "skelly", "violet-raindrops", "violet-saucy"),
                "magikarp", "gyarados");

        put(map, "color", List.of(
                "none", "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"),
                "wooloo", "dubwool");

        put(map, "metals", List.of("none", "copper", "iron", "gold", "netherite"), "gurdurr");

        return map;
    }
}
