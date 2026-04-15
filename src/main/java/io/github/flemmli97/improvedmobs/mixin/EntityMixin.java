package io.github.flemmli97.improvedmobs.mixin;

import io.github.flemmli97.improvedmobs.mixinhelper.IEntityData;
import io.github.flemmli97.improvedmobs.utils.EntityFlags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntityData {

    @Unique
    private final EntityFlags imFlags = new EntityFlags();

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readData(CompoundTag compoundTag, CallbackInfo info) {
        if (compoundTag.contains("IMFlags")) {
            this.imFlags.load(compoundTag.getCompound("IMFlags"));
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveData(CompoundTag compoundTag, CallbackInfo info) {
        compoundTag.put(EntityFlags.TAG_ID, this.imFlags.save());
    }

    @Override
    public EntityFlags getFlags() {
        return this.imFlags;
    }
}
