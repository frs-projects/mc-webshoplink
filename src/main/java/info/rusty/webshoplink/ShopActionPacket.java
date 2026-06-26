package info.rusty.webshoplink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client &rarr; server: the player pressed a button in the in-game browser screen.
 */
public class ShopActionPacket {

    public enum Action {
        FINISH,
        CANCEL
    }

    private final UUID processId;
    private final Action action;

    public ShopActionPacket(UUID processId, Action action) {
        this.processId = processId;
        this.action = action;
    }

    public static void encode(ShopActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.processId);
        buf.writeEnum(msg.action);
    }

    public static ShopActionPacket decode(FriendlyByteBuf buf) {
        return new ShopActionPacket(buf.readUUID(), buf.readEnum(Action.class));
    }

    public static void handle(ShopActionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            switch (msg.action) {
                case FINISH -> ShopCommands.finishAndConfirm(player, msg.processId);
                case CANCEL -> ShopCommands.cancelShop(player, msg.processId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
