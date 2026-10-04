package info.rusty.webshoplink.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Owns the single, long-lived MCEF browser used for shop sessions.
 *
 * <p>Creating a Chromium browser per session is what produced the white flash at
 * the start of every shop: a brand-new browser has no painted frame yet, and the
 * first frame it does paint is the empty document — not the shop page. Instead we
 * create the browser once (at login, or lazily on the first {@code /shop}) and
 * keep it parked on a fully transparent blank page. Starting a session only
 * navigates that browser, and Chromium keeps showing the previous (transparent)
 * frame until the new page has actually painted, so there is nothing to flash.
 *
 * <p>On top of that the screen is told not to blit until the page is
 * {@linkplain #isPageReady() ready}, so a slow shop shows the game world rather
 * than a half-built page. Readiness is, in order of preference:
 * <ol>
 *   <li>the page setting {@code document.title = "webshoplink:ready"} (opt-in
 *       handshake — the only way to know the page has finished its own
 *       client-side rendering),</li>
 *   <li>the document's load event, plus a short grace for the first paint,</li>
 *   <li>a hard timeout, so a broken page can never wedge the screen shut.</li>
 * </ol>
 *
 * <p>Client-only: references MCEF and is reached exclusively from client code. Each loader
 * forwards login/logout to {@link #onLoggingIn()} and {@link #onLoggingOut()}.
 */
public final class ShopBrowserHost {

    /** Title a shop page may set to declare itself painted and ready to be shown. */
    public static final String READY_TITLE = "webshoplink:ready";

    /** Grace after the document's load event, to let Chromium paint the first frame. */
    private static final long LOAD_END_GRACE_MILLIS = 120L;
    /** Upper bound on how long the page stays hidden; a broken page is shown as-is. */
    private static final long REVEAL_TIMEOUT_MILLIS = 5_000L;

    /**
     * Parking page. A transparent document rather than {@code about:blank} so the
     * resting frame is guaranteed to composite to nothing over the game world.
     */
    private static final String BLANK_PAGE = "data:text/html;base64,"
            + Base64.getEncoder().encodeToString(
            ("<!doctype html><html><head><meta charset=\"utf-8\">"
                    + "<style>html,body{margin:0;height:100%;background:transparent}</style>"
                    + "</head><body></body></html>").getBytes(StandardCharsets.UTF_8));

    private static MCEFBrowser browser;
    private static boolean handlersRegistered;

    /** Non-null while a shop session is on screen. */
    private static volatile String sessionUrl;
    /**
     * Bumped by every {@link #startSession}. A screen remembers the token it was
     * opened for so that a screen being replaced (running {@code /shop} while a
     * shop is already open) cannot park the browser out from under its successor.
     */
    private static volatile long sessionToken;
    /** Epoch millis at which the page may be shown; 0 = not scheduled yet. */
    private static volatile long revealAt;
    /** Epoch millis after which the page is shown regardless; 0 = no session. */
    private static volatile long hardRevealAt;
    private static volatile boolean revealed;

    private static int lastPixelWidth = -1;
    private static int lastPixelHeight = -1;

    private ShopBrowserHost() {
    }

    /**
     * Create the browser (if needed) and park it on the blank page, so the first
     * {@code /shop} of a session is as instant as every later one. Safe to call
     * before MCEF has finished initializing — it simply does nothing then.
     */
    public static void warmUp() {
        if (ensureBrowser() != null) {
            resizeToShopViewport();
        }
    }

    /** The shared browser, or {@code null} if MCEF isn't up yet. */
    public static MCEFBrowser getBrowser() {
        return browser;
    }

    /** The token identifying the session currently loaded (or being loaded). */
    public static long currentSessionToken() {
        return sessionToken;
    }

    /** Navigate the shared browser to a session URL and hide it until it has painted. */
    public static void startSession(String url) {
        sessionToken++;
        MCEFBrowser b = ensureBrowser();
        if (b == null) {
            return;
        }
        // Size first, then navigate, so the page lays out at its final size and
        // never has to reflow (and repaint) right after becoming visible.
        resizeToShopViewport();

        sessionUrl = url;
        revealed = false;
        revealAt = 0L;
        hardRevealAt = System.currentTimeMillis() + REVEAL_TIMEOUT_MILLIS;
        b.loadURL(url);
    }

    /**
     * Park the browser back on the blank page, keeping it alive for the next
     * session. Ignored if {@code token} is not the current session, which is how a
     * replaced screen's late {@code removed()} keeps its hands off the new one.
     */
    public static void endSession(long token) {
        if (token != sessionToken) {
            return;
        }
        sessionUrl = null;
        revealed = false;
        revealAt = 0L;
        hardRevealAt = 0L;
        if (browser != null) {
            browser.loadURL(BLANK_PAGE);
        }
    }

    /** Whether the current session's page may be drawn yet. */
    public static boolean isPageReady() {
        if (revealed) {
            return true;
        }
        long now = System.currentTimeMillis();
        long scheduled = revealAt;
        long deadline = hardRevealAt;
        if ((scheduled != 0L && now >= scheduled) || (deadline != 0L && now >= deadline)) {
            revealed = true;
        }
        return revealed;
    }

    /** Resize the browser to the area the shop screen gives it, in real pixels. */
    public static void resizeToShopViewport() {
        MCEFBrowser b = browser;
        if (b == null) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        double guiScale = window.getGuiScale();
        int width = (int) (window.getGuiScaledWidth() * guiScale);
        int height = (int) (Math.max(1, window.getGuiScaledHeight() - ShopBrowserScreen.BAR_HEIGHT) * guiScale);
        if (width == lastPixelWidth && height == lastPixelHeight) {
            return;
        }
        lastPixelWidth = width;
        lastPixelHeight = height;
        b.resize(Math.max(1, width), Math.max(1, height));
    }

    private static MCEFBrowser ensureBrowser() {
        if (browser != null) {
            return browser;
        }
        if (!MCEF.isInitialized()) {
            return null;
        }
        registerHandlers();
        // transparent=true lets the page's transparent CSS background show the
        // game world behind the floating shop panel.
        browser = MCEF.createBrowser(BLANK_PAGE, true);
        lastPixelWidth = -1;
        lastPixelHeight = -1;
        return browser;
    }

    private static void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        sessionUrl = null;
        revealed = false;
        revealAt = 0L;
        hardRevealAt = 0L;
        sessionToken++;
        lastPixelWidth = -1;
        lastPixelHeight = -1;
    }

    /**
     * MCEF fans load/display events out to every browser it owns, so both handlers
     * filter for this one. CEF invokes them on its own thread; they only publish to
     * volatile fields and leave every browser call to the render thread.
     */
    private static void registerHandlers() {
        if (handlersRegistered) {
            return;
        }
        handlersRegistered = true;

        MCEF.getClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                if (isSessionLoad(cefBrowser, frame)) {
                    revealAt = System.currentTimeMillis() + LOAD_END_GRACE_MILLIS;
                }
            }

            @Override
            public void onLoadError(CefBrowser cefBrowser, CefFrame frame, ErrorCode errorCode,
                                    String errorText, String failedUrl) {
                // ERR_ABORTED is fired for navigations we interrupt ourselves.
                // Anything else means the player is looking at an error page, and
                // showing it beats hiding the screen until the timeout expires.
                // Matched on failedUrl, not frame.getURL(): a navigation that fails
                // outright leaves the frame on the page it never left.
                if (cefBrowser == browser && sessionUrl != null && frame != null && frame.isMain()
                        && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED
                        && failedUrl != null && !failedUrl.startsWith("data:")) {
                    revealAt = System.currentTimeMillis();
                }
            }
        });

        MCEF.getClient().addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onTitleChange(CefBrowser cefBrowser, String title) {
                if (cefBrowser == browser && sessionUrl != null && READY_TITLE.equals(title)) {
                    revealAt = System.currentTimeMillis();
                }
            }
        });
    }

    /** True for main-frame navigations of our browser that belong to a shop session. */
    private static boolean isSessionLoad(CefBrowser cefBrowser, CefFrame frame) {
        // The parking page is a data: URL; ignoring it keeps a late blank-page load
        // event from revealing the next session's page before it has painted.
        return cefBrowser == browser && sessionUrl != null && frame != null && frame.isMain()
                && frame.getURL() != null && !frame.getURL().startsWith("data:");
    }

    public static void onLoggingIn() {
        warmUp();
    }

    public static void onLoggingOut() {
        close();
    }
}
