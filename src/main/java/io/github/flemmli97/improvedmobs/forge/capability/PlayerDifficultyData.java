package io.github.flemmli97.improvedmobs.forge.capability;

import io.github.flemmli97.improvedmobs.difficulty.PlayerDifficulty;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerDifficultyData extends PlayerDifficulty implements ICapabilitySerializable<CompoundTag> {

    private final LazyOptional<PlayerDifficulty> holder = LazyOptional.of(() -> this);

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction arg) {
        return CapabilityProvider.PLAYER_CAP.orEmpty(capability, this.holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        return this.save(new CompoundTag());
    }

    @Override
    public void deserializeNBT(CompoundTag arg) {
        this.load(arg);
    }
}
