//? if forge {
/*package info.rusty.webshoplink.forge;

import com.mojang.logging.LogUtils;
import info.rusty.webshoplink.ClientConfig;
import info.rusty.webshoplink.Config;
import info.rusty.webshoplink.Networking;
import info.rusty.webshoplink.Webshoplink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

// Forge entry point: registers the configs and the SimpleChannel transport, and forwards the
// loader's events to the shared Webshoplink. Nothing else.
//
// Only line comments here, and in every other loader-gated file: Stonecutter comments an
// inactive file out with one block comment, which a Javadoc block inside would end early.
@Mod(Webshoplink.MODID)
public final class WebshoplinkMod {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ForgeNetworking networking = new ForgeNetworking();

    public WebshoplinkMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        Networking.bind(networking);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        // The game event bus, not the mod bus: everything below is a running-game event.
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Client-only UI settings (balance overlay). Never loaded on the dedicated server.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // A separate class, so a dedicated server never loads the client-only types (nor Rinku).
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.register(modEventBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        networking.registerMessages();
        LOGGER.info("Webshoplink mod initialized");
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        load(event.getConfig().getSpec());
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        load(event.getConfig().getSpec());
    }

    private static void load(IConfigSpec<?> spec) {
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
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Webshoplink.onPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Webshoplink.registerCommands(event.getDispatcher());
    }
}
*///?}
