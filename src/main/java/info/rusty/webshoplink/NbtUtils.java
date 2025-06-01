package info.rusty.webshoplink;

import dev.dewy.nbt.Nbt;
import dev.dewy.nbt.tags.collection.CompoundTag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Utility class for converting between Minecraft NBT data and JSON
 * using the BitBuf NBT library
 */
public class NbtUtils {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Nbt NBT = new Nbt();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Converts a Minecraft CompoundTag to a JSON string
     *
     * @param mcTag The Minecraft CompoundTag to convert
     * @return JSON string representation of the NBT data
     */
    public static String minecraftNbtToJson(net.minecraft.nbt.CompoundTag mcTag) {
        if (mcTag == null) {
            return "{}";
        }
        
        try {            // Convert Minecraft NBT to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(mcTag, baos);
            byte[] nbtBytes = baos.toByteArray();
            
            // Convert to BitBuf CompoundTag
            ByteArrayInputStream bais = new ByteArrayInputStream(nbtBytes);
            DataInputStream dis = new DataInputStream(bais);
            CompoundTag bitbufTag = NBT.fromStream(dis);            // Convert to JSON
            StringWriter writer = new StringWriter();
            
            // Create a temporary file for JSON conversion
            File tempFile = File.createTempFile("nbt_json", ".json");
            NBT.toJson(bitbufTag, tempFile);
            
            // Read the JSON from the file
            String jsonStr = new String(Files.readAllBytes(tempFile.toPath()));
            tempFile.delete(); // Clean up
            
            // Format the JSON for better readability
            JsonElement jsonElement = JsonParser.parseString(jsonStr);
            return GSON.toJson(jsonElement);
        } catch (IOException e) {
            LOGGER.error("Error converting NBT to JSON", e);
            return "{}";
        }
    }

    /**
     * Converts a JSON string back to a Minecraft CompoundTag
     *
     * @param json The JSON string to convert
     * @return Minecraft CompoundTag
     */
    public static net.minecraft.nbt.CompoundTag jsonToMinecraftNbt(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new net.minecraft.nbt.CompoundTag();
        }        try {
            // Parse JSON to BitBuf CompoundTag using temporary file
            File tempFile = File.createTempFile("json_nbt", ".json");
            Files.write(tempFile.toPath(), json.getBytes());
            
            CompoundTag bitbufTag = NBT.fromJson(tempFile);
            tempFile.delete(); // Clean up the temporary file
            
            // Convert to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NBT.toStream(bitbufTag, dos);
            byte[] nbtBytes = baos.toByteArray();
            
            // Convert to Minecraft CompoundTag
            return net.minecraft.nbt.NbtIo.readCompressed(new ByteArrayInputStream(nbtBytes));
        } catch (IOException e) {
            LOGGER.error("Error converting JSON to NBT", e);
            return new net.minecraft.nbt.CompoundTag();
        }
    }

    /**
     * Converts a Minecraft CompoundTag to a Base64 string
     *
     * @param mcTag The Minecraft CompoundTag to convert
     * @return Base64 string representation of the NBT data
     */
    public static String minecraftNbtToBase64(net.minecraft.nbt.CompoundTag mcTag) {
        if (mcTag == null) {
            return "";
        }
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(mcTag, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            LOGGER.error("Error converting NBT to Base64", e);
            return "";
        }
    }    /**
     * Converts a Base64 string back to a Minecraft CompoundTag
     *
     * @param base64 The Base64 string to convert
     * @return Minecraft CompoundTag
     */
    public static net.minecraft.nbt.CompoundTag base64ToMinecraftNbt(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new net.minecraft.nbt.CompoundTag();
        }
        
        try {
            byte[] nbtBytes = Base64.getDecoder().decode(base64);
            return net.minecraft.nbt.NbtIo.readCompressed(new ByteArrayInputStream(nbtBytes));
        } catch (IOException e) {
            LOGGER.error("Error converting Base64 to NBT", e);
            return new net.minecraft.nbt.CompoundTag();
        }
    }
    
    /**
     * Demonstrates the NBT utility methods with an example
     * This method can be called to verify the functionality works correctly
     */
    public static void runExample() {
        LOGGER.info("Running NBT conversion example...");
        
        try {
            // Create a test Minecraft CompoundTag
            net.minecraft.nbt.CompoundTag mcTag = new net.minecraft.nbt.CompoundTag();
            mcTag.putString("name", "Diamond Sword");
            mcTag.putInt("damage", 5);
            
            // Create a nested compound
            net.minecraft.nbt.CompoundTag displayTag = new net.minecraft.nbt.CompoundTag();
            displayTag.putString("Name", "§bEnchanted Diamond Sword");
            mcTag.put("display", displayTag);
            
            // Create an enchantment list
            net.minecraft.nbt.ListTag enchantList = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.CompoundTag sharpness = new net.minecraft.nbt.CompoundTag();
            sharpness.putString("id", "minecraft:sharpness");
            sharpness.putInt("lvl", 3);
            enchantList.add(sharpness);
            mcTag.put("Enchantments", enchantList);
            
            // Convert to JSON
            String json = minecraftNbtToJson(mcTag);
            LOGGER.info("NBT to JSON: {}", json);
            
            // Convert back to NBT
            net.minecraft.nbt.CompoundTag reconstructedTag = jsonToMinecraftNbt(json);
            LOGGER.info("Reconstructed from JSON - Name: {}, Damage: {}", 
                    reconstructedTag.getString("name"), 
                    reconstructedTag.getInt("damage"));
            
            // Check if display tag was preserved
            if (reconstructedTag.contains("display")) {
                LOGGER.info("Display Name: {}", 
                        reconstructedTag.getCompound("display").getString("Name"));
            }
            
            // Convert to Base64
            String base64 = minecraftNbtToBase64(mcTag);
            LOGGER.info("NBT to Base64: {}", base64);
            
            // Convert back from Base64
            net.minecraft.nbt.CompoundTag fromBase64 = base64ToMinecraftNbt(base64);
            LOGGER.info("From Base64 - Name: {}", fromBase64.getString("name"));
            
            LOGGER.info("NBT conversion example completed successfully");
        } catch (Exception e) {
            LOGGER.error("Error in NBT conversion example", e);
        }
    }
}
