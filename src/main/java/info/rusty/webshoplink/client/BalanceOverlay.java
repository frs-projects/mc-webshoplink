package info.rusty.webshoplink.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import info.rusty.webshoplink.ClientConfig;
import info.rusty.webshoplink.Webshoplink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Small always-visible HUD element rendering the balance web page (the same
 * MCEF browser used for the shop, just tiny and non-interactive) anchored to a
 * configurable screen corner. Disabled by default; see {@link ClientConfig}.
 *
 * <p>The browser is created lazily on first render and kept alive across
 * screens so the page isn't re-fetched every frame. It is closed on logout or
 * when the overlay is disabled via config.
 */
@Mod.EventBusSubscriber(modid = Webshoplink.MODID, value = Dist.CLIENT)
public class BalanceOverlay implements IGuiOverlay {

    public static final BalanceOverlay INSTANCE = new BalanceOverlay();

    /** How long to wait before retrying the real page after showing the fallback. */
    private static final long RETRY_INTERVAL_MILLIS = 30_000L;

    private MCEFBrowser browser;
    /** The balance page we want to show (never the fallback data: URL). */
    private String currentUrl;
    private int lastPixelWidth = -1;
    private int lastPixelHeight = -1;
    /** Epoch millis at which the page should be reloaded; 0 = nothing pending. */
    private long reloadAt = 0L;

    /** Set from the CEF thread when the balance page failed to load. */
    private volatile boolean loadFailed = false;
    /** True while the bundled "Failed to load balance" page is displayed. */
    private boolean showingFallback = false;
    private long retryAt = 0L;
    private boolean loadHandlerRegistered = false;

    private BalanceOverlay() {
    }

    /**
     * Reload the balance page after {@code delayMillis}, giving the shop backend
     * a moment to settle. Called when a shop session's browser closes so a
     * purchase is reflected without waiting for the page's own refresh cycle.
     */
    public static void scheduleReload(long delayMillis) {
        INSTANCE.reloadAt = System.currentTimeMillis() + delayMillis;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ClientConfig.balanceDisplayEnabled) {
            close();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !MCEF.isInitialized()) {
            return;
        }

        String url = buildUrl(mc.player.getUUID().toString());
        if (browser == null) {
            registerLoadHandler();
            browser = MCEF.createBrowser(url, true);
            currentUrl = url;
            lastPixelWidth = -1;
            lastPixelHeight = -1;
            loadFailed = false;
            showingFallback = false;
        } else if (!url.equals(currentUrl)) {
            // balanceUrl changed via config reload.
            currentUrl = url;
            loadFailed = false;
            showingFallback = false;
            browser.loadURL(url);
        }

        long now = System.currentTimeMillis();
        if (loadFailed && !showingFallback) {
            // Swap in the bundled failure page and retry the real one later.
            loadFailed = false;
            showingFallback = true;
            retryAt = now + RETRY_INTERVAL_MILLIS;
            browser.loadURL(fallbackDataUrl());
        } else if (showingFallback && now >= retryAt) {
            showingFallback = false;
            browser.loadURL(currentUrl);
        }

        if (reloadAt != 0L && now >= reloadAt) {
            reloadAt = 0L;
            if (showingFallback) {
                // Retry the real page instead of reloading the failure page.
                showingFallback = false;
                browser.loadURL(currentUrl);
            } else {
                browser.reload();
            }
        }

        int width = Math.min(ClientConfig.balanceWidth, screenWidth);
        int height = Math.min(ClientConfig.balanceHeight, screenHeight);
        double guiScale = mc.getWindow().getGuiScale();
        int pixelWidth = Math.max(1, (int) (width * guiScale));
        int pixelHeight = Math.max(1, (int) (height * guiScale));
        if (pixelWidth != lastPixelWidth || pixelHeight != lastPixelHeight) {
            browser.resize(pixelWidth, pixelHeight);
            lastPixelWidth = pixelWidth;
            lastPixelHeight = pixelHeight;
        }

        // The full-screen shop browser already covers this corner; drawing the
        // balance box through the shop page's transparent areas is just noise.
        if (mc.screen instanceof ShopBrowserScreen) {
            return;
        }

        if (!browser.isTextureReady()) {
            return;
        }
        ResourceLocation texture = browser.getTextureLocation();
        if (texture == null) {
            return;
        }

        int margin = ClientConfig.balanceMargin;
        int x = switch (ClientConfig.balancePosition) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - margin;
        };
        int y = switch (ClientConfig.balancePosition) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - margin;
        };
        guiGraphics.blit(texture, x, y, 0.0F, 0.0F, width, height, width, height);
    }

    private static String buildUrl(String playerUuid) {
        String base = ClientConfig.balanceUrl;
        if (base.contains("{uuid}")) {
            return base.replace("{uuid}", playerUuid);
        }
        return base + playerUuid;
    }

    private void close() {
        if (browser != null) {
            browser.close();
            browser = null;
            currentUrl = null;
            lastPixelWidth = -1;
            lastPixelHeight = -1;
            loadFailed = false;
            showingFallback = false;
        }
    }

    // --- load-failure fallback ---------------------------------------------------------

    private static String cachedFallbackDataUrl;

    /** The bundled failure page, encoded as a data: URL (jar resources have no file: path). */
    private static String fallbackDataUrl() {
        if (cachedFallbackDataUrl == null) {
            String html;
            try (InputStream in = BalanceOverlay.class.getResourceAsStream("/assets/webshoplink/balance_fallback.html")) {
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException | NullPointerException e) {
                html = "<html><body style=\"margin:0;background:rgba(15,23,42,.85);color:#fca5a5;"
                        + "display:flex;align-items:center;justify-content:center;height:100vh;"
                        + "font-family:sans-serif\">Failed to load balance</body></html>";
            }
            cachedFallbackDataUrl = "data:text/html;base64,"
                    + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
        }
        return cachedFallbackDataUrl;
    }

    /**
     * MCEF's client fans load events out to every browser it owns (including the
     * full-screen shop), so the handler filters for this overlay's browser. CEF
     * invokes it on its own thread; it only flips the volatile flag and leaves
     * all loadURL calls to the render thread.
     */
    private void registerLoadHandler() {
        if (loadHandlerRegistered) {
            return;
        }
        loadHandlerRegistered = true;
        MCEF.getClient().addLoadHandler(new CefLoadHandler() {
            @Override
            public void onLoadError(CefBrowser cefBrowser, CefFrame frame, ErrorCode errorCode,
                                    String errorText, String failedUrl) {
                // ERR_ABORTED is fired for navigations we interrupt ourselves
                // (e.g. loading the fallback page over a pending load) — not a failure.
                if (cefBrowser == browser && frame.isMain() && errorCode != ErrorCode.ERR_ABORTED) {
                    loadFailed = true;
                }
            }

            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                // A non-2xx answer (e.g. 404/500 from the shop server) renders an
                // error/HTML page we don't control; treat it as a failure too.
                if (cefBrowser == browser && frame.isMain()
                        && frame.getURL() != null && frame.getURL().startsWith("http")
                        && (httpStatusCode < 200 || httpStatusCode >= 300)) {
                    loadFailed = true;
                }
            }

            @Override
            public void onLoadingStateChange(CefBrowser cefBrowser, boolean isLoading,
                                             boolean canGoBack, boolean canGoForward) {
            }

            @Override
            public void onLoadStart(CefBrowser cefBrowser, CefFrame frame,
                                    CefRequest.TransitionType transitionType) {
            }
        });
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.close();
    }
}
