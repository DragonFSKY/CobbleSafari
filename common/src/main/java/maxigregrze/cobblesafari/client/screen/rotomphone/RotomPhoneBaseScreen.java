package maxigregrze.cobblesafari.client.screen.rotomphone;

import com.mojang.blaze3d.systems.RenderSystem;

import maxigregrze.cobblesafari.CobbleSafari;
import maxigregrze.cobblesafari.network.RotomPhoneConfigSyncPayload;
import maxigregrze.cobblesafari.rotomphone.RotomPhoneClientCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class RotomPhoneBaseScreen extends Screen {

    private static final String TEX_PATH = "textures/gui/rotomphone/";

    public static final int MAIN_W = 348;
    public static final int MAIN_H = 184;
    public static final int BOTTOM_H = 64;
    public static final int TOP_H = 132;
    public static final int SKIN_H = 380;

    protected static final int BACK_BTN_W = 12;
    protected static final int BACK_BTN_H = 62;
    protected static final int BACK_BTN_X = 321;
    protected static final int BACK_BTN_Y = 61;

    public static final int ONLINE_PC_TINT = 0xFF3deeee;

    protected final String rotomName;
    protected boolean shinyStatus;
    protected String currentSkin;
    protected boolean safetyMode;
    protected boolean rotoGlide;
    protected final RotomPhoneShell shell;

    protected int originX;
    protected int originY;

    protected RotomPhoneBaseScreen(Component title, String rotomName, boolean shinyStatus,
                                    String currentSkin, boolean safetyMode, boolean rotoGlide) {
        this(title, rotomName, shinyStatus, currentSkin, safetyMode, rotoGlide, RotomPhoneShell.PHONE);
    }

    protected RotomPhoneBaseScreen(Component title, String rotomName, boolean shinyStatus,
                                    String currentSkin, boolean safetyMode, boolean rotoGlide,
                                    RotomPhoneShell shell) {
        super(title);
        this.rotomName = rotomName;
        this.shinyStatus = shinyStatus;
        this.currentSkin = currentSkin;
        this.safetyMode = safetyMode;
        this.rotoGlide = rotoGlide;
        this.shell = shell;
    }

    public void setCurrentSkin(String skin) {
        this.currentSkin = skin;
    }

    public void setSafetyMode(boolean mode) {
        this.safetyMode = mode;
    }

    public void setRotoGlide(boolean enabled) {
        this.rotoGlide = enabled;
    }

    @Override
    protected void init() {
        super.init();
        this.originX = (this.width - MAIN_W) / 2;
        this.originY = (this.height - MAIN_H) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally blank: the phone frame/background is drawn in render().
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (shell == RotomPhoneShell.ONLINE_PC) {
            OnlinePcBackdropRenderer.render(graphics, width, height);
        } else {
            RotomPhoneBackdropRenderer.renderFullPhone(graphics, width, height, shinyStatus, currentSkin);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        renderPhoneContent(graphics, mouseX, mouseY, partialTick);
        if (showBackButton()) {
            renderBackButton(graphics, mouseX, mouseY);
        }
    }

    private void renderBackButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!showBackButton()) return;

        int bx = originX + BACK_BTN_X;
        int by = originY + BACK_BTN_Y;
        boolean hovered = isInBounds(mouseX, mouseY, bx, by, BACK_BTN_W, BACK_BTN_H);

        ResourceLocation tex = loc("rotomphone_gui_backbutton.png");

        int tint = hovered ? 0xFFFFFFFF : getTintColor();
        float r = ((tint >> 16) & 0xFF) / 255f;
        float g = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(r, g, b, 1.0f);
        graphics.blit(tex, bx, by, 0, 0, BACK_BTN_W, BACK_BTN_H, BACK_BTN_W, BACK_BTN_H);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    protected boolean showBackButton() {
        return true;
    }

    protected int getTintColor() {
        if (shell == RotomPhoneShell.ONLINE_PC) {
            return ONLINE_PC_TINT;
        }
        RotomPhoneConfigSyncPayload.SkinData skinData = getCurrentSkinData();
        if (skinData != null && skinData.hasCustomScreen() && RotomPhoneClientCache.isCurrentWallpaperEnabled()) {
            try {
                return 0xFF000000 | Integer.parseInt(skinData.color(), 16);
            } catch (NumberFormatException ignored) {
                // Malformed colour string; fall back to the default below.
            }
        }
        return shinyStatus ? 0xFFffffc0 : 0xFF7affff;
    }

    protected RotomPhoneConfigSyncPayload.SkinData getCurrentSkinData() {
        if (currentSkin == null || currentSkin.isEmpty()) return null;
        for (RotomPhoneConfigSyncPayload.SkinData s : RotomPhoneClientCache.getCachedSkins()) {
            if (s.id().equalsIgnoreCase(currentSkin)) return s;
        }
        return null;
    }

    protected abstract void renderPhoneContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && showBackButton()) {
            int bx = originX + BACK_BTN_X;
            int by = originY + BACK_BTN_Y;
            if (isInBounds((int) mouseX, (int) mouseY, bx, by, BACK_BTN_W, BACK_BTN_H)) {
                onBackButtonClicked();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void onBackButtonClicked() {
        if (this.minecraft == null) {
            return;
        }
        if (shell == RotomPhoneShell.ONLINE_PC) {
            this.minecraft.setScreen(null);
            return;
        }
        this.minecraft.setScreen(new RotomPhoneMenuScreen(rotomName, shinyStatus, currentSkin, safetyMode, rotoGlide));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && shell == RotomPhoneShell.ONLINE_PC && !showBackButton()) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected static ResourceLocation loc(String filename) {
        return ResourceLocation.fromNamespaceAndPath(CobbleSafari.MOD_ID, TEX_PATH + filename);
    }

    protected static boolean isInBounds(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected static boolean isInBounds(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---------------------------------------------------------------- shared draw helpers

    /** Added to anchor Y before scaling; negative moves the drawn text upward by 7 screen pixels. */
    protected static final float SCALED_TEXT_Y_OFFSET = -7f;

    /** Corner radius used by rounded rectangles across the phone apps. */
    protected static final int CORNER_R = 5;

    /**
     * Per-row horizontal inset for a rounded corner, indexed by distance (in pixels) from the
     * corner edge (0 = the outermost row/column of the corner). Mirrored on every axis to round
     * each of the four corners. Tuned to give a soft, fully-opaque rounded look rather than a
     * straight diagonal bevel. Length matches {@link #CORNER_R}.
     */
    private static final int[] CORNER_INSET = {4, 2, 1, 1, 0};

    private static int cornerInset(int dist) {
        return dist >= 0 && dist < CORNER_INSET.length ? CORNER_INSET[dist] : 0;
    }

    /** Filled rectangle with rounded corners selected by the four flags. */
    protected static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int argb,
                                          boolean tl, boolean tr, boolean bl, boolean br) {
        for (int row = 0; row < h; row++) {
            int leftInset = 0;
            int rightInset = 0;
            int topDist = row;
            int botDist = h - 1 - row;
            if (tl && topDist < r) {
                leftInset = Math.max(leftInset, cornerInset(topDist));
            }
            if (bl && botDist < r) {
                leftInset = Math.max(leftInset, cornerInset(botDist));
            }
            if (tr && topDist < r) {
                rightInset = Math.max(rightInset, cornerInset(topDist));
            }
            if (br && botDist < r) {
                rightInset = Math.max(rightInset, cornerInset(botDist));
            }
            g.fill(x + leftInset, y + row, x + w - rightInset, y + row + 1, argb);
        }
    }

    /**
     * Rounded rectangle drawn as a 1px border plus interior fill, each pixel painted exactly once,
     * so translucent colours (alpha below 0xFF) render without the border and fill double-blending.
     */
    protected static void fillRoundedRectWithBorder(GuiGraphics g, int x, int y, int w, int h, int r,
                                                    int borderArgb, int fillArgb) {
        for (int row = 0; row < h; row++) {
            int outer = roundedRowInset(row, h, r);
            int oL = x + outer;
            int oR = x + w - outer;
            if (row == 0 || row == h - 1) {
                g.fill(oL, y + row, oR, y + row + 1, borderArgb);
                continue;
            }
            int inner = roundedRowInset(row - 1, h - 2, r);
            int iL = x + 1 + inner;
            int iR = x + w - 1 - inner;
            if (iL > oL) {
                g.fill(oL, y + row, iL, y + row + 1, borderArgb);
            }
            if (oR > iR) {
                g.fill(iR, y + row, oR, y + row + 1, borderArgb);
            }
            if (iR > iL) {
                g.fill(iL, y + row, iR, y + row + 1, fillArgb);
            }
        }
    }

    private static int roundedRowInset(int row, int h, int r) {
        int inset = 0;
        if (row < r) {
            inset = cornerInset(row);
        }
        int botDist = h - 1 - row;
        if (botDist < r) {
            inset = Math.max(inset, cornerInset(botDist));
        }
        return inset;
    }

    /** Blits a white-on-transparent texture tinted with the given ARGB colour (alpha included). */
    protected static void drawTinted(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int argb) {
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;
        float alpha = ((argb >>> 24) & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(red, green, blue, alpha);
        g.blit(tex, x, y, 0, 0, w, h, w, h);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Renders text at 2x scale, left-aligned so its left edge starts at x. */
    protected void drawScaledLeftAligned(GuiGraphics g, Component c, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y + SCALED_TEXT_Y_OFFSET, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(this.font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Renders text at 2x scale, centered on x. */
    protected void drawScaledCentered(GuiGraphics g, Component c, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y + SCALED_TEXT_Y_OFFSET, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(this.font, c, 0, 0, color);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
