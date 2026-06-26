package info.rusty.webshoplink.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.ShopActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Full-screen in-game browser for a shop session. Renders an MCEF (Chromium)
 * browser into the area above a small Minecraft button bar offering
 * <em>Finish Trade</em> and <em>Cancel</em>. Closing without choosing (ESC)
 * cancels the session.
 *
 * <p>Client-only: this class references MCEF and is reached exclusively through
 * {@link ClientShopBrowser}, which is only invoked on {@code Dist.CLIENT}.
 */
public class ShopBrowserScreen extends Screen {

    private static final int BAR_HEIGHT = 28;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private final UUID processId;
    private final String url;

    private MCEFBrowser browser;
    /** Guards against sending more than one action (e.g. Finish then a stray Cancel on close). */
    private boolean actionSent = false;

    public ShopBrowserScreen(UUID processId, String url) {
        super(Component.literal("Shop"));
        this.processId = processId;
        this.url = url;
    }

    @Override
    protected void init() {
        super.init();
        if (browser == null) {
            // transparent=true lets the page's transparent CSS background show the
            // (dimmed/blurred) game world behind the floating shop panel.
            browser = MCEF.createBrowser(url, true);
        }

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
        if (browser != null) {
            double guiScale = minecraft.getWindow().getGuiScale();
            browser.resize((int) (getBrowserWidth() * guiScale), (int) (getBrowserHeight() * guiScale));
        }
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int width, int height) {
        super.resize(mc, width, height);
        resizeBrowser();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        if (browser != null && browser.isTextureReady()) {
            ResourceLocation texture = browser.getTextureLocation();
            if (texture != null) {
                int w = getBrowserWidth();
                int h = getBrowserHeight();
                guiGraphics.blit(texture, getBrowserX(), getBrowserY(), 0.0F, 0.0F, w, h, w, h);
            }
        } else {
            guiGraphics.drawCenteredString(this.font, Component.literal("Loading shop…"),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // --- input routing to the browser -------------------------------------------------

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
        if (browser == null) {
            return false;
        }
        browser.sendMouseRelease(browserX(mouseX), browserY(mouseY), button);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && inBrowser(mouseX, mouseY)) {
            browser.sendMouseMove(browserX(mouseX), browserY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (super.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
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
            Networking.CHANNEL.sendToServer(new ShopActionPacket(processId, action));
        }
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        // ESC / close without pressing a button cancels the session.
        if (!actionSent) {
            actionSent = true;
            Networking.CHANNEL.sendToServer(new ShopActionPacket(processId, ShopActionPacket.Action.CANCEL));
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
