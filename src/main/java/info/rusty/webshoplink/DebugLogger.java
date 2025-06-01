package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import info.rusty.webshoplink.DataTypes.InventoryData;
import info.rusty.webshoplink.DataTypes.ItemStackData;

import java.util.List;
import java.util.UUID;

/**
 * Utility class for debug logging in the Webshoplink mod
 */
public class DebugLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Log a message if debugging is enabled and verbosity level is sufficient
     * @param message The message to log
     * @param minimumVerbosity The minimum verbosity level required to log this message
     */
    public static void log(String message, Config.DebugVerbosity minimumVerbosity) {
        if (Config.debugEnabled && Config.debugVerbosity.ordinal() >= minimumVerbosity.ordinal()) {
            LOGGER.info("[Webshoplink Debug] " + message);
        }

        return;
    }

    /**
     * Log a shop session start
     */
    public static void logShopStart(String playerName, UUID playerId, String shopSlug, InventoryData inventoryData) {
        if (!Config.debugEnabled) return;
        
        // For minimal verbosity, just log basic info
        log("Player " + playerName + " started shop session", Config.DebugVerbosity.MINIMAL);
        
        // For default verbosity, log counts
        StringBuilder sb = new StringBuilder();
        sb.append("Player ").append(playerName).append(" (").append(playerId).append(") started shop session with slug: ").append(shopSlug).append("\n");
        sb.append("  - Main Inventory: ").append(countNonEmptyItems(inventoryData.getMainInventory())).append(" items\n");
        sb.append("  - Armor Inventory: ").append(countNonEmptyItems(inventoryData.getArmorInventory())).append(" items\n");
        sb.append("  - Offhand Inventory: ").append(countNonEmptyItems(inventoryData.getOffhandInventory())).append(" items\n");
        sb.append("  - Ender Inventory: ").append(countNonEmptyItems(inventoryData.getEnderChest())).append(" items");
        
        log(sb.toString(), Config.DebugVerbosity.DEFAULT);
        
        // For ALL verbosity, also log full inventory
        log("Full inventory data: \n" + GSON.toJson(inventoryData), Config.DebugVerbosity.ALL);
    }

    /**
     * Log a shop session UUID received from server
     */
    public static void logShopSessionId(UUID processId) {
        if (!Config.debugEnabled) return;

        log("Shop session initiated", Config.DebugVerbosity.MINIMAL);
        
        log("Session UUID from server: " + processId, Config.DebugVerbosity.DEFAULT);
    }

    /**
     * Log shop session checkout
     */
    public static void logShopCheckout(UUID processId, String playerName, String twoFactorCode) {
        if (!Config.debugEnabled) return;

        log("Player " + playerName + " checking out shop session", Config.DebugVerbosity.MINIMAL);
        
        log("Player " + playerName + " checking out shop session: " + processId + " with code: " + twoFactorCode, Config.DebugVerbosity.DEFAULT);
    }

    /**
     * Log shop session checkout response
     */
    public static void logShopCheckoutResponse(UUID processId, InventoryData newInventory) {
        if (!Config.debugEnabled) return;
        
        log("Received checkout response", Config.DebugVerbosity.MINIMAL);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Received checkout response for session ").append(processId).append("\n");
        sb.append("  - Main Inventory: ").append(countNonEmptyItems(newInventory.getMainInventory())).append(" items\n");
        sb.append("  - Armor Inventory: ").append(countNonEmptyItems(newInventory.getArmorInventory())).append(" items\n");
        sb.append("  - Offhand Inventory: ").append(countNonEmptyItems(newInventory.getOffhandInventory())).append(" items\n");
        sb.append("  - Ender Inventory: ").append(countNonEmptyItems(newInventory.getEnderChest())).append(" items");
        
        log(sb.toString(), Config.DebugVerbosity.DEFAULT);
        
        log("Full new inventory data: \n" + GSON.toJson(newInventory), Config.DebugVerbosity.ALL);
    }

    /**
     * Log inventory application
     */
    public static void logInventoryApplied(UUID processId, String playerName) {
        if (!Config.debugEnabled) return;
        
        log("Applied inventory changes to player " + playerName, Config.DebugVerbosity.MINIMAL);
        
        log("Applied inventory changes from session " + processId + " to player " + playerName, Config.DebugVerbosity.DEFAULT);
    }

    /**
     * Log notification of changes applied
     */
    public static void logNotifyChangesApplied(UUID processId) {
        if (!Config.debugEnabled) return;
        
        log("Notified server that changes were applied", Config.DebugVerbosity.MINIMAL);
        
        log("Notified server that changes for session " + processId + " were applied", Config.DebugVerbosity.DEFAULT);
    }

    /**
     * Log an error
     */
    public static void logError(String message, Throwable error) {
        // Always log errors regardless of debug config
        LOGGER.error("[Webshoplink Error] " + message, error);
    }

    /**
     * Count non-empty items in a list of ItemStackData
     */
    private static int countNonEmptyItems(List<ItemStackData> items) {
        if (items == null) return 0;
        
        return (int) items.stream()
                .filter(item -> item != null && 
                        !("minecraft:air".equals(item.getItemId()) || item.getCount() <= 0))
                .count();
    }
}
