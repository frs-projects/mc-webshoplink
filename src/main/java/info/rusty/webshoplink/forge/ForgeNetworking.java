//? if forge {
/*package info.rusty.webshoplink.forge;

import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.Webshoplink;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

// Carries Networking.MESSAGES over one optional SimpleChannel: a missing mod on the other end
// is accepted. Discriminators are the list order, which matches the pre-Stonecutter build, so
// old and new Forge builds stay wire compatible.
final class ForgeNetworking implements Networking.Transport {

    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Webshoplink.MODID, "main"),
            () -> Networking.PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(Networking.PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(Networking.PROTOCOL_VERSION));

    void registerMessages() {
        int id = 0;
        for (Networking.Message<?> message : Networking.MESSAGES) {
            register(id++, message);
        }
    }

    private <T> void register(int id, Networking.Message<T> message) {
        // The direction makes Forge drop a message that arrives on the wrong side, so a
        // client-bound handler never runs on a server.
        NetworkDirection direction = message.direction() == Networking.Direction.TO_CLIENT
                ? NetworkDirection.PLAY_TO_CLIENT
                : NetworkDirection.PLAY_TO_SERVER;
        channel.messageBuilder(message.type(), id, direction)
                .encoder(message.encoder())
                .decoder(message.decoder())
                .consumerMainThread((msg, ctx) -> message.handler().accept(msg, ctx.get().getSender()))
                .add();
    }

    @Override
    public boolean isRemotePresent(ServerPlayer player) {
        return channel.isRemotePresent(player.connection.connection);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object message) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    @Override
    public void sendToServer(Object message) {
        channel.sendToServer(message);
    }
}
*///?}
