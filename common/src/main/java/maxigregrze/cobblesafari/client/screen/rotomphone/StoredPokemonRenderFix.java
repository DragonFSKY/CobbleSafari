package maxigregrze.cobblesafari.client.screen.rotomphone;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Restores the renderable form of a Pokémon rebuilt client-side from stored NBT (GTS offers, Wonder
 * Trade pool, trade animations).
 *
 * <p>Cobblemon persists the form under {@code FormId} and its codec restores it, but the trailing
 * {@code initialize()} re-derives the form from the aspect set ({@code updateAspects()} then
 * {@code updateForm()}). Aspects for data-driven forms - regional forms such as Alolan are declared
 * by a {@code flag} species feature with {@code isAspect: true} - are supplied by an
 * {@code AspectProvider} that {@code SpeciesFeatures.loadOnClient} never registers (it only fills
 * {@code codeFeatures}). Client-side the aspect set therefore comes back without the form aspect and
 * the form collapses to the standard one, which is why the model rendered in the phone showed the
 * base form while the stored data stayed correct.
 *
 * <p>{@code FormId} is authoritative, so {@link #restoreForm} re-applies the form's own aspects
 * through {@code forcedAspects} - the field Cobblemon documents for forcing an aspect, and a plain
 * property with no side effects (unlike {@code setForm}, which recomputes moves, ability and HP).
 * {@link #renderAspects} then folds them back in when building the {@code RenderablePokemon}.
 */
public final class StoredPokemonRenderFix {

    /** {@code DataKeys.POKEMON_FORM_ID} - mandatory in Cobblemon's Pokémon codec. */
    private static final String KEY_FORM_ID = "FormId";

    private StoredPokemonRenderFix() {}

    /**
     * Re-applies the form stored in {@code tag} to a Pokémon just loaded from that same tag. No-op
     * when the tag carries no form id or when the stored form has no aspects of its own (standard
     * forms), so a Pokémon that was already correct is left untouched.
     */
    public static Pokemon restoreForm(Pokemon pokemon, CompoundTag tag) {
        if (pokemon == null || tag == null || !tag.contains(KEY_FORM_ID, Tag.TAG_STRING)) {
            return pokemon;
        }
        String formId = tag.getString(KEY_FORM_ID);
        if (formId.isEmpty()) {
            return pokemon;
        }
        FormData form = pokemon.getSpecies().getFormByShowdownId(formId);
        List<String> formAspects = form.getAspects();
        if (formAspects.isEmpty()) {
            return pokemon;
        }
        Set<String> forced = new LinkedHashSet<>(pokemon.getForcedAspects());
        if (forced.addAll(formAspects)) {
            pokemon.setForcedAspects(forced);
        }
        return pokemon;
    }

    /**
     * Aspect set to render {@code pokemon} with: its own aspects plus any forced/form aspects. For a
     * live party Pokémon every source already agrees, so the result equals its current aspects.
     */
    public static Set<String> renderAspects(Pokemon pokemon) {
        Set<String> merged = new LinkedHashSet<>(pokemon.getAspects());
        merged.addAll(pokemon.getForcedAspects());
        merged.addAll(pokemon.getForm().getAspects());
        return merged;
    }
}
