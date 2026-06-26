package info.rusty.webshoplink.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Client-side entry point for opening the shop browser. Invoked from the packet
 * handler via {@code DistExecutor} so it (and MCEF) is only ever classloaded on a
 * physical client.
 */
public final class ClientShopBrowser {

    private ClientShopBrowser() {
    }

    public static void open(UUID processId, String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!MCEF.isInitialized()) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(
                            "The in-game browser is still initializing. Please run the shop command again in a moment."), false);
                }
                return;
            }
            mc.setScreen(new ShopBrowserScreen(processId, url));
        });
    }
}
