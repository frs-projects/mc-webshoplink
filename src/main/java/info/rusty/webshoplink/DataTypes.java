package info.rusty.webshoplink;

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
            // TODO
        }

        public void setEchestFromPlayer(Container playerEchest) {
            // TODO
        }
    }

    public static class InventoryData {
        private Integer size;
        private ItemList items;

        public Integer getSize() {
            return size;
        }

        public ItemList getItems() {
            return items;
        }
    }

    public static class ContainerData {
        private Integer size;
        private ItemList items;

        public Integer getSize() {
            return size;
        }

        public ItemList getItems() {
            return items;
        }
    }

    public static class ItemList {
        private Map<Integer, ItemData> items;

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
        private String nbt;

        public String getItemId() {
            return itemId;
        }

        public Integer getCount() {
            return count;
        }

        public String getNbt() {
            return nbt;
        }

        public ItemStack getItemStackData() {
            // TODO
            return null;
        }
    }
}
