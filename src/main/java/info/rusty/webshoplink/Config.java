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

    private static final ForgeConfigSpec.ConfigValue<String> SHOP_CHECKOUT_ENDPOINT = BUILDER
            .comment("Endpoint for checking out shop processes")
            .define("shopCheckoutEndpoint", "/{uuid}/checkout");
            
    private static final ForgeConfigSpec.ConfigValue<String> SHOP_APPLIED_ENDPOINT = BUILDER
            .comment("Endpoint for marking shop processes as applied")
            .define("shopAppliedEndpoint", "/{uuid}/setApplied");
            
    // Money Items configuration
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MONEY_ITEMS = BUILDER
            .comment("Items to be considered as money for shop transactions (will be removed when initiating shop)")
            .defineList("moneyItems", 
                Collections.singletonList("minecraft:emerald"),
                Config::validateItemName);
                
    // Debug configuration
    private static final ForgeConfigSpec.BooleanValue DEBUG_ENABLED = BUILDER
            .comment("Enable debug logging")
            .define("debugEnabled", false);
            
    private static final ForgeConfigSpec.EnumValue<DebugVerbosity> DEBUG_VERBOSITY = BUILDER
            .comment("Debug verbosity level: MINIMAL (basic info), DEFAULT (standard info), ALL (detailed info including inventory contents)")
            .defineEnum("debugVerbosity", DebugVerbosity.DEFAULT);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static String apiBaseUrl;
    public static String shopEndpoint;
    public static String shopCheckoutEndpoint;
    public static String shopAppliedEndpoint;
    public static Set<Item> moneyItems;
    public static boolean debugEnabled;
    public static DebugVerbosity debugVerbosity;
    
    /**
     * Debug verbosity levels
     */
    public enum DebugVerbosity {
        MINIMAL,  // Basic operation info (started, finished, applied)
        DEFAULT,  // Standard info (operation details, counts)
        ALL       // Detailed info (includes full inventory contents)
    }

    private static boolean validateItemName(final Object obj) {
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        apiBaseUrl = API_BASE_URL.get();
        shopEndpoint = SHOP_ENDPOINT.get();
        shopCheckoutEndpoint = SHOP_CHECKOUT_ENDPOINT.get();
        shopAppliedEndpoint = SHOP_APPLIED_ENDPOINT.get();
        
        // Load money items
        moneyItems = MONEY_ITEMS.get().stream()
                .map(ResourceLocation::tryParse)
                .map(ForgeRegistries.ITEMS::getValue)
                .collect(Collectors.toSet());
                
        // Load debug configuration
        debugEnabled = DEBUG_ENABLED.get();
        debugVerbosity = DEBUG_VERBOSITY.get();
    }
}
