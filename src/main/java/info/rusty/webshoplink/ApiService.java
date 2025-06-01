package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import info.rusty.webshoplink.DataTypes.InventoryData;
import info.rusty.webshoplink.DataTypes.ShopResponse;
import info.rusty.webshoplink.DataTypes.ShopFinishResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles all API communication for the Webshoplink mod.
 */
public class ApiService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Initiates a shop session with the API
     */
    public static CompletableFuture<ShopResponse> initiateShop(UUID playerId, String playerName, String shopSlug, InventoryData inventoryData) {
        DebugLogger.log("Player " + playerName + " started shop session", Config.DebugVerbosity.MINIMAL);

        StringBuilder sb = new StringBuilder();
        sb.append("Player ").append(playerName).append(" (").append(playerId).append(") started shop session with slug: ").append(shopSlug).append("\n");
        sb.append("  - Main Inventory: ").append(InventoryManager.countNonEmptyItems(inventoryData.getMainInventory())).append(" items\n");
        sb.append("  - Armor Inventory: ").append(InventoryManager.countNonEmptyItems(inventoryData.getArmorInventory())).append(" items\n");
        sb.append("  - Offhand Inventory: ").append(InventoryManager.countNonEmptyItems(inventoryData.getOffhandInventory())).append(" items\n");
        sb.append("  - Ender Inventory: ").append(InventoryManager.countNonEmptyItems(inventoryData.getEnderChest())).append(" items");
        
        DebugLogger.log(sb.toString(), Config.DebugVerbosity.DEFAULT);
        
        // For ALL verbosity, also log full inventory
        DebugLogger.log("Full inventory data: \n" + GSON.toJson(inventoryData), Config.DebugVerbosity.ALL);
        
        // Create request payload with the new structure
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId.toString());
        payload.put("shopSlug", shopSlug);
        
        // Create inventories object with all inventory types
        Map<String, Object> inventories = new HashMap<>();
        inventories.put("mainInventory", inventoryData.getMainInventory());
        inventories.put("armorInventory", inventoryData.getArmorInventory());
        inventories.put("offhandInventory", inventoryData.getOffhandInventory());
        inventories.put("enderChestInventory", inventoryData.getEnderChest());
        
        payload.put("inventories", inventories);
        
        // Send HTTP request
        String jsonPayload = GSON.toJson(payload);
        
        DebugLogger.log("Sending API request to: " + Config.apiBaseUrl + Config.shopEndpoint, Config.DebugVerbosity.DEFAULT);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.apiBaseUrl + Config.shopEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        // Process the request asynchronously
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        DebugLogger.log("Received successful response: " + response.body(), Config.DebugVerbosity.DEFAULT);
                        ShopResponse shopResponse = GSON.fromJson(response.body(), ShopResponse.class);
                        DebugLogger.log("Shop session initiated", Config.DebugVerbosity.MINIMAL);
                        DebugLogger.log("Session UUID from server: " + shopResponse.getUuid(), Config.DebugVerbosity.DEFAULT);
                        return shopResponse;
                    } else {
                        String errorMsg = "Error from shop API: " + response.statusCode() + " - " + response.body();
                        DebugLogger.logError(errorMsg, null);
                        throw new RuntimeException("API error: " + response.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.logError("Exception during API call", ex);
                    throw new RuntimeException("API communication error", ex);
                });
    }
    
    /**
     * Finishes a shop session with the API
     */
    public static CompletableFuture<ShopFinishResponse> finishShop(UUID processId, String playerName, String twoFactorCode) {
        // Log checkout attempt
        DebugLogger.log("Player " + playerName + " checking out shop session", Config.DebugVerbosity.MINIMAL);
        DebugLogger.log("Player " + playerName + " checking out shop session: " + processId + " with code: " + twoFactorCode, Config.DebugVerbosity.DEFAULT);
        
        // Create request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("tfaCode", twoFactorCode);
        
        // Send HTTP request
        String jsonPayload = GSON.toJson(payload);
        String endpoint = Config.apiBaseUrl + Config.shopCheckoutEndpoint.replace("{uuid}", processId.toString());
        
        DebugLogger.log("Sending checkout request to: " + endpoint, Config.DebugVerbosity.DEFAULT);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
          // Process the request asynchronously
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        DebugLogger.log("Received shop finish response: " + response.body(), Config.DebugVerbosity.ALL);
                        ShopFinishResponse finishResponse = GSON.fromJson(response.body(), ShopFinishResponse.class);
                        DebugLogger.log("Received checkout response", Config.DebugVerbosity.MINIMAL);
                        StringBuilder sb = new StringBuilder();
                        sb.append("Received checkout response for session ").append(processId).append("\n");
                        sb.append("  - Main Inventory: ").append(InventoryManager.countNonEmptyItems(finishResponse.getInventoryData().getMainInventory())).append(" items\n");
                        sb.append("  - Armor Inventory: ").append(InventoryManager.countNonEmptyItems(finishResponse.getInventoryData().getArmorInventory())).append(" items\n");
                        sb.append("  - Offhand Inventory: ").append(InventoryManager.countNonEmptyItems(finishResponse.getInventoryData().getOffhandInventory())).append(" items\n");
                        sb.append("  - Ender Inventory: ").append(InventoryManager.countNonEmptyItems(finishResponse.getInventoryData().getEnderChest())).append(" items");
                        DebugLogger.log(sb.toString(), Config.DebugVerbosity.DEFAULT);
                        DebugLogger.log("Full new inventory data: \n" + GSON.toJson(finishResponse.getInventoryData()), Config.DebugVerbosity.ALL);
                        return finishResponse;
                    } else {
                        String errorMsg = "Error from shop finish API: " + response.statusCode() + " - " + response.body();
                        DebugLogger.logError(errorMsg, null);
                        throw new RuntimeException("API error: " + response.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.logError("Exception during checkout API call", ex);
                    throw new RuntimeException("API communication error", ex);
                });
    }    /**
     * Notifies the API that changes were applied
     */
    public static CompletableFuture<Void> notifyChangesApplied(UUID processId) {
        DebugLogger.log("Notified server that changes were applied", Config.DebugVerbosity.MINIMAL);
        DebugLogger.log("Notified server that changes for session " + processId + " were applied", Config.DebugVerbosity.DEFAULT);
        
        String endpoint = Config.apiBaseUrl + Config.shopAppliedEndpoint.replace("{uuid}", processId.toString());
        DebugLogger.log("Sending notification to: " + endpoint, Config.DebugVerbosity.DEFAULT);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
          // Process the request asynchronously, don't wait for response
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        DebugLogger.logError("Error notifying changes applied: " + response.statusCode() + " - " + response.body(), null);
                    } else {
                        DebugLogger.log("Successfully notified changes applied, response: " + response.body(), Config.DebugVerbosity.DEFAULT);
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.logError("Exception during notification API call", ex);
                    return null; // With thenAccept, null is properly typed as Void
                });
    }
}
