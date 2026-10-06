//? if forge {
/*package info.rusty.webshoplink.forge;

import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.client.BalanceOverlay;
import info.rusty.webshoplink.client.ShopBrowserHost;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

// Client-only wiring; only registered when running on a client.
final class ForgeClient {

    private ForgeClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClient::onRegisterOverlays);
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::onLoggingOut);
    }

    private static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("balance",
                (gui, guiGraphics, partialTick, width, height) -> BalanceOverlay.INSTANCE.render(guiGraphics, width, height));
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ShopBrowserHost.onLoggingIn();
        Networking.sendClientHello();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ShopBrowserHost.onLoggingOut();
        BalanceOverlay.onLoggingOut();
    }
}
*///?}
