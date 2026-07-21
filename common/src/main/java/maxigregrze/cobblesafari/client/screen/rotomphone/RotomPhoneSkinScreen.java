package maxigregrze.cobblesafari.client.screen.rotomphone;

import maxigregrze.cobblesafari.network.RotomPhoneActionPayload;
import maxigregrze.cobblesafari.network.RotomPhoneConfigSyncPayload;
import maxigregrze.cobblesafari.platform.Services;
import maxigregrze.cobblesafari.rotomphone.RotomPhoneClientCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RotomPhoneSkinScreen extends RotomPhoneBaseScreen {

    private static final ResourceLocation TEX_LOGO = loc("skin/rotomphone_gui_icon_skin.png");
    private static final ResourceLocation BTN_LEFT = loc("rotomphone_gui_buttonleft.png");
    private static final ResourceLocation BTN_RIGHT = loc("rotomphone_gui_buttonright.png");
    private static final ResourceLocation TEX_ON = loc("gts/rotomphone_gui_icon_valid.png");
    private static final ResourceLocation TEX_OFF = loc("gts/rotomphone_gui_icon_invalid.png");
    private static final int BTN_SIZE = 44;
    private static final int BTN_LEFT_X = 62;
    private static final int BTN_RIGHT_X = 242;
    private static final int BTN_Y = 70;
    private static final int LABEL_X = 174;
    private static final int LABEL_Y = 92;
    /** Horizontal space between the two arrows; x2 skin names wider than this fall back to x1. */
    private static final int LABEL_MAX_W = 132;

    // Custom-wallpaper toggle row: label x2 at x=58 (as the Settings rows), 32px toggle right-aligned at x=258.
    private static final int WALLPAPER_ROW_Y = 136;
    private static final int WALLPAPER_LABEL_X = 58;
    private static final int TOGGLE_X = 258;
    private static final int TOGGLE_SIZE = 32;

    private final List<SkinEntry> skinList = new ArrayList<>();
    private int currentIndex = 0;

    public RotomPhoneSkinScreen(String rotomName, boolean shinyStatus, String currentSkin, boolean safetyMode, boolean rotoGlide) {
        super(Component.translatable("gui.cobblesafari.rotomphone.app.skin"), rotomName, shinyStatus, currentSkin, safetyMode, rotoGlide);
    }

    @Override
    protected void init() {
        super.init();
        skinList.clear();
        skinList.add(new SkinEntry("", Component.translatable("gui.cobblesafari.rotomphone.skin.none"), false));

        for (RotomPhoneConfigSyncPayload.SkinData sd : RotomPhoneClientCache.getCachedSkins()) {
            if (sd.unlockedForPlayer()) {
                skinList.add(new SkinEntry(sd.id(), Component.literal(sd.displayName()), sd.hasCustomScreen()));
            }
        }

        currentIndex = 0;
        String skinKey = currentSkin == null ? "" : currentSkin;
        for (int i = 0; i < skinList.size(); i++) {
            if (skinList.get(i).id.equalsIgnoreCase(skinKey)) {
                currentIndex = i;
                break;
            }
        }
    }

    @Override
    protected void renderPhoneContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int theme = getTintColor();
        drawTinted(graphics, TEX_LOGO, originX + 58, originY + 16, 32, 32, 0xFFFFFFFF);
        drawScaledLeftAligned(graphics, Component.translatable("gui.cobblesafari.rotomphone.app.skin"),
                originX + 98, originY + 32, 0xFFFFFFFF);

        SkinEntry entry = skinList.get(currentIndex);
        if (this.font.width(entry.displayName) * 2 <= LABEL_MAX_W) {
            drawScaledCentered(graphics, entry.displayName, originX + LABEL_X, originY + LABEL_Y, theme);
        } else {
            graphics.drawCenteredString(this.font, entry.displayName,
                    originX + LABEL_X, originY + LABEL_Y - this.font.lineHeight / 2, theme);
        }

        int last = skinList.size() - 1;
        boolean canGoLeft = last > 0 && currentIndex > 0;
        boolean canGoRight = last > 0 && currentIndex < last;

        if (canGoLeft) {
            int lx = originX + BTN_LEFT_X;
            int ly = originY + BTN_Y;
            boolean hovered = isInBounds(mouseX, mouseY, lx, ly, BTN_SIZE, BTN_SIZE);
            drawTinted(graphics, BTN_LEFT, lx, ly, BTN_SIZE, BTN_SIZE, hovered ? 0xFFFFFFFF : theme);
        }

        if (canGoRight) {
            int rx = originX + BTN_RIGHT_X;
            int ry = originY + BTN_Y;
            boolean hovered = isInBounds(mouseX, mouseY, rx, ry, BTN_SIZE, BTN_SIZE);
            drawTinted(graphics, BTN_RIGHT, rx, ry, BTN_SIZE, BTN_SIZE, hovered ? 0xFFFFFFFF : theme);
        }

        if (entry.hasCustomScreen()) {
            drawScaledLeftAligned(graphics, Component.translatable("gui.cobblesafari.rotomphone.skin.custom_wallpaper"),
                    originX + WALLPAPER_LABEL_X, originY + WALLPAPER_ROW_Y + TOGGLE_SIZE / 2, theme);
            boolean enabled = RotomPhoneClientCache.isCurrentWallpaperEnabled();
            boolean hovered = isInBounds(mouseX, mouseY, originX + TOGGLE_X, originY + WALLPAPER_ROW_Y, TOGGLE_SIZE, TOGGLE_SIZE);
            drawTinted(graphics, enabled ? TEX_ON : TEX_OFF, originX + TOGGLE_X, originY + WALLPAPER_ROW_Y,
                    TOGGLE_SIZE, TOGGLE_SIZE, hovered ? 0xFFFFFFFF : theme);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int last = skinList.size() - 1;
            boolean canGoLeft = last > 0 && currentIndex > 0;
            boolean canGoRight = last > 0 && currentIndex < last;

            if (canGoLeft && isInBounds(mouseX, mouseY, originX + BTN_LEFT_X, originY + BTN_Y, BTN_SIZE, BTN_SIZE)) {
                currentIndex--;
                applySkin();
                return true;
            }

            if (canGoRight && isInBounds(mouseX, mouseY, originX + BTN_RIGHT_X, originY + BTN_Y, BTN_SIZE, BTN_SIZE)) {
                currentIndex++;
                applySkin();
                return true;
            }

            if (skinList.get(currentIndex).hasCustomScreen()
                    && isInBounds(mouseX, mouseY, originX + TOGGLE_X, originY + WALLPAPER_ROW_Y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                RotomPhoneClientCache.setCurrentWallpaperEnabled(!RotomPhoneClientCache.isCurrentWallpaperEnabled());
                Services.PLATFORM.sendPayloadToServer(
                        new RotomPhoneActionPayload(RotomPhoneActionPayload.ACTION_TOGGLE_WALLPAPER, ""));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void applySkin() {
        String skinId = skinList.get(currentIndex).id;
        setCurrentSkin(skinId);
        Services.PLATFORM.sendPayloadToServer(
                new RotomPhoneActionPayload(RotomPhoneActionPayload.ACTION_CHANGE_SKIN, skinId));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onBackButtonClicked();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record SkinEntry(String id, Component displayName, boolean hasCustomScreen) {}
}
