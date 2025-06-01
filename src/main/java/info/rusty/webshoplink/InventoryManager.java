package info.rusty.webshoplink;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import info.rusty.webshoplink.DataTypes.*;

import java.util.Map;

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

        Map<Integer, ItemData> items = newInventory.getItems();
        for (int i = 0; i < newInventory.getSize(); i++) {
            // Get the new item for this slot
            ItemData item = items.get(i);
            ItemStack currentStack = player.getInventory().getItem(i);
            
            if (item != null) {
                // We have a new item to set - compare with current item
                boolean shouldUpdate = false;
                ItemStack newItemStack = item.getItemStackData();
                
                // Check if current item is empty or different from the new item
                if (currentStack.isEmpty()) {
                    shouldUpdate = !newItemStack.isEmpty();
                } else {
                    // Compare item type
                    String currentItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(currentStack.getItem()).toString();
                    if (!currentItemId.equals(item.getItemId())) {
                        shouldUpdate = true;
                    } 
                    // Compare count
                    else if (currentStack.getCount() != item.getCount()) {
                        shouldUpdate = true;
                    } 
                    // Compare NBT data
                    else if ((currentStack.hasTag() && item.getNbt() == null) || 
                             (!currentStack.hasTag() && item.getNbt() != null) ||
                             (currentStack.hasTag() && item.getNbt() != null && 
                              !currentStack.getTag().toString().equals(item.getNbt()))) {
                        shouldUpdate = true;
                    }
                }
                
                if (shouldUpdate) {
                    player.getInventory().setItem(i, newItemStack);
                    DebugLogger.log("Updated slot " + i + " to " + item.getItemId() + " x" + item.getCount(), Config.DebugVerbosity.DEFAULT);
                }
            } else if (!currentStack.isEmpty()) {
                // No item at this position in the new inventory, but slot is not empty - clear it
                player.getInventory().setItem(i, ItemStack.EMPTY);
                DebugLogger.log("Cleared slot " + i, Config.DebugVerbosity.DEFAULT);
            }
        }
    }    public static void applyNewEchest(ServerPlayer player, ContainerData newEchest) {
        // Log the operation
        DebugLogger.log("Applying new E-Chest to player: " + player.getName().getString(), Config.DebugVerbosity.MINIMAL);

        Map<Integer, ItemData> items = newEchest.getItems();
        for (int i = 0; i < newEchest.getSize(); i++) {
            // Get the new item for this slot
            ItemData item = items.get(i);
            ItemStack currentStack = player.getEnderChestInventory().getItem(i);
            
            if (item != null) {
                // We have a new item to set - compare with current item
                boolean shouldUpdate = false;
                ItemStack newItemStack = item.getItemStackData();
                
                // Check if current item is empty or different from the new item
                if (currentStack.isEmpty()) {
                    shouldUpdate = !newItemStack.isEmpty();
                } else {
                    // Compare item type
                    String currentItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(currentStack.getItem()).toString();
                    if (!currentItemId.equals(item.getItemId())) {
                        shouldUpdate = true;
                    } 
                    // Compare count
                    else if (currentStack.getCount() != item.getCount()) {
                        shouldUpdate = true;
                    } 
                    // Compare NBT data
                    else if ((currentStack.hasTag() && item.getNbt() == null) || 
                             (!currentStack.hasTag() && item.getNbt() != null) ||
                             (currentStack.hasTag() && item.getNbt() != null && 
                              !currentStack.getTag().toString().equals(item.getNbt()))) {
                        shouldUpdate = true;
                    }
                }
                
                if (shouldUpdate) {
                    player.getEnderChestInventory().setItem(i, newItemStack);
                    DebugLogger.log("Updated ender chest slot " + i + " to " + item.getItemId() + " x" + item.getCount(), Config.DebugVerbosity.DEFAULT);
                }
            } else if (!currentStack.isEmpty()) {
                // No item at this position in the new inventory, but slot is not empty - clear it
                player.getEnderChestInventory().setItem(i, ItemStack.EMPTY);
                DebugLogger.log("Cleared ender chest slot " + i, Config.DebugVerbosity.DEFAULT);
            }
        }
    }    /**
     * Generates a diff between original and new inventory for display to the player
     */
    public static InventoryDiff generateInventoryDiff(InventorySnapshot original, InventoryData newInventory) {
        return generateInventoryDiff(original, newInventory, null);
    }
    
    /**
     * Generates a diff between original and new inventory including ender chest for display to the player
     */
    public static InventoryDiff generateInventoryDiff(InventorySnapshot original, InventoryData newInventory, ContainerData newEchest) {
        DebugLogger.log("Generating inventory diff", Config.DebugVerbosity.MINIMAL);
        
        InventoryDiff diff = new InventoryDiff();
        Map<Integer, ItemData> newItems = newInventory.getItems();
        
        // Create a map of items in the original inventory for easy lookup
        java.util.Map<String, Integer> originalItemCounts = new java.util.HashMap<>();
        
        // First, add all items from the main inventory
        for (int i = 0; i < original.getMainInventory().length; i++) {
            ItemStack stack = original.getMainInventory()[i];
            if (!stack.isEmpty()) {
                String itemKey = getItemKey(stack);
                originalItemCounts.put(itemKey, originalItemCounts.getOrDefault(itemKey, 0) + stack.getCount());
            }
        }
        
        // Add armor items
        for (ItemStack stack : original.getArmorInventory()) {
            if (!stack.isEmpty()) {
                String itemKey = getItemKey(stack);
                originalItemCounts.put(itemKey, originalItemCounts.getOrDefault(itemKey, 0) + stack.getCount());
            }
        }
        
        // Add offhand items
        for (ItemStack stack : original.getOffhandInventory()) {
            if (!stack.isEmpty()) {
                String itemKey = getItemKey(stack);
                originalItemCounts.put(itemKey, originalItemCounts.getOrDefault(itemKey, 0) + stack.getCount());
            }
        }
        
        // Add ender chest items
        for (ItemStack stack : original.getEnderChest()) {
            if (!stack.isEmpty()) {
                String itemKey = getItemKey(stack);
                originalItemCounts.put(itemKey, originalItemCounts.getOrDefault(itemKey, 0) + stack.getCount());
            }
        }
        
        // Create a map for the new inventory
        java.util.Map<String, Integer> newItemCounts = new java.util.HashMap<>();
        
        // Process new inventory items
        for (int i = 0; i < newInventory.getSize(); i++) {
            ItemData itemData = newItems.get(i);
            if (itemData != null) {
                String itemKey = itemData.getItemId();
                if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                    itemKey += ":" + itemData.getNbt();
                }
                newItemCounts.put(itemKey, newItemCounts.getOrDefault(itemKey, 0) + itemData.getCount());
            }
        }
        
        // Process new ender chest items if provided
        if (newEchest != null) {
            Map<Integer, ItemData> newEchestItems = newEchest.getItems();
            for (int i = 0; i < newEchest.getSize(); i++) {
                ItemData itemData = newEchestItems.get(i);
                if (itemData != null) {
                    String itemKey = itemData.getItemId();
                    if (itemData.getNbt() != null && !itemData.getNbt().isEmpty()) {
                        itemKey += ":" + itemData.getNbt();
                    }
                    newItemCounts.put(itemKey, newItemCounts.getOrDefault(itemKey, 0) + itemData.getCount());
                }
            }
        }
          
        // Compare original and new inventories to generate diff
        java.util.Set<String> allItems = new java.util.HashSet<>();
        allItems.addAll(originalItemCounts.keySet());
        allItems.addAll(newItemCounts.keySet());
        
        for (String itemKey : allItems) {
            int originalCount = originalItemCounts.getOrDefault(itemKey, 0);
            int newCount = newItemCounts.getOrDefault(itemKey, 0);
            int diff_count = newCount - originalCount;
              if (diff_count != 0) {
                // Use the full item key for better identification
                String itemId = itemKey;
                // Extract just the item ID part without the NBT data for display
                if (itemKey.contains(":nbt")) {
                    itemId = itemKey.substring(0, itemKey.indexOf(":nbt"));
                }
                
                if (diff_count > 0) {
                    diff.addItem(itemId, diff_count);
                } else {
                    diff.removeItem(itemId, -diff_count);
                }
            }
        }
        
        return diff;
    }
      /**
     * Helper method to get a unique key for an item
     */
    private static String getItemKey(ItemStack stack) {
        String itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.hasTag()) {
            itemKey += ":" + stack.getTag().toString();
        }
        return itemKey;
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
