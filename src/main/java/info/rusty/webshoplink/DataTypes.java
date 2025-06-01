package info.rusty.webshoplink;

import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * Represents a serializable item stack
     */
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
        
        @Override
        public String toString() {
            return count + "x " + itemId + (nbt != null && !nbt.isEmpty() ? " with NBT" : "");
        }
    }

    /**
     * Represents serialized inventory data
     */
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
    }

    /**
     * Response from the shop API when initiating a shop session
     */
    public static class ShopResponse {
        private String uuid;
        private String link;
        private String twoFactorCode;
        
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
        }    }
    
    /**
     * Response from the shop API when finishing a shop session
     */
    public static class ShopFinishResponse {
        private Map<String, Object> inventories;
        private InventoryData inventoryData;
        private static final Logger LOGGER = LogUtils.getLogger();
        
        public Map<String, Object> getInventories() {
            return inventories;
        }
          public void setInventories(Map<String, Object> inventories) {
            this.inventories = inventories;
            
            // If inventoryData is null but inventories is available, try to convert
            if (inventoryData == null && inventories != null) {
                try {
                    convertInventoriesToInventoryData();
                } catch (Exception e) {
                    LOGGER.error("Error converting inventories to inventoryData", e);
                }
            }
        }
          private void convertInventoriesToInventoryData() {
            if (inventories == null) return;
            
            try {
                LOGGER.info("Converting inventories to inventoryData: " + inventories);
                
                // The inventories structure is different from what we expected
                // It's a map with a nested structure: {"inventories": {"mainInventory": [...], ...}}
                // We need to access the inner map first
                Map<String, Object> inventoriesMap = inventories;
                
                // Extract the inventory lists from the map
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> mainInv = (List<Map<String, Object>>) inventoriesMap.get("mainInventory");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> armorInv = (List<Map<String, Object>>) inventoriesMap.get("armorInventory");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> offhandInv = (List<Map<String, Object>>) inventoriesMap.get("offhandInventory");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> enderChestInv = (List<Map<String, Object>>) inventoriesMap.get("enderChestInventory");
                
                // Convert each list to a list of ItemStackData
                List<ItemStackData> mainInventory = convertToItemStackDataList(mainInv);
                List<ItemStackData> armorInventory = convertToItemStackDataList(armorInv);
                List<ItemStackData> offhandInventory = convertToItemStackDataList(offhandInv);
                List<ItemStackData> enderChest = convertToItemStackDataList(enderChestInv);
                
                // Create the InventoryData object
                this.inventoryData = new InventoryData(mainInventory, armorInventory, offhandInventory, enderChest);
                LOGGER.info("Successfully converted inventories to inventoryData");
            } catch (Exception e) {
                LOGGER.error("Failed to convert inventories to inventoryData", e);
            }
        }
          private List<ItemStackData> convertToItemStackDataList(List<Map<String, Object>> items) {
            List<ItemStackData> result = new ArrayList<>();
            
            if (items != null) {
                LOGGER.info("Converting item list with " + items.size() + " items");
                for (Map<String, Object> item : items) {
                    try {
                        String itemId = (String) item.get("itemId");
                        if (itemId == null) itemId = "minecraft:air";
                        
                        Number countNum = (Number) item.get("count");
                        int count = countNum != null ? countNum.intValue() : 0;
                          Object nbtObj = item.get("nbt");
                        Map<String, Object> nbt = null;
                        
                        if (nbtObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> nbtMap = (Map<String, Object>) nbtObj;
                            nbt = nbtMap;
                        } else if (nbtObj instanceof List) {
                            // If nbt is an empty array, treat it as null
                            @SuppressWarnings("unchecked")
                            List<Object> nbtList = (List<Object>) nbtObj;
                            if (!nbtList.isEmpty()) {
                                // If the array has elements, convert them to a map
                                nbt = new HashMap<>();
                                nbt.put("listData", nbtList);
                            }
                        } else if (nbtObj != null) {
                            // For any other non-null case, create a simple map with the value
                            nbt = new HashMap<>();
                            nbt.put("value", nbtObj.toString());
                        }
                        
                        result.add(new ItemStackData(itemId, count, nbt));
                    } catch (Exception e) {
                        LOGGER.error("Error converting item: " + item, e);
                    }
                }
            } else {
                LOGGER.info("Item list is null");
            }
            
            return result;
        }
        
        public InventoryData getInventoryData() {
            // If inventoryData is still null but we have inventories, try to convert again
            if (inventoryData == null && inventories != null) {
                convertInventoriesToInventoryData();
            }
            
            return inventoryData;
        }
        
        public void setInventoryData(InventoryData inventoryData) {
            this.inventoryData = inventoryData;
        }
    }
}
