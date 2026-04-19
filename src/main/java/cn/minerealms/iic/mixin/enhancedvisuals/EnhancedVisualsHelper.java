package cn.minerealms.iic.mixin.enhancedvisuals;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.creative.enhancedvisuals.api.Visual;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualTypeOverlay;
import team.creative.enhancedvisuals.client.VisualManager;

import java.util.Collection;
import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class EnhancedVisualsHelper {

    private static final Logger LOGGER = LogManager.getLogger("EnhancedVisualsHelper");
    private static final Random RANDOM = new Random();

    private static VisualTypeOverlay lightPollutionType;
    private static VisualTypeOverlay moderatePollutionType;
    private static VisualTypeOverlay heavyPollutionType;
    private static VisualTypeOverlay severePollutionType;

    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }

        try {
            lightPollutionType = new VisualTypeOverlay("light_pollution");
            moderatePollutionType = new VisualTypeOverlay("moderate_pollution");
            heavyPollutionType = new VisualTypeOverlay("heavy_pollution");
            severePollutionType = new VisualTypeOverlay("severe_pollution");
            initialized = true;
            LOGGER.info("[EnhancedVisualsHelper] Initialized visual types");
        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Failed to initialize visual types", e);
        }
    }

    public static void triggerLightEffects() {
        init();
        try {
            VisualManager.addParticlesFadeOut(lightPollutionType, null, 3, 40, false);
            LOGGER.info("[EnhancedVisualsHelper] Light pollution effects triggered");
        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering light effects", e);
        }
    }

    public static void triggerModerateEffects() {
        init();
        try {
            VisualManager.addParticlesFadeOut(moderatePollutionType, null, 5, 60, false);
            LOGGER.info("[EnhancedVisualsHelper] Moderate pollution effects triggered");
        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering moderate effects", e);
        }
    }

    public static void triggerHeavyEffects() {
        init();
        try {
            VisualManager.addParticlesFadeOut(heavyPollutionType, null, 8, 80, false);
            LOGGER.info("[EnhancedVisualsHelper] Heavy pollution effects triggered");
        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering heavy effects", e);
        }
    }

    public static void triggerSevereEffects() {
        init();
        try {
            VisualManager.addParticlesFadeOut(severePollutionType, null, 12, 100, false);
            LOGGER.info("[EnhancedVisualsHelper] Severe pollution effects triggered");
        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering severe effects", e);
        }
    }

    public static boolean isEnhancedVisualsPresent() {
        try {
            Class.forName("team.creative.enhancedvisuals.client.VisualManager", false, EnhancedVisualsHelper.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}