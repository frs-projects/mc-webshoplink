package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Server &rarr; client: open the in-game shop browser at the given URL.
 */
public class OpenBrowserPacket {

    private final UUID processId;
    private final String url;

    public OpenBrowserPacket(UUID processId, String url) {
        this.processId = processId;
        this.url = url;
    }

    public static void encode(OpenBrowserPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.processId);
        buf.writeUtf(msg.url);
    }

    public static OpenBrowserPacket decode(FriendlyByteBuf buf) {
        return new OpenBrowserPacket(buf.readUUID(), buf.readUtf());
    }

    // Client main thread; the transport only delivers this message on a client. The client
    // class is referenced fully qualified, so the dedicated server never classloads it (nor Rinku).
    static void handle(OpenBrowserPacket msg) {
        info.rusty.webshoplink.client.ClientShopBrowser.open(msg.processId, msg.url);
    }
}
