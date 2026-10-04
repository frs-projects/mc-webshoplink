package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The messages between the server-side shop logic and the client-side in-game browser,
 * independent of how a loader carries them. Each loader's entry point binds a {@link Transport}
 * (a Forge {@code SimpleChannel}, or NeoForge payloads) that registers every entry of
 * {@link #MESSAGES} and delivers it to its handler on the receiving side's main thread,
 * rejecting messages that arrive in the wrong direction.
 *
 * <p>The channel is <em>optional</em> (it accepts a missing mod on the other end), so vanilla
 * clients or clients without this mod can still connect to the server. Whether a given player
 * can actually open the browser is then checked at runtime with {@link #isClientReady(ServerPlayer)}.
 */
public final class Networking {

    // Bumped whenever the set of packets changes, so a client running an older
    // version of this mod is rejected at login instead of receiving a packet id
    // it cannot decode. Clients without the mod are still accepted.
    public static final String PROTOCOL_VERSION = "2";

    public enum Direction { TO_CLIENT, TO_SERVER }

    /**
     * One message type. {@code handler} gets the sending player for {@link Direction#TO_SERVER}
     * messages and {@code null} for {@link Direction#TO_CLIENT} ones.
     */
    public record Message<T>(String name, Class<T> type, Direction direction,
                             BiConsumer<T, FriendlyByteBuf> encoder,
                             Function<FriendlyByteBuf, T> decoder,
                             BiConsumer<T, ServerPlayer> handler) {
    }

    /** How messages travel; supplied by the loader. */
    public interface Transport {
        /** Whether the player's client has this mod's channel, so a message can be delivered. */
        boolean isRemotePresent(ServerPlayer player);

        void sendToPlayer(ServerPlayer player, Object message);

        void sendToServer(Object message);
    }

    // Order is the Forge discriminator order, so it is part of the wire format: append only.
    public static final List<Message<?>> MESSAGES = List.of(
            new Message<>("open_browser", OpenBrowserPacket.class, Direction.TO_CLIENT,
                    OpenBrowserPacket::encode, OpenBrowserPacket::decode, (msg, sender) -> OpenBrowserPacket.handle(msg)),
            new Message<>("shop_action", ShopActionPacket.class, Direction.TO_SERVER,
                    ShopActionPacket::encode, ShopActionPacket::decode, ShopActionPacket::handle),
            new Message<>("balance_url", BalanceUrlPacket.class, Direction.TO_CLIENT,
                    BalanceUrlPacket::encode, BalanceUrlPacket::decode, (msg, sender) -> BalanceUrlPacket.handle(msg)));

    private static Transport transport;

    private Networking() {
    }

    public static void bind(Transport loaderTransport) {
        transport = loaderTransport;
    }

    /**
     * @return {@code true} if the player's client has this mod (and therefore the
     *         browser channel) installed, so a browser packet can be delivered.
     */
    public static boolean isClientReady(ServerPlayer player) {
        return transport.isRemotePresent(player);
    }

    /**
     * Tells the given player's client to open the in-game shop browser at {@code url}.
     */
    public static void openBrowser(ServerPlayer player, java.util.UUID processId, String url) {
        transport.sendToPlayer(player, new OpenBrowserPacket(processId, url));
    }

    /** Client side: reports a button press in the browser screen to the server. */
    public static void sendToServer(ShopActionPacket packet) {
        transport.sendToServer(packet);
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
        transport.sendToPlayer(player, new BalanceUrlPacket(url));
    }

    /** Pushes the server-configured balance URL to every player with the mod installed. */
    public static void sendBalanceUrlToAll() {
        MinecraftServer server = Webshoplink.server();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendBalanceUrl(player);
        }
    }
}
