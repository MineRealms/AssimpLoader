package io.github.flemmli97.improvedmobs.industrial;

import com.Harbinger.Spore.ExtremelySusThings.SporeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public class SporeIntegration {

    public static boolean isSporeMob(LivingEntity entity) {
        return entity.getType().getCategory().getName().toLowerCase().contains("spore") || 
               entity.getType().getDescriptionId().contains("spore");
    }

    public static int getActiveHiveminds(ServerLevel level) {
        try {
            SporeSavedData data = SporeSavedData.get(level);
            if (data != null) {
                return data.getAmountOfHiveminds();
            }
        } catch (Exception e) {
            // Ignored
        }
        return 0;
    }

    public static void buffSporeMob(Mob mob, double pollutionLevel) {
        // If it's a Spore mob, we can give it extra buffs based on pollution
        if (isSporeMob(mob)) {
            // Extra HP or damage can be applied here on top of Improved Mobs base buffs
            // 比如：每 10 点全局污染，额外增加 10% 血量
            double extraHealthMultiplier = 1.0 + (pollutionLevel / 100.0);
            
            net.minecraft.world.entity.ai.attributes.AttributeInstance health = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(health.getBaseValue() * extraHealthMultiplier);
                mob.setHealth(mob.getMaxHealth());
            }
        }
    }
}