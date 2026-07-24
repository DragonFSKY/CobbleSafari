package maxigregrze.cobblesafari.rotomphone;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import maxigregrze.cobblesafari.CobbleSafari;
import maxigregrze.cobblesafari.config.RotomPhoneConfig;
import maxigregrze.cobblesafari.init.ModBlocks;
import maxigregrze.cobblesafari.init.ModItems;
import maxigregrze.cobblesafari.item.RotomPhoneItem;
import maxigregrze.cobblesafari.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmptyPhoneServerHandler {

    private EmptyPhoneServerHandler() {}

    /** Which filled device a pending confirmation should produce (and which empty item it consumes). */
    public enum FillTarget { PHONE, EARPIECE }

    /** How long a confirmation may stay open before the server drops it and closes the client screen. */
    private static final long PENDING_TTL_MS = 60_000L;

    private static final Map<UUID, PendingFill> PENDING_FILLS = new ConcurrentHashMap<>();

    public static void attemptFill(ServerPlayer player, boolean isFromBlock, BlockPos blockPos,
                                   int inventorySlot, FillTarget target) {
        if (isFromBlock && blockPos != null && isBlockClaimedByOther(player, blockPos)) {
            player.displayClientMessage(Component.translatable("cobblesafari.rotomphone.block_busy"), true);
            return;
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon firstRotom = findFirstRotom(party);
        if (firstRotom == null) {
            player.displayClientMessage(Component.translatable("cobblesafari.rotomphone.no_rotom"), true);
            return;
        }

        // Refuse up front rather than letting the player confirm a doomed absorption.
        if (!RotomPhoneConfig.isAllowsShinyPhone() && firstRotom.getShiny()) {
            player.displayClientMessage(Component.translatable("cobblesafari.rotomphone.shiny_not_allowed"), true);
            CobbleSafari.LOGGER.info("{} tried to use a shiny rotom in a rotom phone, but it is not allowed",
                    player.getName().getString());
            return;
        }

        PENDING_FILLS.put(player.getUUID(), new PendingFill(firstRotom, isFromBlock, blockPos,
                player.level().dimension(), inventorySlot, target,
                System.currentTimeMillis() + PENDING_TTL_MS));

        maxigregrze.cobblesafari.platform.Services.PLATFORM.sendPayloadToPlayer(player,
                new maxigregrze.cobblesafari.network.OpenEmptyPhoneConfirmPayload(
                        firstRotom.getSpecies().getName(),
                        firstRotom.getLevel(),
                        firstRotom.getShiny(),
                        firstRotom.getSpecies().getResourceIdentifier().toString(),
                        List.copyOf(firstRotom.getAspects())));
    }

    public static void handleConfirm(ServerPlayer player, boolean confirmed) {
        PendingFill pending = PENDING_FILLS.remove(player.getUUID());
        if (pending == null || !confirmed) {
            return;
        }
        if (System.currentTimeMillis() >= pending.expiresAtMs()) {
            player.displayClientMessage(Component.translatable("cobblesafari.rotomphone.fill_expired"), true);
            return;
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon rotom = pending.rotom();
        if (rotom == null || !party.toGappyList().contains(rotom)) {
            return;
        }

        // Kept as a server-side backstop: the config may have been reloaded while the screen was open,
        // and a modified client must not be able to bypass the rule by sending the confirm directly.
        if (!RotomPhoneConfig.isAllowsShinyPhone() && rotom.getShiny()) {
            player.displayClientMessage(Component.translatable(
                    "cobblesafari.rotomphone.shiny_not_allowed"), false);
            CobbleSafari.LOGGER.info("{} tried to use a shiny rotom in a rotom phone, but it is not allowed",
                    player.getName().getString());
            return;
        }

        boolean earpiece = pending.target() == FillTarget.EARPIECE;

        // 1. Consume the container FIRST. If it is gone, abort without touching the Pokemon: this is
        //    what keeps "one container consumed" and "one device produced" from ever diverging.
        if (!consumeContainer(player, pending, earpiece)) {
            player.displayClientMessage(Component.translatable("cobblesafari.rotomphone.fill_failed"), true);
            return;
        }

        // 2. Give back everything the Rotom was carrying, before it leaves the party.
        dropCarriedItems(player, rotom);

        // 3. Consume the Rotom and produce the device.
        boolean shiny = rotom.getShiny();
        party.remove(rotom);

        maxigregrze.cobblesafari.advancement.ModCriteria.ROTOM_PHONE_MADE.trigger(player);
        if (shiny) {
            maxigregrze.cobblesafari.advancement.ModCriteria.ROTOM_PHONE_SHINY.trigger(player);
        }

        ItemStack resultStack = new ItemStack(earpiece ? ModItems.ROTOM_EARPIECE : ModItems.ROTOM_PHONE);
        RotomPhoneItem.setRotomName(resultStack, rotom.getSpecies().getName());
        RotomPhoneItem.setShiny(resultStack, shiny);
        RotomPhoneItem.setCurrentSkin(resultStack, "");
        RotomPhoneItem.setSafetyMode(resultStack, false);

        if (pending.isFromBlock()) {
            player.level().addFreshEntity(new ItemEntity(player.level(),
                    player.getX(), player.getY() + 0.5, player.getZ(), resultStack));
        } else if (!player.getInventory().add(resultStack)) {
            player.drop(resultStack, false);
        }

        player.displayClientMessage(Component.translatable("cobblesafari.rotomphone.filled"), false);
    }

    /**
     * Consumes the empty phone this confirmation was opened on. Returns {@code false} — without any
     * side effect — when it is no longer there: block broken or already absorbed by someone else,
     * item moved out of its slot, or player now in another dimension.
     */
    private static boolean consumeContainer(ServerPlayer player, PendingFill pending, boolean earpiece) {
        if (pending.isFromBlock()) {
            ServerLevel level = player.serverLevel();
            BlockPos pos = pending.blockPos();
            if (pos == null
                    || !level.dimension().equals(pending.dimension())
                    || !level.getBlockState(pos).is(ModBlocks.EMPTYPHONE)) {
                return false;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        }

        Item expectedEmpty = earpiece ? ModItems.EMPTY_EARPIECE : ModBlocks.EMPTYPHONE.asItem();
        ItemStack inSlot = player.getInventory().getItem(pending.inventorySlot());
        if (!inSlot.is(expectedEmpty)) {
            return false;
        }
        inSlot.shrink(1);
        return true;
    }

    /** Hands back everything the Rotom carried, dropped in front of the player rather than deleted. */
    private static void dropCarriedItems(ServerPlayer player, Pokemon rotom) {
        ItemStack held = rotom.removeHeldItem();
        if (!held.isEmpty()) {
            player.drop(held, false);
        }
        ItemStack cosmetic = rotom.removeCosmeticItem();
        if (!cosmetic.isEmpty()) {
            player.drop(cosmetic, false);
        }
    }

    /**
     * True if another player currently holds an open confirmation on this exact block. The pending
     * table is the single source of truth for the claim, so every way out of the confirmation
     * (confirm, cancel, escape, expiry, disconnect) releases the block for free.
     *
     * <p>The same player is deliberately allowed through: if their screen was lost client-side they
     * must be able to reopen, which simply replaces their own pending entry.
     */
    private static boolean isBlockClaimedByOther(ServerPlayer player, BlockPos pos) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PendingFill> entry : PENDING_FILLS.entrySet()) {
            PendingFill p = entry.getValue();
            if (p.isFromBlock()
                    && pos.equals(p.blockPos())
                    && player.level().dimension().equals(p.dimension())
                    && now < p.expiresAtMs()
                    && !entry.getKey().equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** Drops stale confirmations and closes their client screen. Called once per server tick. */
    public static void tickExpirations(MinecraftServer server) {
        if (PENDING_FILLS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        PENDING_FILLS.entrySet().removeIf(entry -> {
            if (now < entry.getValue().expiresAtMs()) {
                return false;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner != null) {
                ModNetworking.sendCloseEmptyPhoneConfirm(owner);
                owner.displayClientMessage(
                        Component.translatable("cobblesafari.rotomphone.fill_expired"), true);
            }
            return true;
        });
    }

    /** Called on disconnect: never keep a Pokemon reference (or a block claim) for an absent player. */
    public static void clear(UUID playerUuid) {
        PENDING_FILLS.remove(playerUuid);
    }

    private static Pokemon findFirstRotom(PlayerPartyStore party) {
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && "rotom".equalsIgnoreCase(pokemon.getSpecies().getName())) {
                return pokemon;
            }
        }
        return null;
    }

    private record PendingFill(Pokemon rotom, boolean isFromBlock, BlockPos blockPos,
                               ResourceKey<Level> dimension, int inventorySlot, FillTarget target,
                               long expiresAtMs) {}
}
