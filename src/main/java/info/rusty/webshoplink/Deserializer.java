package info.rusty.webshoplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class Deserializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public static Map<String, Object> deserializeItemStack(String itemStackJson) {
        // Simply deserialize the JSON string to a HashMap
        return GSON.fromJson(itemStackJson, MAP_TYPE);
    }

    public static Map<String, Object> deserializeInventory(String inventoryJson) {
        // Simply deserialize the JSON string to a HashMap
        return GSON.fromJson(inventoryJson, MAP_TYPE);
    }

    public static Map<String, Object> deserializeContainer(String containerJson) {
        // Simply deserialize the JSON string to a HashMap
        return GSON.fromJson(containerJson, MAP_TYPE);
    }
}
