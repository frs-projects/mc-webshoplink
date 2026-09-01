package info.rusty.webshoplink;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge network channel between the server-side shop logic and the client-side
 * in-game browser.
 *
 * <p>The channel is registered as <em>optional</em> (it accepts a missing protocol
 * version on the other end), so vanilla clients or clients without this mod can
 * still connect to the server. Whether a given player can actually open the browser
 * is then checked at runtime with {@link #isClientReady(ServerPlayer)}.
 */
public final class Networking {

    // Bumped whenever the set of packets changes, so a client running an older
    // version of this mod is rejected at login instead of receiving a packet id
    // it cannot decode. Clients without the mod are still accepted (acceptMissing).
    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Webshoplink.MODID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

    private Networking() {
    }

    /**
     * Registers all packets. Must be called once during mod construction.
     */
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenBrowserPacket.class,
                OpenBrowserPacket::encode, OpenBrowserPacket::decode, OpenBrowserPacket::handle);
        CHANNEL.registerMessage(id++, ShopActionPacket.class,
                ShopActionPacket::encode, ShopActionPacket::decode, ShopActionPacket::handle);
        CHANNEL.registerMessage(id++, BalanceUrlPacket.class,
                BalanceUrlPacket::encode, BalanceUrlPacket::decode, BalanceUrlPacket::handle);
    }

    /**
     * @return {@code true} if the player's client has this mod (and therefore the
     *         browser channel) installed, so a browser packet can be delivered.
     */
    public static boolean isClientReady(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    /**
     * Tells the given player's client to open the in-game shop browser at {@code url}.
     */
    public static void openBrowser(ServerPlayer player, java.util.UUID processId, String url) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenBrowserPacket(processId, url));
    }

    /**
     * Pushes the server-configured balance URL to the given player. Sending a blank
     * URL is meaningful: it tells the client this server has none configured, so the
     * overlay falls back to the client's own config value.
     */
    public static void sendBalanceUrl(ServerPlayer player) {
        if (!isClientReady(player)) {
            return;
        }
        String url = (Config.balanceUrl != null) ? Config.balanceUrl : "";
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BalanceUrlPacket(url));
    }

    /** Pushes the server-configured balance URL to every player with the mod installed. */
    public static void sendBalanceUrlToAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendBalanceUrl(player);
        }
    }
}
