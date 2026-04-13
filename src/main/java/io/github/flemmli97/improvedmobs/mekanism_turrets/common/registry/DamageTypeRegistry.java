package io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public class DamageTypeRegistry {

    private final Registry<DamageType> damageTypes;
    private final DamageSource electricFence;
    private final DamageSource laser;

    public DamageTypeRegistry(RegistryAccess registryAccess) {
        this.damageTypes = registryAccess.registryOrThrow(Registries.DAMAGE_TYPE);
        this.electricFence = source(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ImprovedMobs.MODID, "electric_fence")));
        this.laser = source(ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(ImprovedMobs.MODID, "laser")));
    }

    private DamageSource source(ResourceKey<DamageType> pDamageTypeKey) {
        return new DamageSource(this.damageTypes.getHolderOrThrow(pDamageTypeKey));
    }

    public DamageSource electricFence() {
        return this.electricFence;
    }
    public DamageSource laser() {
        return this.laser;
    }
}
