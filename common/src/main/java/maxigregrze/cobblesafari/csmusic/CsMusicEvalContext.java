package maxigregrze.cobblesafari.csmusic;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Per-player, per-tick scratch space for condition evaluation. Axes that depend only on the player
 * (currently the biome holder) are resolved lazily here and reused across every rule of the sweep,
 * instead of being recomputed once per rule.
 *
 * <p>Strictly local to a single evaluation pass: never stored, so there is nothing to invalidate.</p>
 */
public final class CsMusicEvalContext {

    private final ServerPlayer player;
    private Holder<Biome> biome;

    public CsMusicEvalContext(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer player() {
        return player;
    }

    public Holder<Biome> biome() {
        if (biome == null) {
            biome = player.serverLevel().getBiome(player.blockPosition());
        }
        return biome;
    }
}
