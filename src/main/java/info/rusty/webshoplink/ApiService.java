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
import net.minecraft.nbt.CompoundTag;

/**
 * Handles all API communication for the Webshoplink mod.
 */
public class ApiService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(CompoundTag.class, new NbtSerializer.CompoundTagAdapter())
            .create();
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
                        
                        // Validate the UUID in the response
                        if (shopResponse.getUuid() == null || shopResponse.getUuid().isEmpty()) {
                            DebugLogger.logError("Invalid response: Missing UUID in shop response", null);
                            shopResponse.setErrorMessage("Invalid response: Missing UUID in shop response");
                            return shopResponse;
                        }
                        
                        // Validate the link in the response
                        if (shopResponse.getLink() == null || shopResponse.getLink().isEmpty()) {
                            DebugLogger.logError("Invalid response: Missing link in shop response", null);
                            shopResponse.setErrorMessage("Invalid response: Missing link in shop response");
                            return shopResponse;
                        }
                        
                        DebugLogger.log("Shop session initiated", Config.DebugVerbosity.MINIMAL);
                        DebugLogger.log("Session UUID from server: " + shopResponse.getUuid(), Config.DebugVerbosity.DEFAULT);
                        return shopResponse;
                    } else {
                        String errorMsg = "Error from shop API: " + response.statusCode() + " - " + response.body();
                        DebugLogger.logError(errorMsg, null);
                        
                        // Try to parse error message from response body
                        ShopResponse errorResponse = new ShopResponse();
                        
                        try {
                            // First try parsing as ShopResponse directly
                            errorResponse = GSON.fromJson(response.body(), ShopResponse.class);
                            
                            // If error message is null, try parsing as error object with message field
                            if (errorResponse.getErrorMessage() == null) {
                                Map<String, String> errorMap = GSON.fromJson(response.body(), Map.class);
                                if (errorMap.containsKey("message") || errorMap.containsKey("error")) {
                                    String message = errorMap.getOrDefault("message", 
                                                    errorMap.getOrDefault("error", "Unknown error"));
                                    errorResponse.setErrorMessage(message);
                                    DebugLogger.log("Parsed error message: " + message, Config.DebugVerbosity.DEFAULT);
                                } else {
                                    errorResponse.setErrorMessage("API error: " + response.statusCode());
                                }
                            }
                        } catch (Exception e) {
                            // If parsing fails, create a generic error message
                            DebugLogger.logError("Failed to parse error response: " + e.getMessage(), e);
                            errorResponse.setErrorMessage("API error: " + response.statusCode() + " - " + response.body());
                        }
                        
                        return errorResponse;
                    }
                }).exceptionally(ex -> {
                    DebugLogger.logError("Exception during API call", ex);
                    ShopResponse errorResponse = new ShopResponse();
                    if (ex.getCause() != null) {
                        errorResponse.setErrorMessage("Communication error: " + ex.getCause().getMessage());
                    } else {
                        errorResponse.setErrorMessage("Communication error: Failed to connect to shop server");
                    }
                    return errorResponse;
                });
    }

    /**
     * Cancels a shop session with the API
     */
    public static CompletableFuture<Boolean> cancelShop(UUID processId, String playerName, String twoFactorCode) {
        // Log cancellation attempt
        DebugLogger.log("Player " + playerName + " cancelled shop session", Config.DebugVerbosity.MINIMAL);
        DebugLogger.log("Player " + playerName + " cancelling shop session: " + processId + " with code: " + twoFactorCode, Config.DebugVerbosity.DEFAULT);

        // Create request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("tfaCode", twoFactorCode);

        // Send HTTP request
        String jsonPayload = GSON.toJson(payload);
        String endpoint = Config.apiBaseUrl + Config.shopCancelEndpoint.replace("{uuid}", processId.toString());

        DebugLogger.log("Sending cancellation request to: " + endpoint, Config.DebugVerbosity.DEFAULT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        // Process the request asynchronously
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        DebugLogger.log("Received shop cancel response: " + response.body(), Config.DebugVerbosity.ALL);
                        // cancel was successful.
                        return true;
                    } else {
                        String errorMsg;
                          // Try to parse error message from response body
                        try {
                            Map<String, String> errorMap = GSON.fromJson(response.body(), Map.class);
                            if (errorMap.containsKey("message") || errorMap.containsKey("error")) {
                                errorMsg = errorMap.getOrDefault("message", 
                                        errorMap.getOrDefault("error", "Unknown error"));
                                DebugLogger.log("Parsed error message from cancelShop: " + errorMsg, Config.DebugVerbosity.DEFAULT);
                            } else {
                                errorMsg = "API error: " + response.statusCode() + " - " + response.body();
                            }
                        } catch (Exception e) {
                            // If parsing fails, use a generic error message
                            DebugLogger.logError("Error parsing response in cancelShop: " + e.getMessage(), e);
                            errorMsg = "Error from shop cancel API: " + response.statusCode() + " - " + response.body();
                        }
                        
                        DebugLogger.logError(errorMsg, null);
                        throw new ErrorResponse(errorMsg, response.statusCode());
                    }
                }).exceptionally(ex -> {
                    if (ex.getCause() instanceof ErrorResponse) {
                        // Just rethrow if it's already our custom error
                        throw (ErrorResponse) ex.getCause();
                    }
                    
                    DebugLogger.logError("Exception during cancel API call", ex);
                    throw new ErrorResponse("API communication error: Failed to connect to shop server", 0);
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
                        String errorMsg;
                          // Try to parse error message from response body
                        try {
                            Map<String, String> errorMap = GSON.fromJson(response.body(), Map.class);
                            if (errorMap.containsKey("message") || errorMap.containsKey("error")) {
                                errorMsg = errorMap.getOrDefault("message", 
                                        errorMap.getOrDefault("error", "Unknown error"));
                                DebugLogger.log("Parsed error message from finishShop: " + errorMsg, Config.DebugVerbosity.DEFAULT);
                            } else {
                                errorMsg = "API error: " + response.statusCode() + " - " + response.body();
                            }
                        } catch (Exception e) {
                            // If parsing fails, use a generic error message
                            DebugLogger.logError("Error parsing response in finishShop: " + e.getMessage(), e);
                            errorMsg = "Error from shop finish API: " + response.statusCode() + " - " + response.body();
                        }
                        
                        DebugLogger.logError(errorMsg, null);
                        throw new ErrorResponse(errorMsg, response.statusCode());
                    }
                }).exceptionally(ex -> {
                    if (ex.getCause() instanceof ErrorResponse) {
                        // Just rethrow if it's already our custom error
                        throw (ErrorResponse) ex.getCause();
                    }
                    
                    DebugLogger.logError("Exception during checkout API call", ex);
                    throw new ErrorResponse("API communication error: Failed to connect to shop server", 0);
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
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        String errorMsg;
                        
                        // Try to parse error message from response body                        // Try to parse error message from response body
                        try {
                            Map<String, String> errorMap = GSON.fromJson(response.body(), Map.class);
                            if (errorMap.containsKey("message") || errorMap.containsKey("error")) {
                                errorMsg = errorMap.getOrDefault("message", 
                                        errorMap.getOrDefault("error", "Unknown error"));
                                DebugLogger.log("Parsed error message from notifyChangesApplied: " + errorMsg, Config.DebugVerbosity.DEFAULT);
                            } else {
                                errorMsg = "API error: " + response.statusCode() + " - " + response.body();
                            }
                        } catch (Exception e) {
                            // If parsing fails, use a generic error message
                            DebugLogger.logError("Error parsing response in notifyChangesApplied: " + e.getMessage(), e);
                            errorMsg = "Error from API: " + response.statusCode() + " - " + response.body();
                        }
                        
                        DebugLogger.logError("Error notifying changes applied: " + errorMsg, null);
                        throw new ErrorResponse(errorMsg, response.statusCode());
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
                                throw new ErrorResponse("Unexpected response: " + message, response.statusCode());
                            }
                        } catch (Exception e) {
                            DebugLogger.logError("Error parsing response: " + response.body(), e);
                            throw new ErrorResponse("Error parsing response: " + e.getMessage(), response.statusCode());
                        }
                    }
                }).exceptionally(ex -> {
                    if (ex.getCause() instanceof ErrorResponse) {
                        // Just rethrow if it's already our custom error
                        throw (ErrorResponse) ex.getCause();
                    }
                    
                    DebugLogger.logError("Exception during notification API call", ex);
                    throw new ErrorResponse("API communication error: Failed to connect to shop server", 0);
                });
    }
}
