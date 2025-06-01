package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Utility class for debug logging in the Webshoplink mod
 */
public class DebugLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Log a message if debugging is enabled. this is just a proxy function for logging with default level.
     * @param message The message to log
     */
    public static void log(String message) {
        log(message, Config.DebugVerbosity.DEFAULT);
    }

    /**
     * Log a message if debugging is enabled and verbosity level is sufficient
     * @param message The message to log
     * @param minimumVerbosity The minimum verbosity level required to log this message
     */
    public static void log(String message, Config.DebugVerbosity minimumVerbosity) {
        if (Config.debugEnabled && Config.debugVerbosity.ordinal() >= minimumVerbosity.ordinal()) {
            LOGGER.info("[Webshoplink Debug] " + message);
        }
    }

    /**
     * Log an error
     */
    public static void logError(String message, Throwable error) {
        // Always log errors regardless of debug config
        LOGGER.error("[Webshoplink Error] " + message, error);
    }
}
