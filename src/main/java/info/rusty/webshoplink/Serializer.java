package info.rusty.webshoplink;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Map;

public class Serializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, Object> serializeItemStackToMap(ItemStack stack) {
        Map<String, Object> properties = new java.util.HashMap<>();
        // Check for NBT Data and serialize that first:
        if (stack.hasTag()) {
            properties.put("nbt", stack.getTags().toString());
        }
        // Serialize ItemStack data to a string
        properties.put("item_id", stack.getItem().toString());
        properties.put("count", stack.getCount());
        return properties;
    }

    public static String serializeItemStack(ItemStack stack) {
        Map<String, Object> properties = new java.util.HashMap<>();
        // Check for NBT Data and serialize that first:
        if (stack.hasTag()) {
            properties.put("nbt", stack.getTags().toString());
        }
        // Serialize ItemStack data to a string
        properties.put("item_id", stack.getItem().toString());
        properties.put("count", stack.getCount());
        return GSON.toJson(properties);
    }

    public static String serializeInventory(Inventory inventory) {
        Map<String, Object> properties = new java.util.HashMap<>();
        // Basic properties:
        properties.put("size", inventory.getContainerSize());
        // Loop all items, serialize them, save them with the slot index in properties
        NonNullList<ItemStack> items = inventory.items;
        Map<Integer, Object> inventoryStacks = new java.util.HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                inventoryStacks.put(i, serializeItemStackToMap(stack));
            }
        }
        properties.put("items", inventoryStacks);
        // Serialize Inventory data to a string
        return GSON.toJson(properties);
    }

    public static String serializeContainer(Container container) {
        Map<String, Object> properties = new java.util.HashMap<>();
        // Basic properties:
        properties.put("size", container.getContainerSize());
        // Loop items
        Map<Integer, Object> items = new java.util.HashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                items.put(i, serializeItemStackToMap(stack));
            }
        }
        properties.put("items", items);
        // Serialize Container data to a string
        return GSON.toJson(properties);
    }
}