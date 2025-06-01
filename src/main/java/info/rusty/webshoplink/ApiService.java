package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import static info.rusty.webshoplink.DataTypes.*;

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
    public static CompletableFuture<ShopResponse> initiateShop(UUID playerId, String playerName, String shopSlug, InventoryList inventories) {
        DebugLogger.log("Player " + playerName + " started shop session", Config.DebugVerbosity.MINIMAL);
        
        // Create request payload with the new structure
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId.toString());
        payload.put("shopSlug", shopSlug);
        
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
    public static CompletableFuture<DataTypes.InventoryList> finishShop(UUID processId, String playerName, String twoFactorCode) {
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
                        return GSON.fromJson(response.body(), InventoryList.class);
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
    }
      /**
     * Notifies the API that changes were applied
     * @param processId The UUID of the shop process
     * @param twoFactorCode The two-factor authentication code for verification
     * @return A CompletableFuture that completes when the API confirms the changes were applied
     */
    public static CompletableFuture<Boolean> notifyChangesApplied(UUID processId, String twoFactorCode) {
        DebugLogger.log("Notifying server that changes were applied", Config.DebugVerbosity.MINIMAL);
        DebugLogger.log("Notifying server that changes for session " + processId + " were applied with code: " + twoFactorCode, Config.DebugVerbosity.DEFAULT);

        // Create request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("tfaCode", twoFactorCode);
        
        // Send HTTP request
        String jsonPayload = GSON.toJson(payload);
        String endpoint = Config.apiBaseUrl + Config.shopAppliedEndpoint.replace("{uuid}", processId.toString());
        
        DebugLogger.log("Sending notification to: " + endpoint, Config.DebugVerbosity.DEFAULT);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
          // Process the request asynchronously and wait for response to validate
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        DebugLogger.logError("Error notifying changes applied: " + response.statusCode() + " - " + response.body(), null);
                        return false;
                    } else {
                        DebugLogger.log("Successfully notified changes applied, response: " + response.body(), Config.DebugVerbosity.DEFAULT);
                        // Parse the response to verify the expected message
                        try {
                            Map<String, String> responseMap = GSON.fromJson(response.body(), Map.class);
                            String message = responseMap.get("message");
                            if ("Shop instance marked as applied".equals(message)) {
                                DebugLogger.log("Server confirmed changes were applied", Config.DebugVerbosity.MINIMAL);
                                return true;
                            } else {
                                DebugLogger.logError("Unexpected response message: " + message, null);
                                return false;
                            }
                        } catch (Exception e) {
                            DebugLogger.logError("Error parsing response: " + response.body(), e);
                            return false;
                        }
                    }
                })
                .exceptionally(ex -> {
                    DebugLogger.logError("Exception during notification API call", ex);
                    return false;
                });
    }
}
