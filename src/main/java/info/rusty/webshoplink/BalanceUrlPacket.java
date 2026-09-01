package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

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

    public static void handle(BalanceUrlPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // The client class is only referenced inside the DistExecutor lambda, so the
        // dedicated server never classloads it (nor MCEF).
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> info.rusty.webshoplink.client.BalanceOverlay.setServerUrl(msg.url)));
        ctx.setPacketHandled(true);
    }
}
