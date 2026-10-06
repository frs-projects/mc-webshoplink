package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client &rarr; server: sent once on login by a client that has this mod (and therefore
 * Rinku, a mandatory client dependency). Receiving it is what marks a player as able to
 * open the in-game browser; see {@link Networking#isClientReady(ServerPlayer)}.
 */
public class ClientHelloPacket {

    public static void encode(ClientHelloPacket msg, FriendlyByteBuf buf) {
    }

    public static ClientHelloPacket decode(FriendlyByteBuf buf) {
        return new ClientHelloPacket();
    }

    // Server main thread; player is the sender.
    static void handle(ClientHelloPacket msg, ServerPlayer player) {
        if (player != null) {
            Networking.onClientHello(player);
        }
    }
}
