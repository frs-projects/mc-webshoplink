package info.rusty.webshoplink;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import info.rusty.webshoplink.DataTypes.InventorySnapshot;
import info.rusty.webshoplink.DataTypes.InventoryData;
import info.rusty.webshoplink.DataTypes.ContainerData;
import info.rusty.webshoplink.DataTypes.ItemList;
import info.rusty.webshoplink.DataTypes.ItemData;

/**
 * Handles all inventory operations for the Webshoplink mod.
 */
public class InventoryManager {
    /**
     * Captures a snapshot of a player's inventory
     */
    public static InventorySnapshot captureInventory(Player player) {
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

    /**
     * Checks if two inventory snapshots match
     */
    public static boolean inventoriesMatch(InventorySnapshot snapshot, InventorySnapshot current) {
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

    /**
     * Applies a new inventory to a player
     */
    public static void applyNewInventory(ServerPlayer player, InventoryData newInventory) {
        // Log the operation
        DebugLogger.log("Applying new inventory to player: " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);

        ItemList items = newInventory.getItems();
        for (int i = 0; i < newInventory.getSize(); i++) {
            // Current slot content
            ItemStack currentSlotContent = player.getInventory().getItem(i);
            String currentItemId = currentSlotContent.getClass().toString();
            Integer currentItemCount = currentSlotContent.getCount();
            String currentItemNbt = currentSlotContent.getTags().toString();
            // New slot content
            ItemData item = items.getItem(i);
            // Compare details:
            if (!currentItemId.equals(item.getItemId()) || !currentItemCount.equals(item.getCount()) || !currentItemNbt.equals(item.getNbt())) {
                // Stack has changed, overwrite with new data.
                player.getInventory().setItem(i, item.getItemStackData());
            }
        }
    }

    public static void applyNewEchest(ServerPlayer player, ContainerData newEchest) {
        // Log the operation
        DebugLogger.log("Applying new E-Chest to player: " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);

        ItemList items = newEchest.getItems();
        for (int i = 0; i < newEchest.getSize(); i++) {
            // Current slot content
            ItemStack currentSlotContent = player.getEnderChestInventory().getItem(i);
            String currentItemId = currentSlotContent.getClass().toString();
            Integer currentItemCount = currentSlotContent.getCount();
            String currentItemNbt = currentSlotContent.getTags().toString();
            // New slot content
            ItemData item = items.getItem(i);
            // Compare details:
            if (!currentItemId.equals(item.getItemId()) || !currentItemCount.equals(item.getCount()) || !currentItemNbt.equals(item.getNbt())) {
                // Stack has changed, overwrite with new data.
                player.getInventory().setItem(i, item.getItemStackData());
            }
        }
    }

    /**
     * Generates a diff between original and new inventory for display to the player
     */
    public static String generateInventoryDiff(InventorySnapshot original, InventoryData newInventory) {
        DebugLogger.log("Generating inventory diff", Config.DebugVerbosity.MINIMAL);
        
        return "Not implemented";
    }

    /**
     * Removes money items from a player's inventory
     */
    public static int removeMoneyItems(Player player) {
        int removed = 0;
        Inventory inventory = player.getInventory();
        
        // Log the operation if debug is enabled
        DebugLogger.log("Checking for money items to remove from player: " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && Config.moneyItems.contains(stack.getItem())) {
                DebugLogger.log("Removing money item: " + stack.getItem().getDescription().getString() + 
                    " x" + stack.getCount() + " from slot " + i,
                    Config.DebugVerbosity.DEFAULT
                );
                removed += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        
        DebugLogger.log("Removed " + removed + " money items from player " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);
        
        return removed;
    }
}
