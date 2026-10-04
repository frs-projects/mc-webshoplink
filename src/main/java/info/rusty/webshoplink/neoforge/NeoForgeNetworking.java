//? if neoforge {
package info.rusty.webshoplink.neoforge;

import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.Webshoplink;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;

// Carries Networking.MESSAGES as optional NeoForge payloads, one payload type per message
// (webshoplink:<name>): a client without the mod can still join, and isRemotePresent tells
// whether this player's client negotiated the channel.
final class NeoForgeNetworking implements Networking.Transport {

    // A shared message wrapped as a payload; the message classes themselves stay loader-neutral.
    private record Payload<T>(CustomPacketPayload.Type<Payload<T>> type, T message) implements CustomPacketPayload {
    }

    private static final Map<Class<?>, CustomPacketPayload.Type<?>> TYPES = new HashMap<>();

    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Handlers run on the main thread by default, which is what the shared handlers expect.
        PayloadRegistrar registrar = event.registrar(Networking.PROTOCOL_VERSION).optional();
        for (Networking.Message<?> message : Networking.MESSAGES) {
            register(registrar, message);
        }
    }

    private static <T> void register(PayloadRegistrar registrar, Networking.Message<T> message) {
        CustomPacketPayload.Type<Payload<T>> type =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Webshoplink.MODID, message.name()));
        StreamCodec<FriendlyByteBuf, Payload<T>> codec = StreamCodec.of(
                (buf, payload) -> message.encoder().accept(payload.message(), buf),
                buf -> new Payload<>(type, message.decoder().apply(buf)));
        TYPES.put(message.type(), type);
        // playToClient/playToServer make NeoForge reject a payload sent the wrong way, so a
        // client-bound handler never runs on a server.
        if (message.direction() == Networking.Direction.TO_CLIENT) {
            IPayloadHandler<Payload<T>> handler = (payload, ctx) -> message.handler().accept(payload.message(), null);
            registrar.playToClient(type, codec, handler);
        } else {
            IPayloadHandler<Payload<T>> handler = (payload, ctx) -> message.handler().accept(payload.message(), (ServerPlayer) ctx.player());
            registrar.playToServer(type, codec, handler);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Payload<T> wrap(T message) {
        CustomPacketPayload.Type<Payload<T>> type = (CustomPacketPayload.Type<Payload<T>>) TYPES.get(message.getClass());
        if (type == null) {
            throw new IllegalStateException("Unregistered message " + message.getClass().getName());
        }
        return new Payload<>(type, message);
    }

    @Override
    public boolean isRemotePresent(ServerPlayer player) {
        // Every message shares one negotiation, so the client-bound browser payload stands for all.
        return player.connection.hasChannel(TYPES.get(Networking.MESSAGES.get(0).type()));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Object message) {
        PacketDistributor.sendToPlayer(player, wrap(message));
    }

    @Override
    public void sendToServer(Object message) {
        PacketDistributor.sendToServer(wrap(message));
    }
}
//?}
