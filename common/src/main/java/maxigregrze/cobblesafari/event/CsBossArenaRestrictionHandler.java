package maxigregrze.cobblesafari.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import kotlin.Unit;
import maxigregrze.cobblesafari.csboss.BossBattleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Arena-scoped Cobblemon restrictions while a CSBoss fight is running: any player standing
 * inside the arena bounds of an active session cannot send a Pokémon out of its ball.
 * No-op while no fight is running.
 */
public final class CsBossArenaRestrictionHandler {

    private CsBossArenaRestrictionHandler() {}

    public static void registerCobblemonEvents() {
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGHEST, event -> {
            UUID ownerId = event.getPokemon().getOwnerUUID();
            if (ownerId != null && event.getLevel() != null
                    && event.getLevel().getPlayerByUUID(ownerId) instanceof ServerPlayer owner
                    && BossBattleManager.isInsideActiveArena(owner.level().dimension(), owner.position())) {
                owner.sendSystemMessage(Component.translatable("cobblesafari.csboss.no_sendout"));
                event.cancel();
            }
            return Unit.INSTANCE;
        });
    }
}
