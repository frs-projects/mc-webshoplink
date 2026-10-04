//? if neoforge {
package info.rusty.webshoplink.neoforge;

import info.rusty.webshoplink.Webshoplink;
import info.rusty.webshoplink.client.BalanceOverlay;
import info.rusty.webshoplink.client.ShopBrowserHost;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

// Client-only wiring; only registered when running on a client.
final class NeoForgeClient {

    private NeoForgeClient() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClient::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::onLoggingOut);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Webshoplink.MODID, "balance"),
                (guiGraphics, deltaTracker) -> BalanceOverlay.INSTANCE.render(
                        guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight()));
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ShopBrowserHost.onLoggingIn();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ShopBrowserHost.onLoggingOut();
        BalanceOverlay.onLoggingOut();
    }
}
//?}
