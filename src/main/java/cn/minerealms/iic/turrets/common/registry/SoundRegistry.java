package cn.minerealms.iic.turrets.common.registry;
import cn.minerealms.iic.IntegratedIndustrialCraft;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegistry {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, IntegratedIndustrialCraft.MODID);

    public static final RegistryObject<SoundEvent> TURRET_SHOOT = SOUNDS.register("turret_shoot",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(IntegratedIndustrialCraft.MODID, "turret_shoot")));
}
