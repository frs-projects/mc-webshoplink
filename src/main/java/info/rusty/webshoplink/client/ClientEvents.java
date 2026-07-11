package info.rusty.webshoplink.client;

import info.rusty.webshoplink.Webshoplink;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only mod-bus event handlers. */
@Mod.EventBusSubscriber(modid = Webshoplink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("balance", BalanceOverlay.INSTANCE);
    }
}
