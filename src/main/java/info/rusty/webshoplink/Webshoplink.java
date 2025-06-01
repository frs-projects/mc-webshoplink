package info.rusty.webshoplink;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
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

// Import modular classes
import info.rusty.webshoplink.InventoryManager;
import info.rusty.webshoplink.ApiService;
import info.rusty.webshoplink.DataTypes;
import info.rusty.webshoplink.ShopCommands;
import info.rusty.webshoplink.UIUtils;
import info.rusty.webshoplink.DataTypes.InventorySnapshot;
import info.rusty.webshoplink.DataTypes.InventoryData;
import info.rusty.webshoplink.DataTypes.ItemStackData;
import info.rusty.webshoplink.DataTypes.ShopResponse;
import info.rusty.webshoplink.DataTypes.ShopFinishResponse;

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
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Webshoplink mod initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Webshoplink mod loaded on server side");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering shop commands");
        ShopCommands.registerCommands(event);
    }
}
