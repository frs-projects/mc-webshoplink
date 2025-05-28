package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
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
                .then(Commands.argument("type", net.minecraft.commands.arguments.StringArgumentType.string())
                    .executes(context -> {
                        return executeShopCommand(context.getSource(), 
                            net.minecraft.commands.arguments.StringArgumentType.getString(context, "type"));
                    })
                )
        );
        
        // Register "shopFinish" command
        event.getDispatcher().register(
            Commands.literal("shopFinish")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", net.minecraft.commands.arguments.StringArgumentType.string())
                    .executes(context -> {
                        return executeShopFinishCommand(context.getSource(), 
                            net.minecraft.commands.arguments.StringArgumentType.getString(context, "uuid"));
                    })
                )
        );
        
        // Register "confirmFinish" command
        event.getDispatcher().register(
            Commands.literal("confirmFinish")
                .requires(source -> source.hasPermission(0)) // Anyone can use
                .then(Commands.argument("uuid", net.minecraft.commands.arguments.StringArgumentType.string())
                    .executes(context -> {
                        return executeConfirmFinishCommand(context.getSource(), 
                            net.minecraft.commands.arguments.StringArgumentType.getString(context, "uuid"));
                    })
                )
        );
    }
    
    private int executeShopCommand(CommandSourceStack source, String type) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Create a unique identifier for this shop process
        UUID processId = UUID.randomUUID();
        
        // Create a shop process and save the player's current inventory
        ShopProcess shopProcess = new ShopProcess(player.getUUID(), processId, captureInventory(player));
        ACTIVE_SHOP_PROCESSES.put(processId, shopProcess);
        
        // Create request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", player.getStringUUID());
        payload.put("processId", processId.toString());
        payload.put("type", type);
        payload.put("inventory", serializeInventory(player.getInventory()));
        payload.put("enderChest", serializeEnderChest(player));
        
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
                    
                    // Store the response data in the shop process
                    shopProcess.setWebLink(shopResponse.getLink());
                    shopProcess.setTwoFactorCode(shopResponse.getTwoFactorCode());
                    
                    // Send the response to the player
                    Component linkComponent = Component.literal("Click the following link to open the shop: ")
                            .append(Component.literal(shopResponse.getLink())
                                    .withStyle(Style.EMPTY
                                            .withColor(net.minecraft.ChatFormatting.BLUE)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, shopResponse.getLink()))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open shop")))));
                    
                    Component codeComponent = Component.literal("Use this Code to verify your identity: ")
                            .append(Component.literal(shopResponse.getTwoFactorCode())
                                    .withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GREEN)));
                    
                    player.sendSystemMessage(linkComponent);
                    player.sendSystemMessage(codeComponent);
                    
                } catch (Exception e) {
                    LOGGER.error("Error processing shop response", e);
                    player.sendSystemMessage(Component.literal("Error processing shop response. Please try again later."));
                    ACTIVE_SHOP_PROCESSES.remove(processId);
                }
            } else {
                LOGGER.error("Error from shop API: " + response.statusCode() + " - " + response.body());
                player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
                ACTIVE_SHOP_PROCESSES.remove(processId);
            }
        }).exceptionally(e -> {
            LOGGER.error("Error connecting to shop API", e);
            player.sendSystemMessage(Component.literal("Error connecting to shop. Please try again later."));
            ACTIVE_SHOP_PROCESSES.remove(processId);
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
            UUID processId = UUID.fromString(uuidString);
            ShopProcess shopProcess = ACTIVE_SHOP_PROCESSES.get(processId);
            
            if (shopProcess == null || !shopProcess.getPlayerId().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("No active shopping process found with that ID."));
                return 0;
            }
            
            // Create request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("playerId", player.getStringUUID());
            payload.put("processId", processId.toString());
            
            // Send HTTP request
            String jsonPayload = GSON.toJson(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Config.apiBaseUrl + Config.shopFinishEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            
            // Process the request asynchronously
            CompletableFuture<HttpResponse<String>> futureResponse = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            futureResponse.thenAccept(response -> {
                if (response.statusCode() == 200) {
                    try {
                        // Parse the response
                        ShopFinishResponse finishResponse = GSON.fromJson(response.body(), ShopFinishResponse.class);
                        
                        // Store the new inventory in the shop process
                        shopProcess.setNewInventory(finishResponse.getInventory());
                        shopProcess.setNewEnderChest(finishResponse.getEnderChest());
                        
                        // Generate and show a diff to the player
                        String diff = generateInventoryDiff(shopProcess.getOriginalInventory(), finishResponse.getInventory());
                        
                        // Create the confirmation message with a clickable button
                        Component confirmComponent = Component.literal("Your shopping cart contains the following changes:\n")
                                .append(Component.literal(diff).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)))
                                .append(Component.literal("\n\nClick here to confirm and apply these changes")
                                        .withStyle(Style.EMPTY
                                                .withColor(net.minecraft.ChatFormatting.GREEN)
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
        ItemStack[] mainInventory = new ItemStack[player.getInventory().items.size()];
        ItemStack[] armorInventory = new ItemStack[player.getInventory().armor.size()];
        ItemStack[] offhandInventory = new ItemStack[player.getInventory().offhand.size()];
        ItemStack[] enderChest = new ItemStack[player.getEnderChestInventory().items.size()];
        
        // Copy all items to prevent reference issues
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            mainInventory[i] = player.getInventory().items.get(i).copy();
        }
        
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            armorInventory[i] = player.getInventory().armor.get(i).copy();
        }
        
        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            offhandInventory[i] = player.getInventory().offhand.get(i).copy();
        }
        
        for (int i = 0; i < player.getEnderChestInventory().items.size(); i++) {
            enderChest[i] = player.getEnderChestInventory().items.get(i).copy();
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
        
        return true;
    }
    
    private void applyNewInventory(ServerPlayer player, InventoryData newInventory) {
        // Clear current inventory
        player.getInventory().clearContent();
        
        // Apply new main inventory
        for (int i = 0; i < newInventory.getMainInventory().size() && i < player.getInventory().items.size(); i++) {
            ItemStackData itemData = newInventory.getMainInventory().get(i);
            player.getInventory().items.set(i, deserializeItemStack(itemData));
        }
        
        // Apply new armor
        for (int i = 0; i < newInventory.getArmorInventory().size() && i < player.getInventory().armor.size(); i++) {
            ItemStackData itemData = newInventory.getArmorInventory().get(i);
            player.getInventory().armor.set(i, deserializeItemStack(itemData));
        }
        
        // Apply new offhand
        for (int i = 0; i < newInventory.getOffhandInventory().size() && i < player.getInventory().offhand.size(); i++) {
            ItemStackData itemData = newInventory.getOffhandInventory().get(i);
            player.getInventory().offhand.set(i, deserializeItemStack(itemData));
        }
        
        // Apply new enderchest if provided
        if (newInventory.getEnderChest() != null) {
            player.getEnderChestInventory().clearContent();
            for (int i = 0; i < newInventory.getEnderChest().size() && i < player.getEnderChestInventory().items.size(); i++) {
                ItemStackData itemData = newInventory.getEnderChest().get(i);
                player.getEnderChestInventory().items.set(i, deserializeItemStack(itemData));
            }
        }
    }
    
    private InventoryData serializeInventory(Inventory inventory) {
        List<ItemStackData> mainInventory = new ArrayList<>();
        List<ItemStackData> armorInventory = new ArrayList<>();
        List<ItemStackData> offhandInventory = new ArrayList<>();
        
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
        
        return new InventoryData(mainInventory, armorInventory, offhandInventory, null);
    }
    
    private List<ItemStackData> serializeEnderChest(Player player) {
        List<ItemStackData> enderChest = new ArrayList<>();
        
        // Serialize enderchest
        for (int i = 0; i < player.getEnderChestInventory().items.size(); i++) {
            enderChest.add(serializeItemStack(player.getEnderChestInventory().items.get(i)));
        }
        
        return enderChest;
    }
    
    private ItemStackData serializeItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ItemStackData("minecraft:air", 0, null);
        }
        
        String itemId = stack.getItem().getRegistryName().toString();
        int count = stack.getCount();
        Map<String, Object> nbt = null;
        
        // Serialize NBT data if present
        if (stack.hasTag()) {
            // Convert NBT to a map representation
            nbt = new HashMap<>();
            // Basic implementation - in a real mod you'd need to recursively convert all NBT
            nbt.put("tag", stack.getTag().toString());
        }
        
        return new ItemStackData(itemId, count, nbt);
    }
    
    private ItemStack deserializeItemStack(ItemStackData data) {
        if (data == null || "minecraft:air".equals(data.getItemId()) || data.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new net.minecraft.resources.ResourceLocation(data.getItemId()));
        
        if (item == null) {
            LOGGER.warn("Unknown item ID: " + data.getItemId());
            return ItemStack.EMPTY;
        }
        
        ItemStack stack = new ItemStack(item, data.getCount());
        
        // TODO: Handle NBT data properly if needed
        // This would require a more complex implementation
        
        return stack;
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
                    diff.append("- ").append(origStack.getCount()).append("x ").append(origStack.getItem().getRegistryName()).append("\n");
                } else if (!origStack.isEmpty() && !("minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0)) {
                    diff.append("Changed: ").append(origStack.getCount()).append("x ").append(origStack.getItem().getRegistryName())
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
        
        return diff.toString();
    }
    
    private boolean compareItemStacks(ItemStack original, ItemStackData newStack) {
        if (original.isEmpty() && ("minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0)) {
            return true;
        }
        
        if (original.isEmpty() || "minecraft:air".equals(newStack.getItemId()) || newStack.getCount() <= 0) {
            return false;
        }
        
        String origItemId = original.getItem().getRegistryName().toString();
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
        private List<ItemStackData> newEnderChest;
        
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
        
        public List<ItemStackData> getNewEnderChest() {
            return newEnderChest;
        }
        
        public void setNewEnderChest(List<ItemStackData> newEnderChest) {
            this.newEnderChest = newEnderChest;
        }
    }
    
    public static class ShopResponse {
        private String link;
        private String twoFactorCode;
        private String processId;
        
        public String getLink() {
            return link;
        }
        
        public String getTwoFactorCode() {
            return twoFactorCode;
        }
        
        public String getProcessId() {
            return processId;
        }
    }
    
    public static class ShopFinishResponse {
        private String processId;
        private InventoryData inventory;
        private List<ItemStackData> enderChest;
        
        public String getProcessId() {
            return processId;
        }
        
        public InventoryData getInventory() {
            return inventory;
        }
        
        public List<ItemStackData> getEnderChest() {
            return enderChest;
        }
    }
}
