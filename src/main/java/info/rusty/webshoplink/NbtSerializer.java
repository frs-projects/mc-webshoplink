package info.rusty.webshoplink;

import com.google.gson.*;
import net.minecraft.nbt.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles serialization of NBT data to ensure proper structure preservation
 * when transmitting data to the API server.
 */
public class NbtSerializer {
    
    /**
     * Converts an NBT compound tag to a JsonObject preserving the nested structure
     * @param tag The NBT compound tag to convert
     * @return A JsonObject representation of the NBT tag
     */
    public static JsonElement serializeNbt(CompoundTag tag) {
        if (tag == null) {
            return JsonNull.INSTANCE;
        }
        
        JsonObject jsonObject = new JsonObject();
        Set<String> keys = tag.getAllKeys();
        
        for (String key : keys) {
            Tag value = tag.get(key);
            jsonObject.add(key, serializeNbtValue(value));
        }
        
        return jsonObject;
    }
    
    /**
     * Serializes an individual NBT tag value based on its type
     * @param tag The NBT tag to serialize
     * @return A JsonElement representation of the tag
     */
    private static JsonElement serializeNbtValue(Tag tag) {
        if (tag instanceof CompoundTag) {
            return serializeNbt((CompoundTag) tag);
        } else if (tag instanceof ListTag) {
            return serializeNbtList((ListTag) tag);
        } else if (tag instanceof StringTag) {
            return new JsonPrimitive(((StringTag) tag).getAsString());
        } else if (tag instanceof IntTag) {
            return new JsonPrimitive(((IntTag) tag).getAsInt());
        } else if (tag instanceof LongTag) {
            return new JsonPrimitive(((LongTag) tag).getAsLong());
        } else if (tag instanceof DoubleTag) {
            return new JsonPrimitive(((DoubleTag) tag).getAsDouble());
        } else if (tag instanceof FloatTag) {
            return new JsonPrimitive(((FloatTag) tag).getAsFloat());
        } else if (tag instanceof ShortTag) {
            return new JsonPrimitive(((ShortTag) tag).getAsShort());
        } else if (tag instanceof ByteTag) {
            return new JsonPrimitive(((ByteTag) tag).getAsByte());
        } else if (tag instanceof ByteArrayTag) {
            byte[] array = ((ByteArrayTag) tag).getAsByteArray();
            JsonArray jsonArray = new JsonArray();
            for (byte b : array) {
                jsonArray.add(b);
            }
            return jsonArray;
        } else if (tag instanceof IntArrayTag) {
            int[] array = ((IntArrayTag) tag).getAsIntArray();
            JsonArray jsonArray = new JsonArray();
            for (int i : array) {
                jsonArray.add(i);
            }
            return jsonArray;
        } else if (tag instanceof LongArrayTag) {
            long[] array = ((LongArrayTag) tag).getAsLongArray();
            JsonArray jsonArray = new JsonArray();
            for (long l : array) {
                jsonArray.add(l);
            }
            return jsonArray;
        } else {
            // Default fallback for any other tag types
            return new JsonPrimitive(tag.toString());
        }
    }
    
    /**
     * Serializes an NBT list tag to a JsonArray
     * @param listTag The NBT list tag to serialize
     * @return A JsonArray representation of the list tag
     */
    private static JsonArray serializeNbtList(ListTag listTag) {
        JsonArray jsonArray = new JsonArray();
        
        for (int i = 0; i < listTag.size(); i++) {
            Tag element = listTag.get(i);
            jsonArray.add(serializeNbtValue(element));
        }
        
        return jsonArray;
    }
    
    /**
     * TypeAdapter for CompoundTag to use with Gson
     */
    public static class CompoundTagAdapter implements JsonSerializer<CompoundTag>, JsonDeserializer<CompoundTag> {
        @Override
        public JsonElement serialize(CompoundTag tag, Type typeOfSrc, JsonSerializationContext context) {
            return serializeNbt(tag);
        }
        
        @Override
        public CompoundTag deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonObject()) {
                return parseJsonToCompoundTag(json.getAsJsonObject());
            } else {
                throw new JsonParseException("Expected JsonObject for CompoundTag, got: " + json);
            }
        }
        
        private CompoundTag parseJsonToCompoundTag(JsonObject jsonObject) {
            CompoundTag tag = new CompoundTag();
            
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                
                if (value.isJsonObject()) {
                    tag.put(key, parseJsonToCompoundTag(value.getAsJsonObject()));
                } else if (value.isJsonArray()) {
                    tag.put(key, parseJsonToListTag(value.getAsJsonArray()));
                } else if (value.isJsonPrimitive()) {
                    JsonPrimitive primitive = value.getAsJsonPrimitive();
                    
                    if (primitive.isString()) {
                        tag.putString(key, primitive.getAsString());
                    } else if (primitive.isNumber()) {
                        Number number = primitive.getAsNumber();
                        
                        // Try to determine the number type
                        if (number.toString().contains(".")) {
                            tag.putDouble(key, number.doubleValue());
                        } else {
                            long longValue = number.longValue();
                            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                tag.putInt(key, number.intValue());
                            } else {
                                tag.putLong(key, longValue);
                            }
                        }
                    } else if (primitive.isBoolean()) {
                        tag.putBoolean(key, primitive.getAsBoolean());
                    }
                }
            }
            
            return tag;
        }
        
        private ListTag parseJsonToListTag(JsonArray jsonArray) {
            if (jsonArray.size() == 0) {
                return new ListTag();
            }
            
            ListTag listTag = new ListTag();
            
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    listTag.add(parseJsonToCompoundTag(element.getAsJsonObject()));
                } else if (element.isJsonArray()) {
                    listTag.add(parseJsonToListTag(element.getAsJsonArray()));
                } else if (element.isJsonPrimitive()) {
                    JsonPrimitive primitive = element.getAsJsonPrimitive();
                    
                    if (primitive.isString()) {
                        listTag.add(StringTag.valueOf(primitive.getAsString()));
                    } else if (primitive.isNumber()) {
                        Number number = primitive.getAsNumber();
                        
                        if (number.toString().contains(".")) {
                            listTag.add(DoubleTag.valueOf(number.doubleValue()));
                        } else {
                            long longValue = number.longValue();
                            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                listTag.add(IntTag.valueOf(number.intValue()));
                            } else {
                                listTag.add(LongTag.valueOf(longValue));
                            }
                        }
                    } else if (primitive.isBoolean()) {
                        listTag.add(ByteTag.valueOf(primitive.getAsBoolean() ? (byte)1 : (byte)0));
                    }
                }
            }
            
            return listTag;
        }
    }
}
