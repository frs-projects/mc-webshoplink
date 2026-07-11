package info.rusty.webshoplink;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Client-side settings (config/webshoplink-client.toml). Everything here only
 * affects the local player's UI; the shop protocol itself is configured
 * server-side in {@link Config}.
 */
@Mod.EventBusSubscriber(modid = Webshoplink.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue BALANCE_DISPLAY_ENABLED = BUILDER
            .comment("Show the shop balance as a small always-visible overlay.",
                    "Renders the web page at balanceUrl in a tiny in-game browser box (requires MCEF).")
            .define("balanceDisplayEnabled", true);

    private static final ForgeConfigSpec.ConfigValue<String> BALANCE_URL = BUILDER
            .comment("URL of the balance page. The player's UUID is appended to this URL,",
                    "or substituted for a {uuid} placeholder if the URL contains one.",
                    "Must be reachable from the player's client. No authentication is used.")
            .define("balanceUrl", "http://localhost:8080/balance/");

    private static final ForgeConfigSpec.EnumValue<Corner> BALANCE_POSITION = BUILDER
            .comment("Screen corner the balance overlay is anchored to.")
            .defineEnum("balancePosition", Corner.TOP_RIGHT);

    private static final ForgeConfigSpec.IntValue BALANCE_WIDTH = BUILDER
            .comment("Width of the balance overlay in scaled (GUI) pixels.")
            .defineInRange("balanceWidth", 120, 20, 640);

    private static final ForgeConfigSpec.IntValue BALANCE_HEIGHT = BUILDER
            .comment("Height of the balance overlay in scaled (GUI) pixels.")
            .defineInRange("balanceHeight", 40, 10, 360);

    private static final ForgeConfigSpec.IntValue BALANCE_MARGIN = BUILDER
            .comment("Distance from the screen edges in scaled (GUI) pixels.")
            .defineInRange("balanceMargin", 4, 0, 100);

    static final ForgeConfigSpec SPEC = BUILDER.build();

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

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        balanceDisplayEnabled = BALANCE_DISPLAY_ENABLED.get();
        balanceUrl = BALANCE_URL.get();
        balancePosition = BALANCE_POSITION.get();
        balanceWidth = BALANCE_WIDTH.get();
        balanceHeight = BALANCE_HEIGHT.get();
        balanceMargin = BALANCE_MARGIN.get();
    }
}
