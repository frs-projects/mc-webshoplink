package info.rusty.webshoplink;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Everything the mod does in response to game events, for every loader. The loader entry
 * points in {@code info.rusty.webshoplink.forge} and {@code info.rusty.webshoplink.neoforge}
 * register the configs and network transport and forward their events here.
 */
public final class Webshoplink {

    /** Filled in from {@code mod.id} by Stonecutter. */
    public static final String MODID = /*$ mod_id*/ "webshoplink";
    private static final Logger LOGGER = LogUtils.getLogger();

    // The running server, for code outside an event (config reloads, item data encoding).
    private static volatile MinecraftServer server;

    private Webshoplink() {
    }

    /** The running server, or {@code null} when none is (yet). */
    public static MinecraftServer server() {
        return server;
    }

    public static void onServerStarting(MinecraftServer startingServer) {
        server = startingServer;
        LOGGER.info("Webshoplink mod loaded on server side");
    }

    public static void onServerStopped() {
        server = null;
    }

    /**
     * Push the server-configured balance URL as soon as a player joins, so their
     * overlay uses this server's page instead of whatever their local config says.
     */
    public static void onPlayerLoggedIn(ServerPlayer player) {
        Networking.sendBalanceUrl(player);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        LOGGER.info("Registering shop commands");
        ShopCommands.registerCommands(dispatcher);
    }
}
