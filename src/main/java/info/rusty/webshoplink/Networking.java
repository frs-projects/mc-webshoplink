package info.rusty.webshoplink;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

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

    private static final String PROTOCOL_VERSION = "1";

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
}
