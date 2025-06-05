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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;
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
                .requires(source -> source.hasPermission(0)) // Anyone can use
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

        // Send debug to server console, then exit. just debugging:
        DebugLogger.log("Captured inventory for player " + player.getName().getString() + ": " + GSON.toJson(inventories, InventoryList.class), Config.DebugVerbosity.ALL);        // Send API request to initiate shop process
        ApiService.initiateShop(player.getUUID(), player.getName().getString(), shopSlug, inventories)
            .thenAccept(shopResponse -> {
                try {
                    // Use the UUID from the response
                    UUID processId = UUID.fromString(shopResponse.getUuid());
                    
                    // Create a shop process and save the player's current inventory
                    ShopProcess shopProcess = new ShopProcess(player.getUUID(), processId, inventorySnapshot, finalShopLabel);
                    ACTIVE_SHOP_PROCESSES.put(processId, shopProcess);
                    
                    // Store the response data in the shop process
                    shopProcess.setWebLink(shopResponse.getLink());
                    shopProcess.setTwoFactorCode(shopResponse.getTwoFactorCode());
                    
                    // Create formatted header with the shop label
                    Component headerComponent = createShopBorder(shopProcess.getShopLabel(), true);
                    Component footerComponent = createShopBorder("", false);
                    Component spacerComponent = Component.literal("");
                    
                    // Step 1: Open Shop link
                    Component openShopComponent = Component.literal("1. ")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                            .append(Component.literal(">>>> Open Shop <<<<")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.BLUE)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, shopResponse.getLink()))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open the shop in your browser")))));
                                            
                    // Step 2: Instructions
                    Component instructionsComponent = Component.literal("2. Make your Purchases in the Browser")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
                    
                    // Step 3: Finish Trade
                    Component finishComponent = Component.literal("3. ")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                            .append(Component.literal(">>>> Finish Trade <<<<")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.GOLD)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shopFinish " + shopResponse.getUuid()))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to complete purchase")))));
                    
                    // Send the complete shop UI to the player
                    player.sendSystemMessage(spacerComponent);
                    player.sendSystemMessage(headerComponent);
                    player.sendSystemMessage(openShopComponent);
                    player.sendSystemMessage(instructionsComponent);
                    player.sendSystemMessage(finishComponent);
                    player.sendSystemMessage(footerComponent);
                    player.sendSystemMessage(spacerComponent);
                    
                    // Remove money items from the player's inventory
                    int removedItems = removeMoneyItems(player);
                    if (removedItems > 0) {
                        player.sendSystemMessage(Component.literal("Removed " + removedItems + " money items from your Inventory and Ender Chest.")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                        DebugLogger.log("Removed " + removedItems + " money items from player " + player.getName().getString());
                    }
                } catch (Exception e) {
                    DebugLogger.logError("Error processing shop response", e);
                    player.sendSystemMessage(Component.literal("Error processing shop response. Please try again later."));
                }
            })
            .exceptionally(e -> {
                DebugLogger.logError("Error connecting to shop API", e);
                player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
                return null;
            });
        
        return 1;
    }
    
    private static int executeShopFinishCommand(CommandSourceStack source, String uuidString) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        try {
            // The uuidString is now the actual process UUID from the API
            UUID processId = UUID.fromString(uuidString);
            ShopProcess shopProcess = ACTIVE_SHOP_PROCESSES.get(processId);
            
            // Log shop finish attempt
            DebugLogger.log("Player " + player.getName().getString() + " executing shopFinish command for process: " + processId, Config.DebugVerbosity.MINIMAL);
            
            // Verify this shop process belongs to the player
            if (shopProcess == null || !shopProcess.getPlayerId().equals(player.getUUID())) {
                DebugLogger.log("No active shopping process found for player " + player.getName().getString() + " with ID: " + processId);
                player.sendSystemMessage(Component.literal("No active shopping process found for that ID."));
                return 0;
            }
            
            // Make the API call to finish the shop process
            ApiService.finishShop(processId, player.getName().getString(), shopProcess.getTwoFactorCode())
                .thenAccept(newInventoryList -> {
                    try {
                        // Get the inventory data from the response
                        InventoryData inventoryData = newInventoryList.getInventoryData();
                        ContainerData echestData = newInventoryList.getEnderChestData();
                        
                        if (inventoryData == null) {
                            DebugLogger.logError("Failed to parse inventory data from response", null);
                            player.sendSystemMessage(Component.literal("Error processing shop finish response. Please try again later."));
                            return;
                        }
                        
                        // Store the new inventory in the shop process
                        shopProcess.setNewInventory(inventoryData);
                        shopProcess.setNewEchest(echestData);
                        DebugLogger.log("Successfully stored new inventory for player " + player.getName().getString() + ", process: " + processId);

                        // Create the confirmation message with a clickable button
                        Component spacerComponent = Component.literal("");
                        Component headerComponent = createShopBorder("Confirm Checkout", true);
                        Component footerComponent = createShopBorder("", false);

                        /* Disable diff display @2025.06.04 - 10:34AM

                        // Generate and show a diff to the player
                        InventoryDiff diff = generateInventoryDiff(shopProcess.getOriginalInventory(), inventoryData, echestData);
                        
                        Component diffIntroComponent = Component.literal("Your shopping cart contains the following changes:\n")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));

                        // Display removed items in red
                        if (!diff.getRemoved().isEmpty()) {
                            Component removedHeaderComponent = Component.literal("\nRemoved items:")
                                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
                            player.sendSystemMessage(spacerComponent);
                            player.sendSystemMessage(headerComponent);
                            player.sendSystemMessage(diffIntroComponent);
                            player.sendSystemMessage(removedHeaderComponent);
                            
                            for (InventoryChange change : diff.getRemoved()) {
                                Component itemComponent = Component.literal("- " + change.getCount() + "x " + change.getFormattedName())
                                        .withStyle(Style.EMPTY.withColor(ChatFormatting.RED));
                                player.sendSystemMessage(itemComponent);
                            }
                        }
                        
                        // Display added items in green
                        if (!diff.getAdded().isEmpty()) {
                            Component addedHeaderComponent = Component.literal("\nAdded items:")
                                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
                            
                            if (diff.getRemoved().isEmpty()) {
                                // If there were no removed items, we need to send the header first
                                player.sendSystemMessage(spacerComponent);
                                player.sendSystemMessage(headerComponent);
                                player.sendSystemMessage(diffIntroComponent);
                            }
                            
                            player.sendSystemMessage(addedHeaderComponent);
                              for (InventoryChange change : diff.getAdded()) {
                                Component itemComponent = Component.literal("+ " + change.getCount() + "x " + change.getFormattedName())
                                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
                                player.sendSystemMessage(itemComponent);
                            }
                        }
                        
                        // If there are no changes at all
                        if (diff.isEmpty()) {
                            player.sendSystemMessage(spacerComponent);
                            player.sendSystemMessage(headerComponent);
                            player.sendSystemMessage(diffIntroComponent);
                            player.sendSystemMessage(Component.literal("No changes to your inventory.")
                                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));
                        }

                        */
                        
                        Component confirmComponent = Component.literal(">>>> ")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY))
                                .append(Component.literal("Confirm and Apply Changes")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.GREEN)
                                                .withUnderlined(true)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/confirmFinish " + processId))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm purchase")))))
                                .append(Component.literal(" <<<<").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)));

                        player.sendSystemMessage(headerComponent); // Remove this if the diff is enabled again
                        player.sendSystemMessage(confirmComponent);
                        player.sendSystemMessage(footerComponent);
                        player.sendSystemMessage(spacerComponent);

                        
                    } catch (Exception e) {
                        DebugLogger.logError("Error processing shop finish response", e);
                        player.sendSystemMessage(Component.literal("Error processing shop finish response. Please try again later."));
                    }
                })
                .exceptionally(e -> {
                    DebugLogger.logError("Error connecting to shop finish API", e);
                    player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
                    return null;
                });
            
        } catch (IllegalArgumentException e) {
            DebugLogger.logError("Invalid UUID format in shopFinish command: " + uuidString, e);
            player.sendSystemMessage(Component.literal("Invalid UUID format. Please use the UUID provided in the shop link."));
            return 0;
        }
        
        return 1;
    }
    
    private static int executeConfirmFinishCommand(CommandSourceStack source, String uuidString) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        try {
            UUID processId = UUID.fromString(uuidString);
            ShopProcess shopProcess = ACTIVE_SHOP_PROCESSES.get(processId);
            
            // Log confirmation attempt
            DebugLogger.log("Player " + player.getName().getString() + " confirming shop process: " + processId);
            
            if (shopProcess == null || !shopProcess.getPlayerId().equals(player.getUUID())) {
                DebugLogger.log("No active shopping process found for player " + player.getName().getString() + " with ID: " + processId);
                player.sendSystemMessage(Component.literal("No active shopping process found with that ID."));
                return 0;
            }
            
            // Check if the player's inventory has changed since the shop process started
            if (!inventoriesMatch(shopProcess.getOriginalInventory(), captureInventory(player))) {
                DebugLogger.log("Inventory changed for player " + player.getName().getString() + ", purchase cancelled", Config.DebugVerbosity.MINIMAL);
                player.sendSystemMessage(Component.literal("Your inventory has changed since starting the shop process. Purchase cancelled."));
                ACTIVE_SHOP_PROCESSES.remove(processId);
                return 0;
            }
              // First notify the API that the changes will be applied and wait for confirmation
            ApiService.notifyChangesApplied(processId, shopProcess.getTwoFactorCode())
                .thenAccept(success -> {
                    if (success) {
                        // Only apply the inventory changes if the API confirms the transaction
                        applyNewInventory(player, shopProcess.getNewInventory());
                        applyNewEchest(player, shopProcess.getNewEchest());
                        DebugLogger.log("Applied inventory changes to player " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);
                        DebugLogger.log("Applied inventory changes from session " + processId + " to player " + player.getName().getString());

                        // Only show success message after API confirmation and inventory update
                        Component spacerComponent = Component.literal("");
                        Component headerComponent = createShopBorder(shopProcess.getShopLabel(), true);
                        Component successComponent = Component.literal("Purchase completed successfully!")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
                        Component footerComponent = createShopBorder("", false);
                        
                        player.sendSystemMessage(spacerComponent);
                        player.sendSystemMessage(headerComponent);
                        player.sendSystemMessage(successComponent);
                        player.sendSystemMessage(footerComponent);
                        player.sendSystemMessage(spacerComponent);
                        
                        // Log completion
                        DebugLogger.log("Purchase completed successfully for player " + player.getName().getString() + ", process: " + processId, Config.DebugVerbosity.MINIMAL);
                        
                        // Remove the completed shop process
                        ACTIVE_SHOP_PROCESSES.remove(processId);
                    } else {
                        // If API verification fails, inform the player
                        player.sendSystemMessage(Component.literal("Purchase verification failed. Please try again later.")
                                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                        DebugLogger.logError("API verification failed for process: " + processId, null);
                    }
                })
                .exceptionally(e -> {
                    DebugLogger.logError("Error notifying API of applied changes", e);
                    player.sendSystemMessage(Component.literal("Error completing purchase. Please try again later.")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
                    return null;
                });
        } catch (IllegalArgumentException e) {
            DebugLogger.logError("Invalid UUID format in confirmFinish command: " + uuidString, e);
            player.sendSystemMessage(Component.literal("Invalid UUID format. Please use the UUID provided in the shop link."));
            return 0;
        }
        
        return 1;
    }
}
