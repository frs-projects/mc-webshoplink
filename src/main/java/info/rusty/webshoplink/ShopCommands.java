package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static info.rusty.webshoplink.DataTypes.*;
import static info.rusty.webshoplink.InventoryManager.*;
import static info.rusty.webshoplink.UIUtils.*;

/**
 * Handles command registration and execution
 */
public class ShopCommands {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Store active shopping processes - Map<UUID, ShopProcess>
    private static final Map<UUID, ShopProcess> ACTIVE_SHOP_PROCESSES = new ConcurrentHashMap<>();
    
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering shop commands");
        
        // Register "shop" command with optional label parameter
        event.getDispatcher().register(
            Commands.literal("shop")
                // Configurable: 0 = anyone, 2 = operators/command blocks only. Evaluated lazily
                // so the predicate always reflects the current config value.
                .requires(source -> source.hasPermission(Config.shopCommandPermissionLevel))
                .then(Commands.argument("type", StringArgumentType.string())
                    .executes(context -> executeShopCommand(context.getSource(),
                        StringArgumentType.getString(context, "type"), "Trader"))
                    .then(Commands.argument("label", StringArgumentType.greedyString())
                        .executes(context -> executeShopCommand(context.getSource(),
                            StringArgumentType.getString(context, "type"),
                            StringArgumentType.getString(context, "label")))
                    )
                )
        );
        
        // Register "shopFinish" command
        event.getDispatcher().register(
            Commands.literal("shopFinish")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", StringArgumentType.string())
                    .executes(context -> executeShopFinishCommand(context.getSource(),
                        StringArgumentType.getString(context, "uuid")))
                )
        );
        
        // Register "confirmFinish" command
        event.getDispatcher().register(
            Commands.literal("confirmFinish")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", StringArgumentType.string())
                    .executes(context -> executeConfirmFinishCommand(context.getSource(),
                        StringArgumentType.getString(context, "uuid")))
                )
        );

        // Register "shopCancel" command
        event.getDispatcher().register(
            Commands.literal("shopCancel")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", StringArgumentType.string())
                    .executes(context -> executeShopCancelCommand(context.getSource(),
                        StringArgumentType.getString(context, "uuid")))
                )
        );
    }

    private static int executeShopCommand(CommandSourceStack source, String shopSlug, String shopLabel) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Limit the shop label length to prevent border underflow errors
        // Ensure label will fit within the border (max length 40 to leave room for padding and spaces)
        // Create a final copy of the potentially modified shopLabel for use in lambda
        final String finalShopLabel;
        if (shopLabel.length() > 40) {
            finalShopLabel = shopLabel.substring(0, 40);
            DebugLogger.log("Shop label truncated to 40 characters for player " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);
        } else {
            finalShopLabel = shopLabel;
        }
        
        // Log command execution
        DebugLogger.log("Player " + player.getName().getString() + " executed shop command with slug: " + shopSlug + ", label: " + finalShopLabel, Config.DebugVerbosity.MINIMAL);
        
        // Capture the player's current inventory for later verification
        InventorySnapshot inventorySnapshot = captureInventory(player);
        
        // Serialize the inventory for API communication
        InventoryList inventories = new InventoryList();
        inventories.setInventoryFromPlayer(player.getInventory());
        inventories.setEchestFromPlayer(player.getEnderChestInventory());
        
        // Send debug to server console
        DebugLogger.log("Captured inventory for player " + player.getName().getString() + ": " + GSON.toJson(inventories, InventoryList.class), Config.DebugVerbosity.ALL);
        
        // Check if we have an active shop process for this player
        if (ACTIVE_SHOP_PROCESSES.values().stream().anyMatch(sp -> sp.getPlayerId().equals(player.getUUID()))) {
            DebugLogger.log("Player " + player.getName().getString() + " already has an active shop process. Cancelling previous process.", Config.DebugVerbosity.MINIMAL);
            
            // Cancel the previous shop process
            ACTIVE_SHOP_PROCESSES.values().stream()
                .filter(sp -> sp.getPlayerId().equals(player.getUUID()))
                .findFirst()
                .ifPresent(shopProcess -> {
                    ApiService.cancelShop(shopProcess.getProcessId(), player.getName().getString(), shopProcess.getTwoFactorCode())
                        .thenAccept(success -> {
                            if (success) {
                                DebugLogger.log("Cancelled previous shop process for player " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);
                                player.sendSystemMessage(Component.literal("Your previous shopping process has been cancelled, starting a new one.").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                                // Remove the cancelled process from the active map
                                ACTIVE_SHOP_PROCESSES.remove(shopProcess.getProcessId());
                            } else {
                                DebugLogger.logError("Failed to cancel previous shop process for player " + player.getName().getString(), null);
                                player.sendSystemMessage(Component.literal("Failed to cancel your previous shopping process. Please try again later.").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                            }
                        }).exceptionally(e -> {
                            DebugLogger.logError("Error cancelling previous shop process", e);
                            player.sendSystemMessage(Component.literal("Error cancelling your previous shopping process. Please try again later.").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                            return null;
                        });
                });
        }

        // Send API request to initiate shop process
        ApiService.initiateShop(player.getUUID(), player.getName().getString(), shopSlug, inventories)
            .thenAccept(shopResponse -> {
                try {
                    // Check if there was an error in the response
                    if (shopResponse.hasError()) {
                        DebugLogger.log("Error received from shop API: " + shopResponse.getErrorMessage(), Config.DebugVerbosity.MINIMAL);
                        
                        // Create a new ErrorResponse to get user-friendly messages
                        ErrorResponse errorResponse = new ErrorResponse(shopResponse.getErrorMessage(), 0);
                        
                        // Display formatted error message to player
                        displayErrorMessage(player, errorResponse);
                        return;
                    }
                    
                    // Use the UUID from the response
                    UUID processId;
                    try {
                        processId = UUID.fromString(shopResponse.getUuid());
                    } catch (IllegalArgumentException e) {
                        DebugLogger.logError("Invalid UUID format in shop response: " + shopResponse.getUuid(), e);
                        ErrorResponse errorResponse = new ErrorResponse("Invalid UUID format in shop response", 0);
                        displayErrorMessage(player, errorResponse);
                        return;
                    }
                    
                    // Create a shop process and save the player's current inventory
                    ShopProcess shopProcess = new ShopProcess(player.getUUID(), processId, inventorySnapshot, finalShopLabel);
                    ACTIVE_SHOP_PROCESSES.put(processId, shopProcess);
                    
                    // Store the response data in the shop process
                    shopProcess.setWebLink(shopResponse.getLink());
                    shopProcess.setTwoFactorCode(shopResponse.getTwoFactorCode());
                    
                    // Open the shop in the player's in-game browser. This requires the
                    // WebshopLink client mod (plus MCEF); if the player's client doesn't
                    // have it, tell them to install it.
                    if (Networking.isClientReady(player)) {
                        Networking.openBrowser(player, processId, shopResponse.getLink());
                    } else {
                        player.sendSystemMessage(Component.literal(
                                "This shop opens in an in-game browser. Please install the WebshopLink client mod and MCEF to use it.")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                    }
                } catch (Exception e) {
                    DebugLogger.logError("Error processing shop response", e);
                    
                    // Create a formatted error message
                    ErrorResponse errorResponse = new ErrorResponse("Error processing shop response: " + e.getMessage(), 0);
                    displayErrorMessage(player, errorResponse);
                }
            }).exceptionally(e -> {
                DebugLogger.logError("Error connecting to shop API", e);
                
                // Create an ErrorResponse for connection errors and display to player
                ErrorResponse errorResponse = new ErrorResponse("Failed to connect to shop server", 0);
                displayErrorMessage(player, errorResponse);
                
                return null;
            });
        
        return 1;
    }
    
    private static int executeShopFinishCommand(CommandSourceStack source, String uuidString) {
        return withPlayerAndProcessId(source, uuidString, (player, processId) -> {
            finishShop(player, processId);
            return 1;
        });
    }

    private static int executeConfirmFinishCommand(CommandSourceStack source, String uuidString) {
        return withPlayerAndProcessId(source, uuidString, (player, processId) -> {
            confirmFinish(player, processId);
            return 1;
        });
    }

    private static int executeShopCancelCommand(CommandSourceStack source, String uuidString) {
        return withPlayerAndProcessId(source, uuidString, (player, processId) -> {
            cancelShop(player, processId);
            return 1;
        });
    }

    /**
     * Resolves the command sender to a player and parses the process UUID, then runs
     * {@code action}. Shared by the {@code /shopFinish}, {@code /confirmFinish} and
     * {@code /shopCancel} command entry points.
     */
    private static int withPlayerAndProcessId(CommandSourceStack source, String uuidString,
                                              java.util.function.BiFunction<ServerPlayer, UUID, Integer> action) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        try {
            return action.apply(player, UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            DebugLogger.logError("Invalid UUID format in command: " + uuidString, e);
            player.sendSystemMessage(Component.literal("Invalid UUID format. Please use the UUID provided for the shop session."));
            return 0;
        }
    }

    /**
     * Looks up the player's active process, returning {@code null} (after messaging the
     * player) if it does not exist or does not belong to them.
     */
    private static ShopProcess requireProcess(ServerPlayer player, UUID processId) {
        ShopProcess shopProcess = ACTIVE_SHOP_PROCESSES.get(processId);
        if (shopProcess == null || !shopProcess.getPlayerId().equals(player.getUUID())) {
            DebugLogger.log("No active shopping process found for player " + player.getName().getString() + " with ID: " + processId);
            player.sendSystemMessage(Component.literal("No active shopping process found for that ID."));
            return null;
        }
        return shopProcess;
    }

    /**
     * Runs inventory/world-touching work on the main server thread. The HTTP client's
     * worker threads must never mutate player inventories directly.
     */
    private static void onServerThread(ServerPlayer player, Runnable task) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(task);
        } else {
            task.run();
        }
    }

    /**
     * Performs the API checkout for the session and stores the resulting inventory on
     * the process. Completes with {@code true} on success, {@code false} (after messaging
     * the player) on any error.
     */
    private static CompletableFuture<Boolean> checkout(ServerPlayer player, UUID processId) {
        ShopProcess shopProcess = requireProcess(player, processId);
        if (shopProcess == null) {
            return CompletableFuture.completedFuture(false);
        }

        DebugLogger.log("Player " + player.getName().getString() + " checking out process: " + processId, Config.DebugVerbosity.MINIMAL);

        return ApiService.finishShop(processId, player.getName().getString(), shopProcess.getTwoFactorCode())
                .thenApply(newInventoryList -> {
                    InventoryData inventoryData = newInventoryList.getInventoryData();
                    ContainerData echestData = newInventoryList.getEnderChestData();
                    if (inventoryData == null) {
                        DebugLogger.logError("Failed to parse inventory data from response", null);
                        displayErrorMessage(player, new ErrorResponse("Failed to parse inventory data from response", 0));
                        return false;
                    }
                    shopProcess.setNewInventory(inventoryData);
                    shopProcess.setNewEchest(echestData);
                    DebugLogger.log("Successfully stored new inventory for player " + player.getName().getString() + ", process: " + processId);
                    return true;
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause();
                    DebugLogger.logError("Error during shop finish", cause);
                    if (cause instanceof ErrorResponse error) {
                        displayErrorMessage(player, error);
                    } else {
                        displayConnectionError(player, "Failed to connect to shop server.");
                    }
                    return false;
                });
    }

    /**
     * Checks out the session and then sends a clickable chat link to apply the changes.
     * Used by the {@code /shopFinish} chat command as a manual backstop.
     */
    static void finishShop(ServerPlayer player, UUID processId) {
        checkout(player, processId).thenAccept(ok -> {
            if (ok) {
                sendConfirmChatLink(player, processId);
            }
        });
    }

    /**
     * Checks out the session and, on success, applies the changes immediately. Used by the
     * in-game browser's "Finish Trade" button, which has no separate confirm step.
     */
    static void finishAndConfirm(ServerPlayer player, UUID processId) {
        checkout(player, processId).thenAccept(ok -> {
            if (ok) {
                confirmFinish(player, processId);
            }
        });
    }

    /**
     * Sends the clickable "Confirm and Apply Changes" chat message for a checked-out session.
     */
    private static void sendConfirmChatLink(ServerPlayer player, UUID processId) {
        Component spacerComponent = Component.literal("");
        Component headerComponent = createShopBorder("Confirm Checkout", true);
        Component footerComponent = createShopBorder("", false);
        Component confirmComponent = Component.literal(">>>> ")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                .append(Component.literal("Confirm and Apply Changes")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.GREEN)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/confirmFinish " + processId))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm purchase")))))
                .append(Component.literal(" <<<<").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));

        player.sendSystemMessage(spacerComponent);
        player.sendSystemMessage(headerComponent);
        player.sendSystemMessage(confirmComponent);
        player.sendSystemMessage(footerComponent);
        player.sendSystemMessage(spacerComponent);
    }

    /**
     * Verifies the player's inventory is unchanged since checkout, notifies the API, and
     * applies the new inventory. Runs on the main server thread.
     */
    static void confirmFinish(ServerPlayer player, UUID processId) {
        onServerThread(player, () -> {
            ShopProcess shopProcess = requireProcess(player, processId);
            if (shopProcess == null) {
                return;
            }
            if (shopProcess.getNewInventory() == null) {
                player.sendSystemMessage(Component.literal("This session was not checked out yet."));
                return;
            }

            DebugLogger.log("Player " + player.getName().getString() + " confirming shop process: " + processId);

            // Reject the purchase if the player's inventory changed since checkout.
            InventorySnapshot currentInventory = captureInventory(player);
            if (!inventoriesMatch(shopProcess.getOriginalInventory(), currentInventory)) {
                DebugLogger.log("Inventory changed for player " + player.getName().getString() + ", purchase cancelled", Config.DebugVerbosity.MINIMAL);
                String differences = InventoryManager.getInventoryDifferences(shopProcess.getOriginalInventory(), currentInventory);

                player.sendSystemMessage(Component.literal("Your inventory has changed since starting the shop process. Purchase cancelled.")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                if (differences != null) {
                    player.sendSystemMessage(Component.literal("Changes detected: " + differences)
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                }

                DebugLogger.log("Inventory differences: " + (differences != null ? differences : "Unknown"), Config.DebugVerbosity.MINIMAL);
                DebugLogger.log("Original inventory: " + GSON.toJson(shopProcess.getOriginalInventory()), Config.DebugVerbosity.ALL);
                DebugLogger.log("Current inventory: " + GSON.toJson(currentInventory), Config.DebugVerbosity.ALL);

                ACTIVE_SHOP_PROCESSES.remove(processId);
                return;
            }

            // Notify the API the changes will be applied, then apply them on the server thread.
            ApiService.notifyChangesApplied(processId, shopProcess.getTwoFactorCode())
                    .thenAccept(success -> onServerThread(player, () -> {
                        applyNewInventory(player, shopProcess.getNewInventory());
                        applyNewEchest(player, shopProcess.getNewEchest());
                        DebugLogger.log("Applied inventory changes from session " + processId + " to player " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);
                        displaySuccessMessage(player, shopProcess.getShopLabel(), "Purchase completed successfully!");
                        ACTIVE_SHOP_PROCESSES.remove(processId);
                    }))
                    .exceptionally(e -> {
                        Throwable cause = e.getCause();
                        DebugLogger.logError("Error during shop purchase confirmation", cause);
                        if (cause instanceof ErrorResponse error) {
                            displayErrorMessage(player, error);
                        } else {
                            displayConnectionError(player, "Failed to connect to shop server.");
                        }
                        return null;
                    });
        });
    }

    /**
     * Cancels a session both with the API and locally.
     */
    static void cancelShop(ServerPlayer player, UUID processId) {
        ShopProcess shopProcess = requireProcess(player, processId);
        if (shopProcess == null) {
            return;
        }
        ApiService.cancelShop(processId, player.getName().getString(), shopProcess.getTwoFactorCode())
                .handle((success, e) -> {
                    // Remove locally in any case; the instance expires server-side anyway.
                    ACTIVE_SHOP_PROCESSES.remove(processId);
                    if (e == null) {
                        player.sendSystemMessage(Component.literal("Your shopping session has been cancelled.")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                    } else {
                        DebugLogger.logError("Error cancelling shop session", e);
                        player.sendSystemMessage(Component.literal("The session could not be cancelled on the server, it will expire on its own.")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                    }
                    return null;
                });
    }
}
