package maxigregrze.cobblesafari.client.screen.rotomphone;

import maxigregrze.cobblesafari.network.RotomPhoneActionPayload;
import maxigregrze.cobblesafari.platform.Services;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class RotomPhoneSettingsScreen extends RotomPhoneBaseScreen {

    private static final ResourceLocation TEX_LOGO = loc("settings/rotomphone_gui_icon_settings.png");
    private static final ResourceLocation TEX_ON = loc("gts/rotomphone_gui_icon_valid.png");
    private static final ResourceLocation TEX_OFF = loc("gts/rotomphone_gui_icon_invalid.png");

    private static final int LABEL_X = 58;
    private static final int TOGGLE_X = 218;
    private static final int TOGGLE_SIZE = 32;
    private static final int SAFETY_ROW_Y = 64;
    private static final int ROTO_ROW_Y = 112;

    public RotomPhoneSettingsScreen(String rotomName, boolean shinyStatus, String currentSkin, boolean safetyMode, boolean rotoGlide) {
        super(Component.translatable("gui.cobblesafari.rotomphone.app.settings"), rotomName, shinyStatus, currentSkin, safetyMode, rotoGlide);
    }

    @Override
    protected void renderPhoneContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int theme = getTintColor();
        drawTinted(graphics, TEX_LOGO, originX + 58, originY + 16, 32, 32, 0xFFFFFFFF);
        drawScaledLeftAligned(graphics, Component.translatable("gui.cobblesafari.rotomphone.app.settings"),
                originX + 98, originY + 32, 0xFFFFFFFF);

        renderSettingRow(graphics, mouseX, mouseY, theme,
                Component.translatable("gui.cobblesafari.rotomphone.settings.safety_toggle"),
                SAFETY_ROW_Y, safetyMode);
        renderSettingRow(graphics, mouseX, mouseY, theme,
                Component.translatable("gui.cobblesafari.rotomphone.settings.roto_glide_toggle"),
                ROTO_ROW_Y, rotoGlide);
    }

    private void renderSettingRow(GuiGraphics g, int mx, int my, int theme, Component label, int rowY, boolean enabled) {
        // x2 label, vertically centered on the 32px toggle icon.
        drawScaledLeftAligned(g, label, originX + LABEL_X, originY + rowY + TOGGLE_SIZE / 2, theme);
        boolean hovered = isInBounds(mx, my, originX + TOGGLE_X, originY + rowY, TOGGLE_SIZE, TOGGLE_SIZE);
        drawTinted(g, enabled ? TEX_ON : TEX_OFF, originX + TOGGLE_X, originY + rowY, TOGGLE_SIZE, TOGGLE_SIZE,
                hovered ? 0xFFFFFFFF : theme);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInBounds(mouseX, mouseY, originX + TOGGLE_X, originY + SAFETY_ROW_Y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                setSafetyMode(!safetyMode);
                Services.PLATFORM.sendPayloadToServer(
                        new RotomPhoneActionPayload(RotomPhoneActionPayload.ACTION_TOGGLE_SAFETY, ""));
                return true;
            }
            if (isInBounds(mouseX, mouseY, originX + TOGGLE_X, originY + ROTO_ROW_Y, TOGGLE_SIZE, TOGGLE_SIZE)) {
                setRotoGlide(!rotoGlide);
                Services.PLATFORM.sendPayloadToServer(
                        new RotomPhoneActionPayload(RotomPhoneActionPayload.ACTION_TOGGLE_ROTO_GLIDE, ""));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onBackButtonClicked();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
