package info.rusty.webshoplink;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = Webshoplink.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Server API configuration
    private static final ForgeConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the shop API")
            .define("apiBaseUrl", "http://localhost:8080/api/shop");

    private static final ForgeConfigSpec.ConfigValue<String> SHOP_ENDPOINT = BUILDER
            .comment("Endpoint for initiating shop processes")
            .define("shopEndpoint", "/initiate");

    private static final ForgeConfigSpec.ConfigValue<String> SHOP_FINISH_ENDPOINT = BUILDER
            .comment("Endpoint for finishing shop processes")
            .define("shopFinishEndpoint", "/finish");

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static String apiBaseUrl;
    public static String shopEndpoint;
    public static String shopFinishEndpoint;

    public static Set<Item> items;

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        apiBaseUrl = API_BASE_URL.get();
        shopEndpoint = SHOP_ENDPOINT.get();
        shopFinishEndpoint = SHOP_FINISH_ENDPOINT.get();
    }
}
