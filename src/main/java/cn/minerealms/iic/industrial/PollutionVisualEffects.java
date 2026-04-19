package cn.minerealms.iic.industrial;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pollution Visual Effects - Client-side EnhancedVisuals Integration
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Triggers visual effects based on chunk pollution levels</li>
 *   <li>Applies nausea/blindness debuffs at high pollution</li>
 *   <li>Client-side only (called from {@link cn.minerealms.iic.mixin.enhancedvisuals.VisualManagerMixin})</li>
 *   <li>Thread-safe cooldown tracking</li>
 * </ul>
 *
 * <h2>Pollution Thresholds</h2>
 * <ul>
 *   <li>50-100: Light pollution (green particles)</li>
 *   <li>100-150: Moderate pollution (yellow particles + nausea I)</li>
 *   <li>150-200: Heavy pollution (orange particles + nausea II)</li>
 *   <li>200+: Severe pollution (red particles + nausea III + blindness)</li>
 * </ul>
 *
 * @author ImprovedMobs Team
 */
@OnlyIn(Dist.CLIENT)
public class PollutionVisualEffects {

    private static final Logger LOGGER = LogManager.getLogger("PollutionVisuals");
    private static final Random RANDOM = new Random();

    // ==================== Configuration ====================

    /** Effect cooldown per player (ticks) - 5 seconds */
    private static final int EFFECT_COOLDOWN = 100;

    /** Pollution thresholds */
    private static final double LIGHT_POLLUTION_THRESHOLD = 50.0;
    private static final double MODERATE_POLLUTION_THRESHOLD = 100.0;
    private static final double HEAVY_POLLUTION_THRESHOLD = 150.0;
    private static final double SEVERE_POLLUTION_THRESHOLD = 200.0;

    // ==================== Runtime State ====================

    /** Last effect trigger time per player (thread-safe) */
    private static final Map<UUID, Long> lastEffectTick = new ConcurrentHashMap<>();

    /** Debug mode toggle */
    private static boolean debugEnabled = false;

    // ==================== Effect Triggering ====================

    /**
     * Check and trigger pollution effects for a player.
     * Called from client-side mixin every tick.
     *
     * @param player the player to check
     * @param pollution the pollution level at player's location
     */
    public static void checkAndTriggerEffects(Player player, double pollution) {
        if (pollution < LIGHT_POLLUTION_THRESHOLD) {
            return;
        }

        // Check cooldown
        UUID playerUUID = player.getUUID();
        long currentTick = Minecraft.getInstance().level.getGameTime();
        Long lastTick = lastEffectTick.get(playerUUID);

        if (lastTick != null && currentTick - lastTick < EFFECT_COOLDOWN) {
            return;  // Still on cooldown
        }

        lastEffectTick.put(playerUUID, currentTick);

        // Trigger effects based on pollution level
        try {
            if (pollution >= SEVERE_POLLUTION_THRESHOLD) {
                triggerSevereEffects(player, pollution);
            } else if (pollution >= HEAVY_POLLUTION_THRESHOLD) {
                triggerHeavyEffects(player, pollution);
            } else if (pollution >= MODERATE_POLLUTION_THRESHOLD) {
                triggerModerateEffects(player, pollution);
            } else {
                triggerLightEffects(player, pollution);
            }

            debugLog(String.format(
                "[PollutionVisuals] Triggered effects for %s at pollution %.1f",
                player.getName().getString(), pollution
            ));

        } catch (Exception e) {
            LOGGER.error("[PollutionVisuals] Error triggering effects: " + e.getMessage());
            if (debugEnabled) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Trigger light pollution effects (50-100 pollution)
     */
    private static void triggerLightEffects(Player player, double pollution) {
        try {
            // Delegate to EnhancedVisuals helper
            LOGGER.info("[PollutionVisualEffects] Calling EnhancedVisualsHelper.triggerLightEffects()");
            cn.minerealms.iic.integration.enhancedvisuals.EnhancedVisualsHelper.triggerLightEffects();
            debugLog(String.format("Light effects triggered: pollution=%.1f", pollution));
        } catch (NoClassDefFoundError e) {
            LOGGER.error("[PollutionVisualEffects] EnhancedVisualsHelper class not found - EnhancedVisuals may not be installed", e);
        } catch (Exception e) {
            LOGGER.error("[PollutionVisualEffects] Error triggering light effects", e);
        }
    }

    /**
     * Trigger moderate pollution effects (100-150 pollution)
     */
    private static void triggerModerateEffects(Player player, double pollution) {
        try {
            // Delegate to EnhancedVisuals helper
            LOGGER.info("[PollutionVisualEffects] Calling EnhancedVisualsHelper.triggerModerateEffects()");
            cn.minerealms.iic.integration.enhancedvisuals.EnhancedVisualsHelper.triggerModerateEffects();

            // Apply Nausea I (10 seconds)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false));

            debugLog(String.format("Moderate effects triggered: pollution=%.1f", pollution));
        } catch (NoClassDefFoundError e) {
            LOGGER.error("[PollutionVisualEffects] EnhancedVisualsHelper class not found", e);
        } catch (Exception e) {
            LOGGER.error("[PollutionVisualEffects] Error triggering moderate effects", e);
        }
    }

    /**
     * Trigger heavy pollution effects (150-200 pollution)
     */
    private static void triggerHeavyEffects(Player player, double pollution) {
        try {
            // Delegate to EnhancedVisuals helper
            LOGGER.info("[PollutionVisualEffects] Calling EnhancedVisualsHelper.triggerHeavyEffects()");
            cn.minerealms.iic.integration.enhancedvisuals.EnhancedVisualsHelper.triggerHeavyEffects();

            // Apply Nausea II (15 seconds)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 1, false, false));

            debugLog(String.format("Heavy effects triggered: pollution=%.1f", pollution));
        } catch (NoClassDefFoundError e) {
            LOGGER.error("[PollutionVisualEffects] EnhancedVisualsHelper class not found", e);
        } catch (Exception e) {
            LOGGER.error("[PollutionVisualEffects] Error triggering heavy effects", e);
        }
    }

    /**
     * Trigger severe pollution effects (200+ pollution)
     */
    private static void triggerSevereEffects(Player player, double pollution) {
        try {
            // Delegate to EnhancedVisuals helper
            LOGGER.info("[PollutionVisualEffects] Calling EnhancedVisualsHelper.triggerSevereEffects()");
            cn.minerealms.iic.integration.enhancedvisuals.EnhancedVisualsHelper.triggerSevereEffects();

            // Apply Nausea III (20 seconds)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 2, false, false));

            // Apply Blindness (5 seconds) for severe pollution
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false));

            debugLog(String.format("Severe effects triggered: pollution=%.1f", pollution));
        } catch (NoClassDefFoundError e) {
            LOGGER.error("[PollutionVisualEffects] EnhancedVisualsHelper class not found", e);
        } catch (Exception e) {
            LOGGER.error("[PollutionVisualEffects] Error triggering severe effects", e);
        }
    }

    // ==================== Test Command Support ====================

    /**
     * Trigger a specific effect type for testing.
     * Called from command on client side.
     *
     * @param player the player to apply effects to
     * @param effectType effect type: "light", "moderate", "heavy", "severe", "all"
     */
    public static void triggerTestEffect(Player player, String effectType) {
        try {
            debugLog(String.format("Triggering test effect '%s' for %s", effectType, player.getName().getString()));

            switch (effectType.toLowerCase()) {
                case "light" -> triggerLightEffects(player, 75.0);
                case "moderate" -> triggerModerateEffects(player, 125.0);
                case "heavy" -> triggerHeavyEffects(player, 175.0);
                case "severe" -> triggerSevereEffects(player, 250.0);
                case "all" -> {
                    triggerLightEffects(player, 75.0);
                    triggerModerateEffects(player, 125.0);
                    triggerHeavyEffects(player, 175.0);
                    triggerSevereEffects(player, 250.0);
                }
                default -> LOGGER.warn("[PollutionVisuals] Unknown effect type: " + effectType);
            }

            LOGGER.info(String.format("[PollutionVisuals] Test effect '%s' triggered for %s",
                effectType, player.getName().getString()));

        } catch (Exception e) {
            LOGGER.error("[PollutionVisuals] Error triggering test effect: " + e.getMessage());
            if (debugEnabled) {
                e.printStackTrace();
            }
        }
    }

    // ==================== Debug Control ====================

    /**
     * Enable or disable debug logging.
     *
     * @param enabled true to enable debug logging
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            LOGGER.info("[PollutionVisuals] Debug logging ENABLED");
        } else {
            LOGGER.info("[PollutionVisuals] Debug logging DISABLED");
        }
    }

    /**
     * Check if debug logging is enabled.
     *
     * @return true if debug logging is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Log debug message (only if debug enabled)
     */
    private static void debugLog(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG] " + message);
        }
    }

    /**
     * Get diagnostic information about the integration.
     *
     * @return diagnostic string
     */
    public static String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Pollution Visual Effects Diagnostics ===\n");
        sb.append("EnhancedVisuals Integration: ACTIVE (Mixin-based)\n");
        sb.append("Debug Enabled: ").append(debugEnabled).append("\n");
        sb.append("Active Cooldowns: ").append(lastEffectTick.size()).append("\n");
        sb.append("Thresholds:\n");
        sb.append("  - Light: ").append(LIGHT_POLLUTION_THRESHOLD).append("\n");
        sb.append("  - Moderate: ").append(MODERATE_POLLUTION_THRESHOLD).append("\n");
        sb.append("  - Heavy: ").append(HEAVY_POLLUTION_THRESHOLD).append("\n");
        sb.append("  - Severe: ").append(SEVERE_POLLUTION_THRESHOLD).append("\n");

        return sb.toString();
    }
}
