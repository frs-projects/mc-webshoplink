package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Webshoplink.MODID)
public class Webshoplink {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "webshoplink";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Gson instance for JSON serialization/deserialization
    private static final Gson GSON = new GsonBuilder().create();
    // HttpClient for API requests
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    // Store active shopping processes - Map<UUID, ShopProcess>
    private static final Map<UUID, ShopProcess> ACTIVE_SHOP_PROCESSES = new ConcurrentHashMap<>();
    
    public Webshoplink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("Webshoplink mod initialized");
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("Webshoplink mod loaded on server side");
    }
    
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering shop commands");
        
        // Register "shop" command
        event.getDispatcher().register(
            Commands.literal("shop")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("type", StringArgumentType.string())
                    .executes(context -> {
                        return executeShopCommand(context.getSource(), 
                            StringArgumentType.getString(context, "type"));
                    })
                )
        );
        
        // Register "shopFinish" command
        event.getDispatcher().register(
            Commands.literal("shopFinish")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", StringArgumentType.string())
                    .executes(context -> {
                        return executeShopFinishCommand(context.getSource(), 
                            StringArgumentType.getString(context, "uuid"));
                    })
                )
        );
        
        // Register "confirmFinish" command
        event.getDispatcher().register(
            Commands.literal("confirmFinish")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", StringArgumentType.string())
                    .executes(context -> {
                        return executeConfirmFinishCommand(context.getSource(), 
                            StringArgumentType.getString(context, "uuid"));
                    })
                )
        );
    }
    
    private int executeShopCommand(CommandSourceStack source, String shopSlug) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Capture the player's current inventory for later verification
        InventorySnapshot inventorySnapshot = captureInventory(player);
        
        // Create request payload with the new structure
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", player.getUUID().toString());
        payload.put("shopSlug", shopSlug);
        
        // Create inventories object with all inventory types
        Map<String, Object> inventories = new HashMap<>();
        InventoryData inventoryData = serializeInventory(player);
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
        CompletableFuture<HttpResponse<String>> futureResponse = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        
        futureResponse.thenAccept(response -> {
            if (response.statusCode() == 200) {
                try {
                    // Parse the response
                    ShopResponse shopResponse = GSON.fromJson(response.body(), ShopResponse.class);
                    
                    try {
                        // Use the UUID from the response
                        UUID processId = UUID.fromString(shopResponse.getUuid());
                        
                        // Create a shop process and save the player's current inventory
                        ShopProcess shopProcess = new ShopProcess(player.getUUID(), processId, inventorySnapshot);
                        ACTIVE_SHOP_PROCESSES.put(processId, shopProcess);
                        
                        // Store the response data in the shop process
                        shopProcess.setWebLink(shopResponse.getLink());
                        shopProcess.setTwoFactorCode(shopResponse.getTwoFactorCode());
                        
                        // Send the response to the player
                        Component linkComponent = Component.literal("Click the following link to open the shop: ")
                                .append(Component.literal(shopResponse.getLink())
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.BLUE)
                                                .withUnderlined(true)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, shopResponse.getLink()))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open shop")))));
                        
                        // Use the UUID directly from the API response for the finish command
                        Component finishComponent = Component.literal("When finished shopping, click here to complete your purchase")
                                .withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.GOLD)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shopFinish " + shopResponse.getUuid()))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to complete purchase"))));
                        
                        player.sendSystemMessage(linkComponent);
                        player.sendSystemMessage(finishComponent);
                        
                        // Remove money items from the player's inventory
                        int removedItems = removeMoneyItems(player);
                        if (removedItems > 0) {
                            player.sendSystemMessage(Component.literal("Removed " + removedItems + " money items from your inventory.")
                                    .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
                            LOGGER.info("Removed " + removedItems + " money items from player " + player.getName().getString());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error processing shop response", e);
                        player.sendSystemMessage(Component.literal("Error processing shop response. Please try again later."));
                    }
                    
                } catch (Exception e) {
                    LOGGER.error("Error processing shop response", e);
                    player.sendSystemMessage(Component.literal("Error processing shop response. Please try again later."));
                }
            } else {
                LOGGER.error("Error from shop API: " + response.statusCode() + " - " + response.body());
                player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
            }
        }).exceptionally(e -> {
            LOGGER.error("Error connecting to shop API", e);
            player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
            return null;
        });
        
        return 1;
    }
    
    private int executeShopFinishCommand(CommandSourceStack source, String uuidString) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        try {
            // The uuidString is now the actual process UUID from the API
            UUID processId = UUID.fromString(uuidString);
            ShopProcess shopProcess = ACTIVE_SHOP_PROCESSES.get(processId);
            
            // Verify this shop process belongs to the player
            if (shopProcess == null || !shopProcess.getPlayerId().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("No active shopping process found for that ID."));
                return 0;
            }
            
            // Create request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("tfaCode", shopProcess.getTwoFactorCode());
            
            // Send HTTP request
            String jsonPayload = GSON.toJson(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Config.apiBaseUrl + Config.shopCheckoutEndpoint.replace("{uuid}", uuidString)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            
            // Process the request asynchronously
            CompletableFuture<HttpResponse<String>> futureResponse = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            futureResponse.thenAccept(response -> {
                if (response.statusCode() == 200) {
                    try {
                        LOGGER.info("Received shop finish response: " + response.body());
                        
                        // Parse the response
                        ShopFinishResponse finishResponse = GSON.fromJson(response.body(), ShopFinishResponse.class);
                        
                        // Get the inventory data from the new response format
                        InventoryData inventoryData = finishResponse.getInventoryData();
                        
                        if (inventoryData == null) {
                            LOGGER.error("Failed to parse inventory data from response");
                            player.sendSystemMessage(Component.literal("Error processing shop finish response. Please try again later."));
                            return;
                        }
                        
                        // Store the new inventory in the shop process
                        shopProcess.setNewInventory(inventoryData);
                        
                        // Generate and show a diff to the player
                        String diff = generateInventoryDiff(shopProcess.getOriginalInventory(), inventoryData);
                        
                        // Create the confirmation message with a clickable button
                        Component confirmComponent = Component.literal("Your shopping cart contains the following changes:\n")
                                .append(Component.literal(diff).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)))
                                .append(Component.literal("\n\nClick here to confirm and apply these changes")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.GREEN)
                                                .withUnderlined(true)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/confirmFinish " + processId))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm purchase")))));
                        
                        player.sendSystemMessage(confirmComponent);
                        
                    } catch (Exception e) {
                        LOGGER.error("Error processing shop finish response", e);
                        player.sendSystemMessage(Component.literal("Error processing shop finish response. Please try again later."));
                    }
                } else {
                    LOGGER.error("Error from shop finish API: " + response.statusCode() + " - " + response.body());
                    player.sendSystemMessage(Component.literal("Error finishing shop process. Please try again later."));
                }
            }).exceptionally(e -> {
                LOGGER.error("Error connecting to shop finish API", e);
                player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
                return null;
            });
            
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("Invalid UUID format. Please use the UUID provided in the shop link."));
            return 0;
        }
        
        return 1;
    }
    
    private int executeConfirmFinishCommand(CommandSourceStack source, String uuidString) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        try {
            UUID processId = UUID.fromString(uuidString);
            ShopProcess shopProcess = ACTIVE_SHOP_PROCESSES.get(processId);
            
            if (shopProcess == null || !shopProcess.getPlayerId().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("No active shopping process found with that ID."));
                return 0;
            }
            
            // Check if the player's inventory has changed since the shop process started
            if (!inventoriesMatch(shopProcess.getOriginalInventory(), captureInventory(player))) {
                player.sendSystemMessage(Component.literal("Your inventory has changed since starting the shop process. Purchase cancelled."));
                ACTIVE_SHOP_PROCESSES.remove(processId);
                return 0;
            }
            
            // Apply the new inventory from the shop process
            applyNewInventory(player, shopProcess.getNewInventory());
            
            // The processId is the UUID from the API response
            String apiUuid = processId.toString();
            
            // Notify the API that the changes were applied
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Config.apiBaseUrl + Config.shopAppliedEndpoint.replace("{uuid}", apiUuid)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            
            // Process the request asynchronously
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .exceptionally(e -> {
                        LOGGER.error("Error notifying API of applied changes", e);
                        return null;
                    });
            
            player.sendSystemMessage(Component.literal("Purchase completed successfully!"));
            
            // Remove the completed shop process
            ACTIVE_SHOP_PROCESSES.remove(processId);
            
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("Invalid UUID format. Please use the UUID provided in the shop link."));
            return 0;
        }
        
        return 1;
    }
    
    // Helper methods
    
    private InventorySnapshot captureInventory(Player player) {
        Inventory inventory = player.getInventory();
        int mainSize = inventory.getContainerSize() - inventory.armor.size() - inventory.offhand.size();
        ItemStack[] mainInventory = new ItemStack[mainSize];
        ItemStack[] armorInventory = new ItemStack[inventory.armor.size()];
        ItemStack[] offhandInventory = new ItemStack[inventory.offhand.size()];
        ItemStack[] enderChest = new ItemStack[player.getEnderChestInventory().getContainerSize()];
        
        // Copy all items to prevent reference issues
        for (int i = 0; i < mainSize; i++) {
            mainInventory[i] = inventory.getItem(i).copy();
        }
        
        for (int i = 0; i < inventory.armor.size(); i++) {
            armorInventory[i] = inventory.armor.get(i).copy();
        }
        
        for (int i = 0; i < inventory.offhand.size(); i++) {
            offhandInventory[i] = inventory.offhand.get(i).copy();
        }
        
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            enderChest[i] = player.getEnderChestInventory().getItem(i).copy();
        }
        
        return new InventorySnapshot(mainInventory, armorInventory, offhandInventory, enderChest);
    }
    
    private boolean inventoriesMatch(InventorySnapshot snapshot, InventorySnapshot current) {
        // Check main inventory
        if (snapshot.getMainInventory().length != current.getMainInventory().length) {
            return false;
        }
        
        for (int i = 0; i < snapshot.getMainInventory().length; i++) {
            if (!ItemStack.matches(snapshot.getMainInventory()[i], current.getMainInventory()[i])) {
                return false;
            }
        }
        
        // Check armor
        if (snapshot.getArmorInventory().length != current.getArmorInventory().length) {
            return false;
        }
        
        for (int i = 0; i < snapshot.getArmorInventory().length; i++) {
            if (!ItemStack.matches(snapshot.getArmorInventory()[i], current.getArmorInventory()[i])) {
                return false;
            }
        }
        
        // Check offhand
        if (snapshot.getOffhandInventory().length != current.getOffhandInventory().length) {
            return false;
        }
        
        for (int i = 0; i < snapshot.getOffhandInventory().length; i++) {
            if (!ItemStack.matches(snapshot.getOffhandInventory()[i], current.getOffhandInventory()[i])) {
                return false;
            }
        }
        
        // Check enderChest
        if (snapshot.getEnderChest().length != current.getEnderChest().length) {
            return false;
        }
        
        for (int i = 0; i < snapshot.getEnderChest().length; i++) {
            if (!ItemStack.matches(snapshot.getEnderChest()[i], current.getEnderChest()[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    private void applyNewInventory(ServerPlayer player, InventoryData newInventory) {
        // Clear current inventory
        player.getInventory().clearContent();
        
        // Apply new main inventory
        for (int i = 0; i < newInventory.getMainInventory().size() && i < player.getInventory().getContainerSize(); i++) {
            ItemStackData itemData = newInventory.getMainInventory().get(i);
            player.getInventory().setItem(i, deserializeItemStack(itemData));
        }
        
        // Apply new armor
        for (int i = 0; i < newInventory.getArmorInventory().size() && i < 4; i++) {
            ItemStackData itemData = newInventory.getArmorInventory().get(i);
            player.getInventory().armor.set(i, deserializeItemStack(itemData));
        }
        
        // Apply new offhand
        if (!newInventory.getOffhandInventory().isEmpty()) {
            ItemStackData itemData = newInventory.getOffhandInventory().get(0);
            player.getInventory().offhand.set(0, deserializeItemStack(itemData));
        }
        
        // Apply new enderchest
        player.getEnderChestInventory().clearContent();
        for (int i = 0; i < newInventory.getEnderChest().size() && i < player.getEnderChestInventory().getContainerSize(); i++) {
            ItemStackData itemData = newInventory.getEnderChest().get(i);
            player.getEnderChestInventory().setItem(i, deserializeItemStack(itemData));
        }
    }
    
    private InventoryData serializeInventory(Player player) {
        Inventory inventory = player.getInventory();
        List<ItemStackData> mainInventory = new ArrayList<>();
        List<ItemStackData> armorInventory = new ArrayList<>();
        List<ItemStackData> offhandInventory = new ArrayList<>();
        List<ItemStackData> enderChest = new ArrayList<>();
        
        // Serialize main inventory
        for (int i = 0; i < inventory.items.size(); i++) {
            mainInventory.add(serializeItemStack(inventory.items.get(i)));
        }
        
        // Serialize armor
        for (int i = 0; i < inventory.armor.size(); i++) {
            armorInventory.add(serializeItemStack(inventory.armor.get(i)));
        }
        
        // Serialize offhand
        for (int i = 0; i < inventory.offhand.size(); i++) {
            offhandInventory.add(serializeItemStack(inventory.offhand.get(i)));
        }
        
        // Serialize enderchest
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            enderChest.add(serializeItemStack(player.getEnderChestInventory().getItem(i)));
        }
        
        return new InventoryData(mainInventory, armorInventory, offhandInventory, enderChest);
    }
    
    private ItemStackData serializeItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ItemStackData("minecraft:air", 0, null);
        }
        
        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        int count = stack.getCount();
        Map<String, Object> nbt = null;
        
        // Serialize NBT data if present
        if (stack.hasTag()) {
            // Convert NBT to a map representation
            nbt = new HashMap<>();
            
            // Convert CompoundTag to a serializable map format
            convertNbtToMap(stack.getTag(), nbt);
        }
        
        return new ItemStackData(itemId, count, nbt);
    }
    
    private void convertNbtToMap(net.minecraft.nbt.CompoundTag tag, Map<String, Object> map) {
        for (String key : tag.getAllKeys()) {
            net.minecraft.nbt.Tag nbtElement = tag.get(key);
            if (nbtElement == null) continue;
            
            switch (nbtElement.getId()) {
                case net.minecraft.nbt.Tag.TAG_COMPOUND:
                    Map<String, Object> nestedMap = new HashMap<>();
                    convertNbtToMap((net.minecraft.nbt.CompoundTag) nbtElement, nestedMap);
                    map.put(key, nestedMap);
                    break;
                case net.minecraft.nbt.Tag.TAG_LIST:
                    net.minecraft.nbt.ListTag listTag = (net.minecraft.nbt.ListTag) nbtElement;
                    List<Object> list = new ArrayList<>();
                    for (int i = 0; i < listTag.size(); i++) {
                        if (listTag.getElementType() == net.minecraft.nbt.Tag.TAG_COMPOUND) {
                            Map<String, Object> listItemMap = new HashMap<>();
                            convertNbtToMap(listTag.getCompound(i), listItemMap);
                            list.add(listItemMap);
                        } else {
                            // For primitive types in a list, add their string representation
                            list.add(listTag.get(i).toString());
                        }
                    }
                    map.put(key, list);
                    break;
                default:
                    // For primitive types (byte, short, int, long, float, double, string, etc.)
                    // We store their string representation
                    map.put(key, nbtElement.toString());
                    break;
            }
        }
    }
    
    private ItemStack deserializeItemStack(ItemStackData data) {
        if (data == null || "minecraft:air".equals(data.getItemId()) || data.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(ResourceLocation.tryParse(data.getItemId()));
        
        if (item == null) {
            LOGGER.warn("Unknown item ID: " + data.getItemId());
            return ItemStack.EMPTY;
        }
        
        ItemStack stack = new ItemStack(item, data.getCount());
        
        // Handle NBT data if present
        if (data.getNbt() != null && !data.getNbt().isEmpty()) {
            net.minecraft.nbt.CompoundTag nbtTag = new net.minecraft.nbt.CompoundTag();
            convertMapToNbt(data.getNbt(), nbtTag);
            stack.setTag(nbtTag);
        }
        
        return stack;
    }
    
    private void convertMapToNbt(Map<String, Object> map, net.minecraft.nbt.CompoundTag compoundTag) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null) continue;
            
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                net.minecraft.nbt.CompoundTag nestedTag = new net.minecraft.nbt.CompoundTag();
                convertMapToNbt(nestedMap, nestedTag);
                compoundTag.put(key, nestedTag);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                
                if (!list.isEmpty()) {
                    if (list.get(0) instanceof Map) {
                        // List of compounds
                        net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
                        for (Object item : list) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> itemMap = (Map<String, Object>) item;
                                net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
                                convertMapToNbt(itemMap, itemTag);
                                listTag.add(itemTag);
                            }
                        }
                        compoundTag.put(key, listTag);
                    } else {
                        // List of strings or other primitives
                        net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
                        for (Object item : list) {
                            // We'll treat all list items as strings for simplicity
                            listTag.add(net.minecraft.nbt.StringTag.valueOf(item.toString()));
                        }
                        compoundTag.put(key, listTag);
                    }
                }
            } else {
                // For primitives, store as string for simplicity
                // In a real implementation, you'd need to determine the actual type
                // For simplicity, we'll store most primitives as strings
                compoundTag.putString(key, value.toString());
            }
        }
    }
    
    private String generateInventoryDiff(InventorySnapshot original, InventoryData newInventory) {
        StringBuilder diff = new StringBuilder();
        
        // Compare main inventory
        diff.append("Main Inventory Changes:\n");
        boolean hasMainChanges = false;
        
        for (int i = 0; i < original.getMainInventory().length && i < newInventory.getMainInventory().size(); i++) {
            ItemStack origStack = original.getMainInventory()[i];
            ItemStackData newStack = newInventory.getMainInventory().get(i);
            
            if (!compareItemStacks(origStack, newStack)) {
                hasMainChanges = true;
                diff.append("Slot ").append(i).append(": ");
                
                if (origStack.isEmpty() && !("minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0)) {
                    diff.append("+ ").append(newStack.getCount()).append("x ").append(newStack.getItemId()).append("\n");
                } else if (!origStack.isEmpty() && ("minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0)) {
                    diff.append("- ").append(origStack.getCount()).append("x ").append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(origStack.getItem())).append("\n");
                } else if (!origStack.isEmpty() && !("minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0)) {
                    diff.append("Changed: ").append(origStack.getCount()).append("x ").append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(origStack.getItem()))
                            .append(" -> ").append(newStack.getCount()).append("x ").append(newStack.getItemId()).append("\n");
                }
            }
        }
        
        if (!hasMainChanges) {
            diff.append("No changes\n");
        }
        
        // For simplicity, just mention if there are armor/offhand changes rather than detailing them
        diff.append("\nArmor & Offhand: ");
        boolean hasArmorChanges = false;
        
        for (int i = 0; i < original.getArmorInventory().length && i < newInventory.getArmorInventory().size(); i++) {
            if (!compareItemStacks(original.getArmorInventory()[i], newInventory.getArmorInventory().get(i))) {
                hasArmorChanges = true;
                break;
            }
        }
        
        for (int i = 0; i < original.getOffhandInventory().length && i < newInventory.getOffhandInventory().size(); i++) {
            if (!compareItemStacks(original.getOffhandInventory()[i], newInventory.getOffhandInventory().get(i))) {
                hasArmorChanges = true;
                break;
            }
        }
        
        if (hasArmorChanges) {
            diff.append("Changes detected");
        } else {
            diff.append("No changes");
        }
        
        // Add enderchest changes information
        diff.append("\n\nEnder Chest: ");
        boolean hasEnderChestChanges = false;
        
        for (int i = 0; i < original.getEnderChest().length && i < newInventory.getEnderChest().size(); i++) {
            if (!compareItemStacks(original.getEnderChest()[i], newInventory.getEnderChest().get(i))) {
                hasEnderChestChanges = true;
                break;
            }
        }
        
        if (hasEnderChestChanges) {
            diff.append("Changes detected");
        } else {
            diff.append("No changes");
        }
        
        return diff.toString();
    }
    
    private boolean compareItemStacks(ItemStack original, ItemStackData newStack) {
        if (original.isEmpty() && ("minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0)) {
            return true;
        }
        
        if (original.isEmpty() || "minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0) {
            return false;
        }
        
        String origItemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(original.getItem()).toString();
        int origCount = original.getCount();
        
        return origItemId.equals(newStack.getItemId()) && origCount == newStack.getCount();
    }
    
    // Data classes
    
    public static class InventorySnapshot {
        private final ItemStack[] mainInventory;
        private final ItemStack[] armorInventory;
        private final ItemStack[] offhandInventory;
        private final ItemStack[] enderChest;
        
        public InventorySnapshot(ItemStack[] mainInventory, ItemStack[] armorInventory, ItemStack[] offhandInventory, ItemStack[] enderChest) {
            this.mainInventory = mainInventory;
            this.armorInventory = armorInventory;
            this.offhandInventory = offhandInventory;
            this.enderChest = enderChest;
        }
        
        public ItemStack[] getMainInventory() {
            return mainInventory;
        }
        
        public ItemStack[] getArmorInventory() {
            return armorInventory;
        }
        
        public ItemStack[] getOffhandInventory() {
            return offhandInventory;
        }
        
        public ItemStack[] getEnderChest() {
            return enderChest;
        }
    }
    
    public static class ItemStackData {
        private final String itemId;
        private final int count;
        private final Map<String, Object> nbt;
        
        public ItemStackData(String itemId, int count, Map<String, Object> nbt) {
            this.itemId = itemId;
            this.count = count;
            this.nbt = nbt;
        }
        
        public String getItemId() {
            return itemId;
        }
        
        public int getCount() {
            return count;
        }
        
        public Map<String, Object> getNbt() {
            return nbt;
        }
    }
    
    public static class InventoryData {
        private final List<ItemStackData> mainInventory;
        private final List<ItemStackData> armorInventory;
        private final List<ItemStackData> offhandInventory;
        private final List<ItemStackData> enderChest;
        
        public InventoryData(List<ItemStackData> mainInventory, List<ItemStackData> armorInventory, 
                            List<ItemStackData> offhandInventory, List<ItemStackData> enderChest) {
            this.mainInventory = mainInventory;
            this.armorInventory = armorInventory;
            this.offhandInventory = offhandInventory;
            this.enderChest = enderChest;
        }
        
        public List<ItemStackData> getMainInventory() {
            return mainInventory;
        }
        
        public List<ItemStackData> getArmorInventory() {
            return armorInventory;
        }
        
        public List<ItemStackData> getOffhandInventory() {
            return offhandInventory;
        }
        
        public List<ItemStackData> getEnderChest() {
            return enderChest;
        }
    }
    
    public static class ShopProcess {
        private final UUID playerId;
        private final UUID processId;
        private final InventorySnapshot originalInventory;
        private String webLink;
        private String twoFactorCode;
        private InventoryData newInventory;
        
        public ShopProcess(UUID playerId, UUID processId, InventorySnapshot originalInventory) {
            this.playerId = playerId;
            this.processId = processId;
            this.originalInventory = originalInventory;
        }
        
        public UUID getPlayerId() {
            return playerId;
        }
        
        public UUID getProcessId() {
            return processId;
        }
        
        public InventorySnapshot getOriginalInventory() {
            return originalInventory;
        }
        
        public String getWebLink() {
            return webLink;
        }
        
        public void setWebLink(String webLink) {
            this.webLink = webLink;
        }
        
        public String getTwoFactorCode() {
            return twoFactorCode;
        }
        
        public void setTwoFactorCode(String twoFactorCode) {
            this.twoFactorCode = twoFactorCode;
        }
        
        public InventoryData getNewInventory() {
            return newInventory;
        }
        
        public void setNewInventory(InventoryData newInventory) {
            this.newInventory = newInventory;
        }
    }
    
    public static class ShopResponse {
        private String link;
        private String twoFactorCode;
        private String uuid;
        
        public String getLink() {
            return link;
        }
        
        public String getTwoFactorCode() {
            return twoFactorCode;
        }
        
        public String getUuid() {
            return uuid;
        }
    }
    
    public static class ShopFinishResponse {
        private Map<String, Object> inventories;
        
        public Map<String, Object> getInventories() {
            return inventories;
        }
        
        public InventoryData getInventoryData() {
            if (inventories == null) {
                return null;
            }
            
            try {
                @SuppressWarnings("unchecked")
                List<ItemStackData> mainInventory = convertToItemStackDataList((List<Map<String, Object>>) inventories.get("mainInventory"));
                @SuppressWarnings("unchecked")
                List<ItemStackData> armorInventory = convertToItemStackDataList((List<Map<String, Object>>) inventories.get("armorInventory"));
                @SuppressWarnings("unchecked")
                List<ItemStackData> offhandInventory = convertToItemStackDataList((List<Map<String, Object>>) inventories.get("offhandInventory"));
                @SuppressWarnings("unchecked")
                List<ItemStackData> enderChestInventory = convertToItemStackDataList((List<Map<String, Object>>) inventories.get("enderChestInventory"));
                
                return new InventoryData(mainInventory, armorInventory, offhandInventory, enderChestInventory);
            } catch (Exception e) {
                LOGGER.error("Error parsing inventory data from response", e);
                return null;
            }
        }
        
        private List<ItemStackData> convertToItemStackDataList(List<Map<String, Object>> items) {
            if (items == null) {
                return new ArrayList<>();
            }
            
            List<ItemStackData> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String itemId = (String) item.get("itemId");
                int count = ((Number) item.get("count")).intValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> nbt = (Map<String, Object>) item.get("nbt");
                result.add(new ItemStackData(itemId, count, nbt));
            }
            return result;
        }
    }

    /**
     * Removes money items from the player's inventory
     * @param player The player to remove money items from
     * @return The total number of money items removed
     */
    private int removeMoneyItems(ServerPlayer player) {
        int totalRemoved = 0;
        Inventory inventory = player.getInventory();
        
        // Process main inventory
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && Config.moneyItems.contains(stack.getItem())) {
                totalRemoved += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        
        // Process ender chest if needed
        Container enderChest = player.getEnderChestInventory();
        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack stack = enderChest.getItem(i);
            if (!stack.isEmpty() && Config.moneyItems.contains(stack.getItem())) {
                totalRemoved += stack.getCount();
                enderChest.setItem(i, ItemStack.EMPTY);
            }
        }
        
        return totalRemoved;
    }
}
