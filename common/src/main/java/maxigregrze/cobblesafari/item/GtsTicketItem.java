package maxigregrze.cobblesafari.item;

import maxigregrze.cobblesafari.config.GtsSettings;
import maxigregrze.cobblesafari.gts.GtsService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GtsTicketItem extends Item {

    public GtsTicketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return InteractionResultHolder.fail(stack);
        }

        GtsService.UseTicketResult result = GtsService.tryUseGtsTicket(server, player.getUUID());
        if (result == GtsService.UseTicketResult.AT_MAX) {
            player.sendSystemMessage(Component.translatable(
                    "cobblesafari.item.ticket_gts.at_max", GtsSettings.get().getMaxOffersWithUpgrades()));
            return InteractionResultHolder.fail(stack);
        }

        int allowed = GtsService.getAllowedOfferCount(server, player.getUUID());
        player.sendSystemMessage(Component.translatable("cobblesafari.item.ticket_gts.used", allowed));
        stack.consume(1, player);

        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
