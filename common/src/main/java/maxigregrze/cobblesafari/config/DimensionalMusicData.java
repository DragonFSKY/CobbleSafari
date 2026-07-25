package maxigregrze.cobblesafari.config;

/**
 * DTO for {@code dimensional_music.json} - global csmusic settings.
 * {@code enabled}: global csmusic toggle. {@code defaultAreaPriority}: fallback priority for a
 * music area created without an explicit value. Where music plays is defined entirely by
 * {@code data/<ns>/csmusic/definition/*.json} trigger files (dimension, biome, battle, …).
 */
public class DimensionalMusicData {

    public boolean enabled = true;
    /** Default priority assigned to a music area when created without an explicit value. */
    public int defaultAreaPriority = 1;
    /**
     * How long (ticks) a chunk's resolved structure list stays valid. Only relevant for rules
     * using {@code when.structure}. Lower = picks up newly generated structures sooner;
     * higher = fewer chunk lookups. Clamped to a 20-tick minimum.
     */
    public int structureCacheTtlTicks = 600;
    /**
     * How often (in ticks) the music arbitration sweep runs. Transitions fade over ~1 s, so a value
     * up to ~5 is inaudible. Boss transitions are always immediate, whatever this is. Set to 1 to
     * restore the pre-160 behaviour exactly.
     */
    public int arbitrationIntervalTicks = 5;
}
