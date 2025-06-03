package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

/**
 * Utility class for debugging NBT serialization/deserialization
 */
public class NbtDebugUtils {
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(CompoundTag.class, new NbtSerializer.CompoundTagAdapter())
            .create();
    
    /**
     * Logs NBT tag details for debugging purposes
     * @param tag The NBT tag to log
     * @param label A label for identifying this NBT in logs
     */
    public static void logNbtDetails(CompoundTag tag, String label) {
        if (tag == null) {
            DebugLogger.log(label + " NBT: null", Config.DebugVerbosity.DEFAULT);
            return;
        }
        
        // Log original NBT string
        DebugLogger.log(label + " Original NBT: " + tag.toString(), Config.DebugVerbosity.DEFAULT);
        
        // Convert to JSON for debugging
        JsonElement jsonElement = NbtSerializer.serializeNbt(tag);
        String prettyJson = PRETTY_GSON.toJson(jsonElement);
        
        // Log JSON representation
        DebugLogger.log(label + " JSON representation:\n" + prettyJson, Config.DebugVerbosity.DEFAULT);
        
        // Log keys to make sure we're capturing everything
        DebugLogger.log(label + " NBT keys: " + tag.getAllKeys(), Config.DebugVerbosity.DEFAULT);
    }
    
    /**
     * Logs item stack NBT for debugging
     * @param stack The item stack
     * @param label A label for the logs
     */
    public static void logItemStackNbt(net.minecraft.world.item.ItemStack stack, String label) {
        if (stack == null || stack.isEmpty()) {
            DebugLogger.log(label + ": Empty or null ItemStack", Config.DebugVerbosity.DEFAULT);
            return;
        }
        
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        DebugLogger.log(label + ": Item ID = " + itemId + ", Count = " + stack.getCount(), Config.DebugVerbosity.DEFAULT);
        
        if (stack.hasTag()) {
            logNbtDetails(stack.getTag(), label);
        } else {
            DebugLogger.log(label + ": No NBT data", Config.DebugVerbosity.DEFAULT);
        }
    }
    
    /**
     * Logs a JsonObject representation of NBT data
     * @param nbt The JSON object containing NBT data
     * @param label A label for the logs
     */
    public static void logJsonNbt(JsonObject nbt, String label) {
        if (nbt == null) {
            DebugLogger.log(label + " NBT JSON: null", Config.DebugVerbosity.DEFAULT);
            return;
        }
        
        // Pretty print the JSON for better readability
        String prettyJson = PRETTY_GSON.toJson(nbt);
        DebugLogger.log(label + " NBT JSON:\n" + prettyJson, Config.DebugVerbosity.DEFAULT);
        DebugLogger.log(label + " NBT JSON keys: " + nbt.keySet(), Config.DebugVerbosity.DEFAULT);
        
        try {
            // Try to convert it to a CompoundTag to check if it works
            CompoundTag tag = NbtSerializer.CompoundTagAdapter.parseJsonToCompoundTag(nbt);
            DebugLogger.log(label + " Converted back to NBT: " + tag.toString(), Config.DebugVerbosity.DEFAULT);
            DebugLogger.log(label + " Converted NBT keys: " + tag.getAllKeys(), Config.DebugVerbosity.DEFAULT);
            
            // And convert back to JSON again to see if the cycle is consistent
            JsonObject reconvertedJson = (JsonObject) NbtSerializer.serializeNbt(tag);
            boolean isEqual = nbt.equals(reconvertedJson);
            DebugLogger.log(label + " Round-trip conversion test: " + (isEqual ? "CONSISTENT" : "INCONSISTENT"), Config.DebugVerbosity.DEFAULT);
            if (!isEqual) {
                DebugLogger.log(label + " Original and reconverted JSON differ. This might be the source of NBT loss.", Config.DebugVerbosity.DEFAULT);
                String reconvertedPretty = PRETTY_GSON.toJson(reconvertedJson);
                DebugLogger.log(label + " Reconverted JSON:\n" + reconvertedPretty, Config.DebugVerbosity.ALL);
            }
        } catch (Exception e) {
            DebugLogger.logError(label + " Failed to convert JSON to NBT: " + e.getMessage(), e);
        }
    }
}
