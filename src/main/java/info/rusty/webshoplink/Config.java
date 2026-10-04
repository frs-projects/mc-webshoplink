package info.rusty.webshoplink;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
//? if neoforge {
import net.neoforged.neoforge.common.ModConfigSpec;
//?} else {
/*import net.minecraftforge.common.ModConfigSpec;
*///?}

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Server/common settings (config/webshoplink-common.toml). The loader registers SPEC and calls
// load() whenever the file is loaded or reloaded. Written against NeoForge's ModConfigSpec;
// Stonecutter renames it to ModConfigSpec for the Forge node (see stonecutter.gradle.kts).
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Server API configuration
    private static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the shop API")
            .define("apiBaseUrl", "http://localhost:8080/api/shop");

    private static final ModConfigSpec.ConfigValue<String> API_KEY = BUILDER
            .comment("API key sent as the X-Webshop-Api-Key header on every request to the shop API")
            .define("apiKey", "");

    private static final ModConfigSpec.ConfigValue<String> SHOP_ENDPOINT = BUILDER
            .comment("Endpoint for initiating shop processes")
            .define("shopEndpoint", "/initiate");

    private static final ModConfigSpec.ConfigValue<String> SHOP_CANCEL_ENDPOINT = BUILDER
            .comment("Endpoint for cancelling shop processes")
            .define("shopCancelEndpoint", "/{uuid}/cancel");

    private static final ModConfigSpec.ConfigValue<String> SHOP_CHECKOUT_ENDPOINT = BUILDER
            .comment("Endpoint for checking out shop processes")
            .define("shopCheckoutEndpoint", "/{uuid}/checkout");
            
    private static final ModConfigSpec.ConfigValue<String> SHOP_APPLIED_ENDPOINT = BUILDER
            .comment("Endpoint for marking shop processes as applied")
            .define("shopAppliedEndpoint", "/{uuid}/setApplied");

    private static final ModConfigSpec.ConfigValue<String> BALANCE_URL = BUILDER
            .comment("URL of the balance page pushed to clients for their balance overlay.",
                    "The player's UUID is appended to this URL, or substituted for a {uuid}",
                    "placeholder if the URL contains one. Must be reachable from the player's",
                    "client. Leave empty to let each client use its own balanceUrl client config.")
            .define("balanceUrl", "");

    // Command permission configuration
    private static final ModConfigSpec.IntValue SHOP_COMMAND_PERMISSION_LEVEL = BUILDER
            .comment("Minecraft permission level required to run /shop (initiate a shopping session).",
                    "0 = anyone, 1 = moderator, 2 = gamemaster/operator (also command blocks), 3 = admin, 4 = owner.",
                    "Set to 2 to lock shops down so only operators or command blocks can open them, e.g. via",
                    "'/execute as @p run shop <type>' at a specific location. The finishing commands",
                    "(/shopFinish, /confirmFinish, /shopCancel) stay available to all players so they can",
                    "complete a session that was opened for them; they cannot start a session on their own.")
            .defineInRange("shopCommandPermissionLevel", 0, 0, 4);

    // Debug configuration
    private static final ModConfigSpec.BooleanValue DEBUG_ENABLED = BUILDER
            .comment("Enable debug logging")
            .define("debugEnabled", false);
            
    private static final ModConfigSpec.EnumValue<DebugVerbosity> DEBUG_VERBOSITY = BUILDER
            .comment("Debug verbosity level: MINIMAL (basic info), DEFAULT (standard info), ALL (detailed info including inventory contents)")
            .defineEnum("debugVerbosity", DebugVerbosity.DEFAULT);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static String apiBaseUrl;
    public static String apiKey;
    public static String shopEndpoint;
    public static String shopCancelEndpoint;
    public static String shopCheckoutEndpoint;
    public static String shopAppliedEndpoint;
    public static String balanceUrl;
    public static int shopCommandPermissionLevel;
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
        return obj instanceof final String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(itemName));
    }

    public static void load() {
        apiBaseUrl = API_BASE_URL.get();
        apiKey = API_KEY.get();
        shopEndpoint = SHOP_ENDPOINT.get();
        shopCancelEndpoint = SHOP_CANCEL_ENDPOINT.get();
        shopCheckoutEndpoint = SHOP_CHECKOUT_ENDPOINT.get();
        shopAppliedEndpoint = SHOP_APPLIED_ENDPOINT.get();
        balanceUrl = BALANCE_URL.get();
        shopCommandPermissionLevel = SHOP_COMMAND_PERMISSION_LEVEL.get();

        // Load debug configuration
        debugEnabled = DEBUG_ENABLED.get();
        debugVerbosity = DEBUG_VERBOSITY.get();

        // A reload can change the URL while players are online; push the new value.
        Networking.sendBalanceUrlToAll();
    }
}
