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
    public static CompletableFuture<ShopResponse> initiateShop(UUID playerId, String shopSlug, InventoryData inventoryData) {
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
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.apiBaseUrl + Config.shopEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        
        // Process the request asynchronously
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return GSON.fromJson(response.body(), ShopResponse.class);
                    } else {
                        LOGGER.error("Error from shop API: " + response.statusCode() + " - " + response.body());
                        throw new RuntimeException("API error: " + response.statusCode());
                    }
                });
    }

    /**
     * Finishes a shop session with the API
     */
    public static CompletableFuture<ShopFinishResponse> finishShop(UUID processId, String twoFactorCode) {
        // Create request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("tfaCode", twoFactorCode);
        
        // Send HTTP request
        String jsonPayload = GSON.toJson(payload);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.apiBaseUrl + Config.shopCheckoutEndpoint.replace("{uuid}", processId.toString())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        
        // Process the request asynchronously
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        LOGGER.info("Received shop finish response: " + response.body());
                        return GSON.fromJson(response.body(), ShopFinishResponse.class);
                    } else {
                        LOGGER.error("Error from shop finish API: " + response.statusCode() + " - " + response.body());
                        throw new RuntimeException("API error: " + response.statusCode());
                    }
                });
    }

    /**
     * Notifies the API that changes were applied
     */
    public static CompletableFuture<Void> notifyChangesApplied(UUID processId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.apiBaseUrl + Config.shopAppliedEndpoint.replace("{uuid}", processId.toString())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        
        // Process the request asynchronously, don't wait for response
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> null);
    }
}
