package cn.minerealms.iic.turrets.common.block_entity;

import mekanism.common.tile.component.ITileComponent;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * 火焰喷射器炮塔升级数据
 * 用于在炮塔升级时保存和恢复配置
 */
public final class FlameThrowerTurretUpgradeData implements IUpgradeData {
    private final boolean targetsHostile;
    private final boolean targetsPassive;
    private final boolean targetsPlayers;
    private final boolean targetsTrusted;
    private final CompoundTag components;

    public FlameThrowerTurretUpgradeData(boolean targetsHostile, boolean targetsPassive, boolean targetsPlayers, boolean targetsTrusted, @NotNull List<ITileComponent> components) {
        this.targetsHostile = targetsHostile;
        this.targetsPassive = targetsPassive;
        this.targetsPlayers = targetsPlayers;
        this.targetsTrusted = targetsTrusted;
        this.components = new CompoundTag();
        for (ITileComponent component : components) {
            component.write(this.components);
        }
    }

    public boolean targetsHostile() {
        return targetsHostile;
    }

    public boolean targetsPassive() {
        return targetsPassive;
    }

    public boolean targetsPlayers() {
        return targetsPlayers;
    }

    public boolean targetsTrusted() {
        return targetsTrusted;
    }

    public CompoundTag components() {
        return components;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FlameThrowerTurretUpgradeData) obj;
        return this.targetsHostile == that.targetsHostile &&
                this.targetsPassive == that.targetsPassive &&
                this.targetsPlayers == that.targetsPlayers &&
                this.targetsTrusted == that.targetsTrusted &&
                Objects.equals(this.components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetsHostile, targetsPassive, targetsPlayers, targetsTrusted, components);
    }

    @Override
    public String toString() {
        return "FlameThrowerTurretUpgradeData[" +
                "targetsHostile=" + targetsHostile + ", " +
                "targetsPassive=" + targetsPassive + ", " +
                "targetsPlayers=" + targetsPlayers + ", " +
                "targetsTrusted=" + targetsTrusted + ", " +
                "components=" + components + ']';
    }
}
