package info.rusty.webshoplink.client;

import de.keksuccino.rinku.RinkuBrowser;
import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.ShopActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Full-screen in-game browser for a shop session. Renders the shared Rinku
 * (Chromium) browser owned by {@link ShopBrowserHost} into the area above a small
 * Minecraft button bar offering <em>Finish Trade</em> and <em>Cancel</em>. Closing
 * without choosing (ESC) cancels the session.
 *
 * <p>The browser is deliberately <em>not</em> created here: it outlives the screen
 * so that opening a shop never has to wait for (and flash through) a fresh
 * Chromium browser's first frame. See {@link ShopBrowserHost}.
 *
 * <p>Client-only: this class references Rinku and is reached exclusively through
 * {@link ClientShopBrowser}, which is only invoked on {@code Dist.CLIENT}.
 */
public class ShopBrowserScreen extends Screen {

    static final int BAR_HEIGHT = 28;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    /**
     * How long the screen stays silent before admitting the page is slow. Below
     * this the world is simply left visible, so a fast shop never flashes a
     * loading message on its way in.
     */
    private static final long LOADING_TEXT_DELAY_MILLIS = 400L;

    private final UUID processId;
    private final long openedAt = System.currentTimeMillis();
    /** The session this screen was opened for; see {@link ShopBrowserHost#endSession(long)}. */
    private final long sessionToken = ShopBrowserHost.currentSessionToken();

    /** Guards against sending more than one action (e.g. Finish then a stray Cancel on close). */
    private boolean actionSent = false;

    public ShopBrowserScreen(UUID processId) {
        super(Component.literal("Shop"));
        this.processId = processId;
    }

    /** The page is on screen (as opposed to still loading behind the live world). */
    public boolean isShowingPage() {
        RinkuBrowser browser = ShopBrowserHost.getBrowser();
        return browser != null && ShopBrowserHost.isPageReady() && browser.isTextureReady();
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(Component.literal("Finish Trade"), b -> sendAndClose(ShopActionPacket.Action.FINISH))
                .bounds(this.width / 2 - BUTTON_WIDTH - 4, this.height - BUTTON_HEIGHT - 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> sendAndClose(ShopActionPacket.Action.CANCEL))
                .bounds(this.width / 2 + 4, this.height - BUTTON_HEIGHT - 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        resizeBrowser();
    }

    private int getBrowserX() {
        return 0;
    }

    private int getBrowserY() {
        return 0;
    }

    private int getBrowserWidth() {
        return this.width;
    }

    private int getBrowserHeight() {
        return Math.max(1, this.height - BAR_HEIGHT);
    }

    private void resizeBrowser() {
        ShopBrowserHost.resizeToShopViewport();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int width, int height) {
        super.resize(mc, width, height);
        resizeBrowser();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here on purpose: the browser is transparent (see
        // ShopBrowserHost), and the world keeps rendering behind a non-pause screen, so we
        // blit the page straight over the live world. Painting the usual dimming
        // gradient first would show through every transparent pixel of the page and
        // defeat the transparency.
        RinkuBrowser browser = ShopBrowserHost.getBrowser();
        ResourceLocation texture = isShowingPage() ? browser.getTextureIdentifier() : null;
        if (texture != null) {
            int w = getBrowserWidth();
            int h = getBrowserHeight();
            guiGraphics.blit(texture, getBrowserX(), getBrowserY(), 0.0F, 0.0F, w, h, w, h);
        } else if (System.currentTimeMillis() - openedAt >= LOADING_TEXT_DELAY_MILLIS) {
            // Until the page has painted the world stays visible; only say something
            // once the wait is long enough that silence would look like a bug.
            guiGraphics.drawCenteredString(this.font, Component.literal("Loading shop…"),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // --- input routing to the browser -------------------------------------------------

    /** The shared browser, or {@code null} if Rinku never came up. */
    private RinkuBrowser browser() {
        return ShopBrowserHost.getBrowser();
    }

    private int browserX(double x) {
        return (int) ((x - getBrowserX()) * minecraft.getWindow().getGuiScale());
    }

    private int browserY(double y) {
        return (int) ((y - getBrowserY()) * minecraft.getWindow().getGuiScale());
    }

    private boolean inBrowser(double x, double y) {
        return x >= getBrowserX() && x < getBrowserX() + getBrowserWidth()
                && y >= getBrowserY() && y < getBrowserY() + getBrowserHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        RinkuBrowser browser = browser();
        if (browser == null || !inBrowser(mouseX, mouseY)) {
            return false;
        }
        browser.sendMousePress(browserX(mouseX), browserY(mouseY), button);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        RinkuBrowser browser = browser();
        if (browser == null) {
            return false;
        }
        browser.sendMouseRelease(browserX(mouseX), browserY(mouseY), button);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        RinkuBrowser browser = browser();
        if (browser != null && inBrowser(mouseX, mouseY)) {
            browser.sendMouseMove(browserX(mouseX), browserY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, delta)) {
            return true;
        }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
    *///?}
        RinkuBrowser browser = browser();
        if (browser == null || !inBrowser(mouseX, mouseY)) {
            return false;
        }
        browser.sendMouseWheel(browserX(mouseX), browserY(mouseY), delta, 0);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Lets super handle ESC (which calls onClose) and any focused widgets first.
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        RinkuBrowser browser = browser();
        if (browser == null) {
            return false;
        }
        browser.sendKeyPress(keyCode, scanCode, modifiers);
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (super.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        RinkuBrowser browser = browser();
        if (browser == null) {
            return false;
        }
        browser.sendKeyRelease(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }
        RinkuBrowser browser = browser();
        if (browser == null || codePoint == 0) {
            return false;
        }
        browser.sendKeyTyped(codePoint, modifiers);
        browser.setFocus(true);
        return true;
    }

    // --- lifecycle --------------------------------------------------------------------

    private void sendAndClose(ShopActionPacket.Action action) {
        if (!actionSent) {
            actionSent = true;
            Networking.sendToServer(new ShopActionPacket(processId, action));
        }
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        // ESC / close without pressing a button cancels the session.
        if (!actionSent) {
            actionSent = true;
            Networking.sendToServer(new ShopActionPacket(processId, ShopActionPacket.Action.CANCEL));
        }
        super.onClose();
    }

    @Override
    public void removed() {
        // The browser outlives the screen — park it on the blank page so the shop
        // page stops running and the next session starts from a transparent frame.
        ShopBrowserHost.endSession(sessionToken);
        // A finished trade changes the balance; refresh the overlay once the
        // backend has had a moment to process the checkout.
        BalanceOverlay.scheduleReload(2000);
        super.removed();
    }

    // Intentionally empty: the transparent browser fills the screen over the live
    // world, so we never want the default screen dimming (or, from 1.20.5, the menu
    // blur, which super.render() would otherwise apply itself). See render().
    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics guiGraphics) {
    }
    *///?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
