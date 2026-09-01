package info.rusty.webshoplink;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Webshoplink.MODID)
public class Webshoplink {

    public static final String MODID = "webshoplink";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Webshoplink() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Client-only UI settings (balance overlay). Never loaded on the dedicated server.
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Register the network channel/packets used to drive the in-game browser.
        Networking.register();
        LOGGER.info("Webshoplink mod initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Webshoplink mod loaded on server side");
    }

    /**
     * Push the server-configured balance URL as soon as a player joins, so their
     * overlay uses this server's page instead of whatever their local config says.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Networking.sendBalanceUrl(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering shop commands");
        ShopCommands.registerCommands(event);
    }
}
