package info.rusty.webshoplink;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import info.rusty.webshoplink.DataTypes.InventorySnapshot;
import info.rusty.webshoplink.DataTypes.InventoryData;
import info.rusty.webshoplink.DataTypes.ItemStackData;
import info.rusty.webshoplink.NbtUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all inventory operations for the Webshoplink mod.
 */
public class InventoryManager {
    private static final Logger LOGGER = LogUtils.getLogger();

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

    /**
     * Serializes a player's inventory to a format suitable for API communication
     */
    public static InventoryData serializeInventory(Player player) {
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

    /**
     * Serializes an ItemStack to ItemStackData
     */
    public static ItemStackData serializeItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ItemStackData("minecraft:air", 0, null);
        }
        
        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        int count = stack.getCount();        Map<String, Object> nbt = null;
        
        // Serialize NBT data if present
        if (stack.hasTag()) {
            // Convert NBT to JSON using our NbtUtils
            String json = NbtUtils.minecraftNbtToJson(stack.getTag());
            
            // Store it as a map entry so it serializes properly
            nbt = new HashMap<>();
            nbt.put("nbtJson", json);
        }
        
        return new ItemStackData(itemId, count, nbt);
    }    /**
     * Converts an NBT CompoundTag to a Map for serialization
     * This is a legacy method, use NbtUtils.minecraftNbtToJson() for new code
     */
    public static void convertNbtToMap(net.minecraft.nbt.CompoundTag tag, Map<String, Object> map) {
        // Use the NbtUtils class to convert to JSON and back to a map
        // This is simpler than manually traversing the NBT structure
        String json = NbtUtils.minecraftNbtToJson(tag);
        
        // Store the full JSON representation - simpler approach
        map.put("nbtJson", json);
    }

    /**
     * Deserializes ItemStackData back to an ItemStack
     */
    public static ItemStack deserializeItemStack(ItemStackData data) {
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
            if (data.getNbt().containsKey("nbtJson")) {
                String json = (String) data.getNbt().get("nbtJson");
                net.minecraft.nbt.CompoundTag nbtTag = NbtUtils.jsonToMinecraftNbt(json);
                stack.setTag(nbtTag);
            } else {
                // Legacy fallback for old data format
                net.minecraft.nbt.CompoundTag nbtTag = new net.minecraft.nbt.CompoundTag();
                convertMapToNbt(data.getNbt(), nbtTag);
                stack.setTag(nbtTag);
            }
        }
        
        return stack;
    }    /**
     * Converts a Map back to an NBT CompoundTag
     * This is a legacy method, use NbtUtils.jsonToMinecraftNbt() for new code
     */
    public static void convertMapToNbt(Map<String, Object> map, net.minecraft.nbt.CompoundTag compoundTag) {
        // Check if we have the simplified JSON representation
        if (map.containsKey("nbtJson")) {
            String json = (String) map.get("nbtJson");
            net.minecraft.nbt.CompoundTag jsonTag = NbtUtils.jsonToMinecraftNbt(json);
            
            // Copy all values to the provided tag
            for (String key : jsonTag.getAllKeys()) {
                compoundTag.put(key, jsonTag.get(key));
            }
            return;
        }
        
        // Legacy fallback for old format data
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
                        net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
                        for (Object obj : list) {
                            if (obj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> mapValue = (Map<String, Object>) obj;
                                net.minecraft.nbt.CompoundTag listItemTag = new net.minecraft.nbt.CompoundTag();
                                convertMapToNbt(mapValue, listItemTag);
                                listTag.add(listItemTag);
                            }
                        }
                        compoundTag.put(key, listTag);
                    } else {
                        // For primitive lists, store as string list for simplicity
                        net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
                        for (Object obj : list) {
                            listTag.add(net.minecraft.nbt.StringTag.valueOf(obj.toString()));
                        }
                        compoundTag.put(key, listTag);
                    }
                }
            } else {
                // For primitives, store as string for simplicity
                compoundTag.putString(key, value.toString());
            }
        }
    }

    /**
     * Generates a diff between original and new inventory for display to the player
     */
    public static String generateInventoryDiff(InventorySnapshot original, InventoryData newInventory) {
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
                    diff.append("- ").append(origStack.getCount()).append("x ")
                            .append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(origStack.getItem())).append("\n");
                } else {
                    diff.append("Changed ").append(origStack.getCount()).append("x ")
                            .append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(origStack.getItem()))
                            .append(" to ").append(newStack.getCount()).append("x ").append(newStack.getItemId()).append("\n");
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

    /**
     * Compares an ItemStack with ItemStackData
     */
    public static boolean compareItemStacks(ItemStack original, ItemStackData newStack) {
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

    /**
     * Removes money items from a player's inventory
     */
    public static int removeMoneyItems(Player player) {
        int removed = 0;
        Inventory inventory = player.getInventory();
        
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && Config.moneyItems.contains(stack.getItem())) {
                removed += stack.getCount();
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        
        return removed;
    }
}
