package info.rusty.webshoplink.client;

import de.keksuccino.rinku.Rinku;
import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.ShopActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Client-side entry point for opening the shop browser. Invoked from the packet
 * handler via {@code DistExecutor} so it (and Rinku) is only ever classloaded on a
 * physical client.
 */
public final class ClientShopBrowser {

    private ClientShopBrowser() {
    }

    public static void open(UUID processId, String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!Rinku.isInitialized()) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(
                            "The in-game browser is still initializing. Please run the shop command again in a moment."), false);
                }
                // Nothing will ever show this session, so don't leave it open on the server.
                Networking.sendToServer(new ShopActionPacket(processId, ShopActionPacket.Action.CANCEL));
                return;
            }
            // Navigate before showing the screen: the shared browser keeps painting
            // its transparent parking page until the shop page is ready, so this
            // head start is pure win and there is nothing to flash through.
            ShopBrowserHost.startSession(url);
            mc.setScreen(new ShopBrowserScreen(processId));
        });
    }
}
