package cn.minerealms.iic;

import cn.minerealms.iic.commands.ImprovedMobsCommand;
import cn.minerealms.iic.industrial.*;
import cn.minerealms.iic.network.PacketHandler;
import cn.minerealms.iic.scanner.ScannerItem;
import cn.minerealms.iic.server.HudUpdateService;
import cn.minerealms.iic.turrets.MekanismTurretsConfig;
import cn.minerealms.iic.turrets.common.block_entity.LaserTurretBlockEntity;
import cn.minerealms.iic.turrets.common.entity.LaserEntity;
import cn.minerealms.iic.turrets.common.packet.MekanismTurretsPacketHandler;
import cn.minerealms.iic.turrets.common.registry.*;
import io.github.flemmli97.improvedmobs.api.difficulty.DifficultyFetcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.network.SerializableDataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.io.File;

@Mod(IntegratedIndustrialCraft.MODID)
public class IntegratedIndustrialCraft {

    public static final String MODID = "integratedindustrialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("IntegratedIndustrialCraft");

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> TERRAIN_SCANNER = ITEMS.register("terrain_scanner", ScannerItem::new);

    public IntegratedIndustrialCraft() {
        LOGGER.info("Initializing Integrated Industrial Craft (IIC)");

        // Initialize Mixin load tracker
        cn.minerealms.iic.util.MixinLoadTracker.init();

        // Create config directory
        File configDir = FMLPaths.CONFIGDIR.get().resolve("iic").toFile();
        if (!configDir.exists()) {
            configDir.mkdir();
        }

        // Register configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MekanismTurretsConfig.SPEC);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);
        modBus.addListener(this::onConfigLoad);
        ITEMS.register(modBus);

        // Register Mekanism Turrets components
        BlockRegistry.BLOCKS.register(modBus);
        BlockEntityTypeRegistry.BLOCK_ENTITY_TYPES.register(modBus);
        BlockEntityTypeRegistry.STANDARD_BLOCK_ENTITIES.register(modBus); // 标准 Forge BlockEntity 注册
        ContainerTypeRegistry.CONTAINER_TYPES.register(modBus);
        EntityRegistry.ENTITY_TYPES.register(modBus);
        ItemRegistry.ITEMS.register(modBus);
        ItemRegistry.ModItemTab.CREATIVE_MODE_TABS.register(modBus);
        SoundRegistry.SOUNDS.register(modBus);

        // Register event handlers
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.minerealms.iic.client.ClientEventHandler.setup();
        }
        MinecraftForge.EVENT_BUS.register(cn.minerealms.iic.forge.EventHandler.class);
        MinecraftForge.EVENT_BUS.register(HudUpdateService.class);
        MinecraftForge.EVENT_BUS.addListener(LaserEntity::enterChunk);

        // Register turret data tickets
        registerTurretsDataTickets();

        LOGGER.info("IIC initialization complete");
    }

    private void setup(FMLCommonSetupEvent event) {
        LOGGER.info("IIC common setup");

        // Register network packets (all packets registered here)
        PacketHandler.register();

        // Register difficulty provider if ImprovedMobs is loaded
        if (ModList.get().isLoaded("improvedmobs")) {
            LOGGER.info("ImprovedMobs detected, registering difficulty provider");
            DifficultyFetcher.add(
                    new ResourceLocation(MODID, "industrial_difficulty"),
                    new cn.minerealms.iic.difficulty.DifficultyProvider()
            );
        } else {
            LOGGER.warn("ImprovedMobs not found! IIC requires ImprovedMobs to function.");
        }

        // Initialize TriAxis config
        TriAxisConfig.load();
    }

    private void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getType() == ModConfig.Type.COMMON) {
            TriAxisConfig.load();
        }
    }

    private void registerTurretsDataTickets() {
        LaserTurretBlockEntity.HAS_TARGET = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofBoolean(new ResourceLocation(IntegratedIndustrialCraft.MODID, "has_target")));
        LaserTurretBlockEntity.TARGET_POS_X = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofDouble(new ResourceLocation(IntegratedIndustrialCraft.MODID, "target_pos_x")));
        LaserTurretBlockEntity.TARGET_POS_Y = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofDouble(new ResourceLocation(IntegratedIndustrialCraft.MODID, "target_pos_y")));
        LaserTurretBlockEntity.TARGET_POS_Z = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofDouble(new ResourceLocation(IntegratedIndustrialCraft.MODID, "target_pos_z")));
    }
}
