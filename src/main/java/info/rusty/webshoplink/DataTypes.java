package info.rusty.webshoplink;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Contains all data classes used in the Webshoplink mod
 */
public class DataTypes {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Represents a snapshot of a player's inventory
     */
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

    /**
     * Represents a shop process
     */
    public static class ShopProcess {
        private final UUID playerId;
        private final UUID processId;
        private final InventorySnapshot originalInventory;
        private final String shopLabel;
        private String webLink;
        private String twoFactorCode;
        private InventoryData newInventory;
        private ContainerData newEchest;

        public ShopProcess(UUID playerId, UUID processId, InventorySnapshot originalInventory, String shopLabel) {
            this.playerId = playerId;
            this.processId = processId;
            this.originalInventory = originalInventory;
            this.shopLabel = shopLabel;
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

        public String getShopLabel() {
            return shopLabel;
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

        public void setNewEchest(ContainerData newEchest) {
            this.newEchest = newEchest;
        }

        public ContainerData getNewEchest() {
            return newEchest;
        }
    }

    /**
     * Response from the shop API when initiating a shop session
     */
    public static class ShopResponse {
        private String uuid;
        private String link;
        private String twoFactorCode;
        private String errorMessage;
        
        public String getUuid() {
            return uuid;
        }
        
        public void setUuid(String uuid) {
            this.uuid = uuid;
        }
        
        public String getLink() {
            return link;
        }
        
        public void setLink(String link) {
            this.link = link;
        }
        
        public String getTwoFactorCode() {
            return twoFactorCode;
        }
        
        public void setTwoFactorCode(String twoFactorCode) {
            this.twoFactorCode = twoFactorCode;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }

    /**
     * Response from the shop API when finishing a shop session
     */
    public static class InventoryList {
        private InventoryData inventory;
        private ContainerData echest;

        public InventoryData getInventoryData()
        {
            return inventory;
        }

        public ContainerData getEnderChestData()
        {
            return echest;
        }

        public void setInventoryFromPlayer(Inventory playerInventory) {
            if (playerInventory == null) return;
            
            this.inventory = new InventoryData();
            Map<Integer, ItemData> itemMap = new java.util.HashMap<>();
            
            // Process main inventory
            for (int i = 0; i < playerInventory.getContainerSize(); i++) {
                ItemStack stack = playerInventory.getItem(i);
                if (!stack.isEmpty()) {
                    ItemData itemData = createItemData(stack);
                    itemMap.put(i, itemData);
                }
            }
            
            this.inventory.size = playerInventory.getContainerSize();
            this.inventory.items = itemMap;
        }

        public void setEchestFromPlayer(Container playerEchest) {
            if (playerEchest == null) return;
            
            this.echest = new ContainerData();
            Map<Integer, ItemData> itemMap = new java.util.HashMap<>();
            
            // Process ender chest inventory
            for (int i = 0; i < playerEchest.getContainerSize(); i++) {
                ItemStack stack = playerEchest.getItem(i);
                if (!stack.isEmpty()) {
                    ItemData itemData = createItemData(stack);
                    itemMap.put(i, itemData);
                }
            }
            
            this.echest.size = playerEchest.getContainerSize();
            this.echest.items = itemMap;
        }
          // Helper method to create ItemData from ItemStack
        private ItemData createItemData(ItemStack stack) {
            ItemData itemData = new ItemData();            
            try {
                java.lang.reflect.Field itemIdField = ItemData.class.getDeclaredField("itemId");
                java.lang.reflect.Field countField = ItemData.class.getDeclaredField("count");
                java.lang.reflect.Field nbtField = ItemData.class.getDeclaredField("nbt");
                
                itemIdField.setAccessible(true);
                countField.setAccessible(true);
                nbtField.setAccessible(true);
                
                // Get the id of the item using built-in Minecraft method
                net.minecraft.resources.ResourceLocation itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                itemIdField.set(itemData, itemKey.toString());
                countField.set(itemData, stack.getCount());
                
                // Convert NBT data to a proper JSON structure if present
                if (stack.hasTag()) {
                    // Log the original NBT
                    DebugLogger.log("Serializing NBT for item " + itemKey.toString(), Config.DebugVerbosity.DEFAULT);
                    NbtDebugUtils.logItemStackNbt(stack, "Original item before serialization");
                    
                    // Use our custom NBT serializer to convert to JsonObject
                    JsonObject nbtJson = (JsonObject) NbtSerializer.serializeNbt(stack.getTag());
                    nbtField.set(itemData, nbtJson);
                    
                    // Log the serialized NBT JSON
                    DebugLogger.log("Serialized NBT to JSON for item " + itemKey.toString(), Config.DebugVerbosity.DEFAULT);
                    NbtDebugUtils.logJsonNbt(nbtJson, "Serialized NBT JSON");
                    
                    // Test round-trip conversion
                    try {
                        CompoundTag roundTrip = NbtSerializer.CompoundTagAdapter.parseJsonToCompoundTag(nbtJson);
                        boolean tagsEqual = roundTrip.equals(stack.getTag());
                        DebugLogger.log("Round-trip NBT conversion test: " + (tagsEqual ? "PASSED" : "FAILED"), Config.DebugVerbosity.DEFAULT);
                        if (!tagsEqual) {
                            DebugLogger.log("Original tag: " + stack.getTag(), Config.DebugVerbosity.DEFAULT);
                            DebugLogger.log("Round-trip tag: " + roundTrip, Config.DebugVerbosity.DEFAULT);
                        }
                    } catch (Exception e) {
                        DebugLogger.logError("Failed round-trip NBT test: " + e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error creating ItemData from ItemStack", e);
            }
            
            return itemData;
        }
    }

    public static class InventoryData {
        private Integer size;
        private Map<Integer, ItemData> items;

        public Integer getSize() {
            return size;
        }

        public Map<Integer, ItemData> getItems() {
            return items;
        }
        
        public ItemData getItem(Integer index) {
            return items.get(index);
        }
    }

    public static class ContainerData {
        private Integer size;
        private Map<Integer, ItemData> items;

        public Integer getSize() {
            return size;
        }

        public Map<Integer, ItemData> getItems() {
            return items;
        }
        
        public ItemData getItem(Integer index) {
            return items.get(index);
        }
    }
    
    public static class ItemData {
        private String itemId;
        private Integer count;
        private JsonObject nbt;

        public String getItemId() {
            return itemId;
        }

        public Integer getCount() {
            return count;
        }

        public JsonObject getNbt() {
            return nbt;
        }
        
        public ItemStack getItemStackData() {
            try {
                // Parse the item id to get the correct item
                String[] parts = itemId.split(":", 2);
                if (parts.length != 2) {
                    LOGGER.error("Invalid item ID format: {}", itemId);
                    return ItemStack.EMPTY;
                }
                
                net.minecraft.resources.ResourceLocation resourceLocation = new net.minecraft.resources.ResourceLocation(parts[0], parts[1]);
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resourceLocation);
                
                if (item == net.minecraft.world.item.Items.AIR) {
                    LOGGER.error("Could not find item with ID: {}", itemId);
                    return ItemStack.EMPTY;
                }
                
                // Create the item stack with the correct count
                ItemStack stack = new ItemStack(item, count != null ? count : 1);
                
                // If NBT data is present, try to parse and apply it
                if (nbt != null) {
                    try {
                        // Log the NBT JSON before conversion for debugging
                        DebugLogger.log("Converting NBT JSON to CompoundTag for item " + itemId, Config.DebugVerbosity.DEFAULT);
                        NbtDebugUtils.logJsonNbt(nbt, "Pre-conversion NBT JSON");
                        
                        // Use the NbtSerializer to properly convert the JsonObject to a CompoundTag
                        CompoundTag nbtData = NbtSerializer.CompoundTagAdapter.parseJsonToCompoundTag(nbt);
                        stack.setTag(nbtData);
                        
                        // Log for debugging
                        DebugLogger.log("Applied NBT data to item " + itemId + ": " + nbtData, Config.DebugVerbosity.DEFAULT);
                        NbtDebugUtils.logItemStackNbt(stack, "Post-application ItemStack");
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse NBT data for item {}: {}", itemId, e.getMessage());
                        e.printStackTrace(); // Add stack trace for better debugging
                    }
                }
                
                return stack;
            } catch (Exception e) {
                LOGGER.error("Error creating ItemStack from ItemData: {}", e.getMessage());
                return ItemStack.EMPTY;
            }
        }
    }
    
    /**
     * Represents an inventory diff between two snapshots
     */
    public static class InventoryDiff {
        private final java.util.List<InventoryChange> added;
        private final java.util.List<InventoryChange> removed;
        
        public InventoryDiff() {
            this.added = new java.util.ArrayList<>();
            this.removed = new java.util.ArrayList<>();
        }
        
        public java.util.List<InventoryChange> getAdded() {
            return added;
        }
        
        public java.util.List<InventoryChange> getRemoved() {
            return removed;
        }
        
        public void addItem(String itemId, int count) {
            if (count > 0) {
                added.add(new InventoryChange(itemId, count));
            }
        }
        
        public void removeItem(String itemId, int count) {
            if (count > 0) {
                removed.add(new InventoryChange(itemId, count));
            }
        }
        
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }
    
    /**
     * Represents a single change in the inventory
     */
    public static class InventoryChange {
        private final String itemId;
        private final int count;
        private final String formattedName;
        
        public InventoryChange(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
            this.formattedName = formatItemId(itemId);
        }
        
        public String getItemId() {
            return itemId;
        }
        
        public int getCount() {
            return count;
        }
        
        public String getFormattedName() {
            return formattedName;
        }
        
        /**
         * Helper method to format item IDs for display
         */
        public static String formatItemId(String itemId) {
            // Keep the full itemId like "minecraft:iron_ingot"
            String displayName = itemId;
            
            // Extract just the name part (after the colon)
            if (displayName.contains(":")) {
                displayName = displayName.substring(displayName.indexOf(":") + 1);
            }
            
            String[] parts = displayName.split("_");
            StringBuilder formatted = new StringBuilder();
            
            for (String part : parts) {
                if (!part.isEmpty()) {
                    formatted.append(Character.toUpperCase(part.charAt(0)));
                    formatted.append(part.substring(1));
                    formatted.append(" ");
                }
            }
            
            return formatted.toString().trim();
        }
    }
}
