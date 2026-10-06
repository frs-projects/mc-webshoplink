package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server &rarr; client: the balance page URL this server wants the overlay to show.
 *
 * <p>Sent once when a player with the mod joins, and again whenever the server
 * config is reloaded. An empty URL means "no server-side URL configured"; the
 * client then falls back to its own {@code balanceUrl} client config value.
 */
public class BalanceUrlPacket {

    private final String url;

    public BalanceUrlPacket(String url) {
        this.url = url;
    }

    public static void encode(BalanceUrlPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.url);
    }

    public static BalanceUrlPacket decode(FriendlyByteBuf buf) {
        return new BalanceUrlPacket(buf.readUtf());
    }

    // Client main thread; the transport only delivers this message on a client. The client
    // class is referenced fully qualified, so the dedicated server never classloads it (nor Rinku).
    static void handle(BalanceUrlPacket msg) {
        info.rusty.webshoplink.client.BalanceOverlay.setServerUrl(msg.url);
    }
}
