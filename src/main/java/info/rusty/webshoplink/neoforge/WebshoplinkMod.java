//? if neoforge {
package info.rusty.webshoplink.neoforge;

import info.rusty.webshoplink.ClientConfig;
import info.rusty.webshoplink.Config;
import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.Webshoplink;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// NeoForge entry point: registers the configs and the payload transport, and forwards the
// loader's events to the shared Webshoplink. The same shared code the Forge build uses;
// everything here is a spelling difference.
@Mod(Webshoplink.MODID)
public final class WebshoplinkMod {

    public WebshoplinkMod(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        Networking.bind(new NeoForgeNetworking());
        modEventBus.addListener(NeoForgeNetworking::registerPayloads);
        modEventBus.addListener(ModConfigEvent.Loading.class, event -> load(event.getConfig().getSpec()));
        modEventBus.addListener(ModConfigEvent.Reloading.class, event -> load(event.getConfig().getSpec()));
        // The game event bus, not the mod bus: everything below is a running-game event.
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Client-only UI settings (balance overlay). Never loaded on the dedicated server.
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // A separate class, so a dedicated server never loads the client-only types (nor MCEF).
        if (dist.isClient()) {
            NeoForgeClient.register(modEventBus);
        }
    }

    private static void load(IConfigSpec spec) {
        if (spec == Config.SPEC) {
            Config.load();
        } else if (spec == ClientConfig.SPEC) {
            ClientConfig.load();
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Webshoplink.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Webshoplink.onServerStopped();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Webshoplink.onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Webshoplink.registerCommands(event.getDispatcher());
    }
}
//?}
