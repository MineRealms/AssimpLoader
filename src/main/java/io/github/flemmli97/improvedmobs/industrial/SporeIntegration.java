package io.github.flemmli97.improvedmobs.industrial;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class SporeIntegration {

    private static boolean checkDone = false;
    private static boolean isSporeLoaded = false;
    
    private static boolean isSporeLoaded() {
        if (!checkDone) {
            try {
                Class.forName("com.Harbinger.Spore.ExtremelySusThings.SporeSavedData");
                isSporeLoaded = true;
            } catch (ClassNotFoundException e) {
                isSporeLoaded = false;
            }
            checkDone = true;
        }
        return isSporeLoaded;
    }

    public static boolean isSporeMob(LivingEntity entity) {
        if (!isSporeLoaded()) return false;
        try {
            return entity.getType().getCategory().getName().toLowerCase().contains("spore") || 
                   entity.getType().getDescriptionId().contains("spore");
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getActiveHiveminds(ServerLevel level) {
        if (!isSporeLoaded()) return 0;
        try {
            Object data = Class.forName("com.Harbinger.Spore.ExtremelySusThings.SporeSavedData")
                .getMethod("get", ServerLevel.class)
                .invoke(null, level);
            if (data != null) {
                return (int) data.getClass().getMethod("getAmountOfHiveminds").invoke(data);
            }
        } catch (Exception e) {
            // Ignored
        }
        return 0;
    }

    public static void buffSporeMob(Mob mob, double pollutionLevel, double localVoltageTier) {
        if (!isSporeLoaded()) return;
        try {
            if (isSporeMob((LivingEntity) mob)) {
                double pollutionBonus = pollutionLevel / 100.0;
                double voltageBonus = Math.max(0, localVoltageTier - 1) * 0.10;
                double totalMultiplier = 1.0 + pollutionBonus + voltageBonus;
                
                AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(health.getBaseValue() * totalMultiplier);
                    mob.setHealth(mob.getMaxHealth());
                }
                
                AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damage != null && pollutionLevel > 50) {
                    damage.setBaseValue(damage.getBaseValue() + (pollutionLevel / 50.0) * 0.5);
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
    }
}