package io.github.flemmli97.improvedmobs.industrial;

import com.Harbinger.Spore.ExtremelySusThings.SporeSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

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

    public static void buffSporeMob(Mob mob, double pollutionLevel, double localVoltageTier) {
        // If it's a Spore mob, we can give it extra buffs based on pollution and voltage
        if (isSporeMob(mob)) {
            // Base: Pollution = +1% HP per 1 point
            // Extra: Voltage Tier = +10% HP per tier above LV
            double pollutionBonus = pollutionLevel / 100.0;
            double voltageBonus = Math.max(0, localVoltageTier - 1) * 0.10;
            double totalMultiplier = 1.0 + pollutionBonus + voltageBonus;
            
            AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(health.getBaseValue() * totalMultiplier);
                mob.setHealth(mob.getMaxHealth());
            }
            
            // Also boost attack damage slightly based on pollution
            AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null && pollutionLevel > 50) {
                // Extra 0.5 damage per 50 pollution
                damage.setBaseValue(damage.getBaseValue() + (pollutionLevel / 50.0) * 0.5);
            }
        }
    }
}