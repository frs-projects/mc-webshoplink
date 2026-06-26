package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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

    public static void handle(OpenBrowserPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // Run on the client thread. The client class is only referenced inside the
        // DistExecutor lambda, so the dedicated server never classloads it (nor MCEF).
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> info.rusty.webshoplink.client.ClientShopBrowser.open(msg.processId, msg.url)));
        ctx.setPacketHandled(true);
    }
}
