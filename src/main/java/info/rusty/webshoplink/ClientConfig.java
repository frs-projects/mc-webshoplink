package info.rusty.webshoplink;

//? if neoforge {
import net.neoforged.neoforge.common.ModConfigSpec;
//?} else {
/*import net.minecraftforge.common.ModConfigSpec;
*///?}

/**
 * Client-side settings (config/webshoplink-client.toml). Everything here only
 * affects the local player's UI; the shop protocol itself is configured
 * server-side in {@link Config}. Registered and loaded by the loader like {@link Config}.
 */
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue BALANCE_DISPLAY_ENABLED = BUILDER
            .comment("Show the shop balance as a small always-visible overlay.",
                    "Renders the web page at balanceUrl in a tiny in-game browser box (requires MCEF).")
            .define("balanceDisplayEnabled", true);

    private static final ModConfigSpec.ConfigValue<String> BALANCE_URL = BUILDER
            .comment("URL of the balance page. The player's UUID is appended to this URL,",
                    "or substituted for a {uuid} placeholder if the URL contains one.",
                    "Must be reachable from the player's client. No authentication is used.")
            .define("balanceUrl", "http://localhost:8080/balance/");

    private static final ModConfigSpec.EnumValue<Corner> BALANCE_POSITION = BUILDER
            .comment("Screen corner the balance overlay is anchored to.")
            .defineEnum("balancePosition", Corner.TOP_RIGHT);

    private static final ModConfigSpec.IntValue BALANCE_WIDTH = BUILDER
            .comment("Width of the balance overlay in scaled (GUI) pixels.")
            .defineInRange("balanceWidth", 120, 20, 640);

    private static final ModConfigSpec.IntValue BALANCE_HEIGHT = BUILDER
            .comment("Height of the balance overlay in scaled (GUI) pixels.")
            .defineInRange("balanceHeight", 40, 10, 360);

    private static final ModConfigSpec.IntValue BALANCE_MARGIN = BUILDER
            .comment("Distance from the screen edges in scaled (GUI) pixels.")
            .defineInRange("balanceMargin", 4, 0, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean balanceDisplayEnabled;
    public static String balanceUrl;
    public static Corner balancePosition;
    public static int balanceWidth;
    public static int balanceHeight;
    public static int balanceMargin;

    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public static void load() {
        balanceDisplayEnabled = BALANCE_DISPLAY_ENABLED.get();
        balanceUrl = BALANCE_URL.get();
        balancePosition = BALANCE_POSITION.get();
        balanceWidth = BALANCE_WIDTH.get();
        balanceHeight = BALANCE_HEIGHT.get();
        balanceMargin = BALANCE_MARGIN.get();
    }
}
