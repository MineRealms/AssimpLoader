package cn.minerealms.iic.turrets.common.block_entity;

import mekanism.api.tier.BaseTier;
import mekanism.api.tier.ITier;

import java.util.function.Supplier;

/**
 * 火焰喷射器炮塔等级（只有一个等级）
 *
 * 参数说明：
 * - cooldown: 冷却时间（tick）
 * - damage: 每tick伤害
 * - fuelCapacity: 燃料容量（mB）
 * - range: 射程（格）
 */
public enum FlameThrowerTurretTier implements ITier {
    BASIC(BaseTier.BASIC, 5, 1.0, 16000, 32); // 5 tick冷却 = 0.25秒，高射速

    private final BaseTier baseTier;
    private final int cooldown;
    private final double damage;
    private final int fuelCapacity;
    private final double range;
    private Integer cooldownReference;
    private Double damageReference;
    private Integer fuelCapacityReference;
    private Double rangeReference;

    FlameThrowerTurretTier(BaseTier baseTier, int cooldown, double damage, int fuelCapacity, double range) {
        this.baseTier = baseTier;
        this.cooldown = cooldown;
        this.damage = damage;
        this.fuelCapacity = fuelCapacity;
        this.range = range;
    }

    @Override
    public BaseTier getBaseTier() {
        return baseTier;
    }

    public int getCooldown() {
        return cooldownReference == null ? getBaseCooldown() : cooldownReference;
    }

    private int getBaseCooldown() {
        return cooldown;
    }

    public double getDamage() {
        return damageReference == null ? getBaseDamage() : damageReference;
    }

    private double getBaseDamage() {
        return damage;
    }

    public int getFuelCapacity() {
        return fuelCapacityReference == null ? getBaseFuelCapacity() : fuelCapacityReference;
    }

    private int getBaseFuelCapacity() {
        return fuelCapacity;
    }

    public double getRange() {
        return rangeReference == null ? getBaseRange() : rangeReference;
    }

    private double getBaseRange() {
        return range;
    }

    public void setConfigReference(Supplier<Integer> cooldownReference, Supplier<Double> damageReference,
                                   Supplier<Integer> fuelCapacityReference, Supplier<Double> rangeReference) {
        this.cooldownReference = cooldownReference.get();
        this.damageReference = damageReference.get();
        this.fuelCapacityReference = fuelCapacityReference.get();
        this.rangeReference = rangeReference.get();
    }
}
