package io.github.flemmli97.improvedmobs.forge;

import io.github.flemmli97.improvedmobs.ImprovedMobs;
import io.github.flemmli97.improvedmobs.ai.util.ItemAITasks;
import io.github.flemmli97.improvedmobs.api.difficulty.DifficultyFetcher;
import io.github.flemmli97.improvedmobs.config.EquipmentList;
import io.github.flemmli97.improvedmobs.forge.capability.CapabilityProvider;
import io.github.flemmli97.improvedmobs.forge.client.ClientEventHandler;
import io.github.flemmli97.improvedmobs.forge.config.ConfigLoader;
import io.github.flemmli97.improvedmobs.forge.config.ConfigSpecs;
import io.github.flemmli97.improvedmobs.forge.events.DifficultyHandler;
import io.github.flemmli97.improvedmobs.forge.events.EventHandler;
import io.github.flemmli97.improvedmobs.forge.integration.difficulty.ScalingHealthDifficulty;
import io.github.flemmli97.improvedmobs.forge.network.PacketHandler;
import io.github.flemmli97.improvedmobs.scanner.ScannerItem;
import io.github.flemmli97.improvedmobs.mekanism_turrets.MekanismTurretsConfig;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.block_entity.LaserTurretBlockEntity;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.block_entity.LaserTurretTier;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.entity.LaserEntity;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.packet.MekanismTurretsPacketHandler;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry.*;
import software.bernie.geckolib.network.SerializableDataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
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

import java.io.File;

@Mod.EventBusSubscriber
@Mod(value = ImprovedMobs.MODID)
public class ImprovedMobsForge {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ImprovedMobs.MODID);
    public static final RegistryObject<Item> TERRAIN_SCANNER = ITEMS.register("terrain_scanner", ScannerItem::new);

    public ImprovedMobsForge() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> "*", (s1, s2) -> true));
        File file = FMLPaths.CONFIGDIR.get().resolve("improvedmobs").toFile();
        if (!file.exists())
            file.mkdir();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigSpecs.clientSpec, "improvedmobs/client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigSpecs.commonSpec, "improvedmobs/common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MekanismTurretsConfig.SPEC);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ImprovedMobsForge::setup);
        modBus.addListener(ImprovedMobsForge::conf);
        modBus.addListener(ImprovedMobsForge::loadTurretsConfig);
        modBus.addListener(CapabilityProvider::register);
        ITEMS.register(modBus);

        // Register Mekanism Turrets components
        BlockRegistry.BLOCKS.register(modBus);
        BlockEntityTypeRegistry.BLOCK_ENTITY_TYPES.register(modBus);
        ContainerTypeRegistry.CONTAINER_TYPES.register(modBus);
        EntityRegistry.ENTITY_TYPES.register(modBus);
        ItemRegistry.ITEMS.register(modBus);
        ItemRegistry.ModItemTab.CREATIVE_MODE_TABS.register(modBus);
        SoundRegistry.SOUNDS.register(modBus);

        if (FMLEnvironment.dist == Dist.CLIENT)
            ClientEventHandler.setup();
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        MinecraftForge.EVENT_BUS.addListener(LaserEntity::enterChunk);

        DifficultyFetcher.register();
        if (ModList.get().isLoaded("scalinghealth"))
            DifficultyFetcher.add(new ResourceLocation(ImprovedMobs.MODID, "scalinghealth_integration"), new ScalingHealthDifficulty());
        if (ModList.get().isLoaded("gtceu"))
            DifficultyFetcher.add(new ResourceLocation(ImprovedMobs.MODID, "industrial_integration"), new io.github.flemmli97.improvedmobs.industrial.IndustrialDifficultyGetter());

        MekanismTurretsPacketHandler.registerPackets();
        registerTurretsDataTickets();
    }

    static void setup(FMLCommonSetupEvent event) {
        PacketHandler.register();
        ItemAITasks.initAI();
        try {
            EquipmentList.initEquip();
        } catch (EquipmentList.InvalidItemNameException e) {
            ImprovedMobs.logger.error(e.getMessage());
        }
        MinecraftForge.EVENT_BUS.register(new DifficultyHandler());
    }

    static void conf(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ConfigSpecs.clientSpec)
            ConfigLoader.loadClient();
        else if (event.getConfig().getSpec() == ConfigSpecs.commonSpec)
            ConfigLoader.loadCommon();
    }

    static void loadTurretsConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == MekanismTurretsConfig.SPEC) {
            LaserTurretTier.BASIC.setConfigReference(MekanismTurretsConfig.basicLaserTurretCooldown, MekanismTurretsConfig.basicLaserTurretDamage, MekanismTurretsConfig.basicLaserTurretEnergyCapacity, MekanismTurretsConfig.basicLaserTurretRange);
            LaserTurretTier.ADVANCED.setConfigReference(MekanismTurretsConfig.advancedLaserTurretCooldown, MekanismTurretsConfig.advancedLaserTurretDamage, MekanismTurretsConfig.advancedLaserTurretEnergyCapacity, MekanismTurretsConfig.advancedLaserTurretRange);
            LaserTurretTier.ELITE.setConfigReference(MekanismTurretsConfig.eliteLaserTurretCooldown, MekanismTurretsConfig.eliteLaserTurretDamage, MekanismTurretsConfig.eliteLaserTurretEnergyCapacity, MekanismTurretsConfig.eliteLaserTurretRange);
            LaserTurretTier.ULTIMATE.setConfigReference(MekanismTurretsConfig.ultimateLaserTurretCooldown, MekanismTurretsConfig.ultimateLaserTurretDamage, MekanismTurretsConfig.ultimateLaserTurretEnergyCapacity, MekanismTurretsConfig.ultimateLaserTurretRange);
        }
    }

    private static void registerTurretsDataTickets() {
        LaserTurretBlockEntity.HAS_TARGET = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofBoolean(new ResourceLocation(ImprovedMobs.MODID, "has_target")));
        LaserTurretBlockEntity.TARGET_POS_X = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofDouble(new ResourceLocation(ImprovedMobs.MODID, "target_pos_x")));
        LaserTurretBlockEntity.TARGET_POS_Y = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofDouble(new ResourceLocation(ImprovedMobs.MODID, "target_pos_y")));
        LaserTurretBlockEntity.TARGET_POS_Z = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofDouble(new ResourceLocation(ImprovedMobs.MODID, "target_pos_z")));
    }
}
