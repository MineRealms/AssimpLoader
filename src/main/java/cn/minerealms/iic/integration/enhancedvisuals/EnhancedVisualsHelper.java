package cn.minerealms.iic.integration.enhancedvisuals;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * EnhancedVisuals integration helper using reflection to avoid class loading issues.
 *
 * This class uses reflection to call EnhancedVisuals API methods, avoiding direct
 * class references that could cause NoClassDefFoundError at runtime.
 *
 * NOTE: @OnlyIn removed to avoid class loading issues. Runtime side check is used instead.
 */
public class EnhancedVisualsHelper {

    private static final Logger LOGGER = LogManager.getLogger("EnhancedVisualsHelper");

    static {
        System.out.println("========================================");
        System.out.println("EnhancedVisualsHelper CLASS LOADED!");
        System.out.println("========================================");
        LOGGER.error("========================================");
        LOGGER.error("EnhancedVisualsHelper CLASS LOADED!");
        LOGGER.error("========================================");
    }

    // Cached reflection objects
    private static Class<?> visualManagerClass;
    private static Class<?> visualRegistryClass;
    private static Class<?> damageHandlerClass;
    private static Class<?> colorClass;
    private static Class<?> intMinMaxClass;
    private static Method addParticlesFadeOutMethod;
    private static Method handlersMethod;
    private static boolean initialized = false;
    private static boolean initFailed = false;

    /**
     * Initialize reflection objects (called once)
     */
    private static void init() {
        if (initialized || initFailed) {
            return;
        }

        try {
            LOGGER.info("[EnhancedVisualsHelper] Initializing reflection...");

            // Load classes
            visualManagerClass = Class.forName("team.creative.enhancedvisuals.client.VisualManager");
            visualRegistryClass = Class.forName("team.creative.enhancedvisuals.common.visual.VisualRegistry");
            damageHandlerClass = Class.forName("team.creative.enhancedvisuals.common.handler.DamageHandler");
            colorClass = Class.forName("team.creative.creativecore.common.util.type.Color");
            intMinMaxClass = Class.forName("team.creative.creativecore.common.config.premade.IntMinMax");

            // Get methods
            handlersMethod = visualRegistryClass.getMethod("handlers");
            addParticlesFadeOutMethod = visualManagerClass.getMethod(
                "addParticlesFadeOut",
                Class.forName("team.creative.enhancedvisuals.api.type.VisualType"),
                Class.forName("team.creative.enhancedvisuals.api.VisualHandler"),
                int.class,
                intMinMaxClass,
                boolean.class,
                colorClass
            );

            initialized = true;
            LOGGER.info("[EnhancedVisualsHelper] Reflection initialized successfully");

        } catch (Throwable e) {
            initFailed = true;
            LOGGER.error("[EnhancedVisualsHelper] Failed to initialize reflection", e);
        }
    }

    /**
     * Get DamageHandler instance from VisualRegistry
     */
    private static Object getDamageHandler() {
        try {
            Object handlersIterable = handlersMethod.invoke(null);
            for (Object handler : (Iterable<?>) handlersIterable) {
                if (damageHandlerClass.isInstance(handler)) {
                    return handler;
                }
            }
        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error getting DamageHandler", e);
        }
        return null;
    }

    /**
     * Trigger light pollution effects (green particles)
     */
    public static void triggerLightEffects() {
        System.out.println("=== SYSTEM.OUT: triggerLightEffects() ENTRY ===");
        System.err.println("=== SYSTEM.ERR: triggerLightEffects() ENTRY ===");
        LOGGER.error("[EnhancedVisualsHelper] === ERROR LEVEL: triggerLightEffects() ENTRY ===");
        LOGGER.info("[EnhancedVisualsHelper] === triggerLightEffects() ENTRY ===");

        try {
            init();
            if (initFailed) {
                LOGGER.error("[EnhancedVisualsHelper] Cannot trigger effects - initialization failed");
                return;
            }

            LOGGER.info("[EnhancedVisualsHelper] Step 1: Getting DamageHandler...");
            Object handler = getDamageHandler();
            if (handler == null) {
                LOGGER.error("[EnhancedVisualsHelper] DamageHandler not found!");
                return;
            }
            LOGGER.info("[EnhancedVisualsHelper] Step 2: DamageHandler found");

            // Get waterDrown field from DamageHandler
            LOGGER.info("[EnhancedVisualsHelper] Step 3: Getting waterDrown visual type...");
            Field waterDrownField = damageHandlerClass.getField("waterDrown");
            Object visualType = waterDrownField.get(handler);
            LOGGER.info("[EnhancedVisualsHelper] Step 4: Visual type obtained");

            // Create Color (100, 255, 100, 150) - green
            LOGGER.info("[EnhancedVisualsHelper] Step 5: Creating green color...");
            Object greenColor = colorClass.getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(100, 255, 100, 150);

            // Create IntMinMax(200, 400)
            LOGGER.info("[EnhancedVisualsHelper] Step 6: Creating duration range...");
            Object duration = intMinMaxClass.getConstructor(int.class, int.class)
                .newInstance(200, 400);

            // Call VisualManager.addParticlesFadeOut
            LOGGER.info("[EnhancedVisualsHelper] Step 7: Calling addParticlesFadeOut...");
            addParticlesFadeOutMethod.invoke(null, visualType, handler, 15, duration, true, greenColor);

            LOGGER.info("[EnhancedVisualsHelper] Step 8: SUCCESS - Light pollution effects triggered!");

        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering light effects", e);
        }
    }

    /**
     * Trigger moderate pollution effects (yellow particles)
     */
    public static void triggerModerateEffects() {
        LOGGER.info("[EnhancedVisualsHelper] === triggerModerateEffects() ENTRY ===");

        try {
            init();
            if (initFailed) return;

            Object handler = getDamageHandler();
            if (handler == null) return;

            Field waterDrownField = damageHandlerClass.getField("waterDrown");
            Object visualType = waterDrownField.get(handler);

            // Yellow color (255, 255, 100, 180)
            Object yellowColor = colorClass.getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(255, 255, 100, 180);

            Object duration = intMinMaxClass.getConstructor(int.class, int.class)
                .newInstance(300, 500);

            addParticlesFadeOutMethod.invoke(null, visualType, handler, 20, duration, true, yellowColor);

            LOGGER.info("[EnhancedVisualsHelper] Moderate pollution effects triggered (yellow)");

        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering moderate effects", e);
        }
    }

    /**
     * Trigger heavy pollution effects (orange particles)
     */
    public static void triggerHeavyEffects() {
        LOGGER.info("[EnhancedVisualsHelper] === triggerHeavyEffects() ENTRY ===");

        try {
            init();
            if (initFailed) return;

            Object handler = getDamageHandler();
            if (handler == null) return;

            Field waterDrownField = damageHandlerClass.getField("waterDrown");
            Object visualType = waterDrownField.get(handler);

            // Orange color (255, 150, 50, 200)
            Object orangeColor = colorClass.getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(255, 150, 50, 200);

            Object duration = intMinMaxClass.getConstructor(int.class, int.class)
                .newInstance(400, 600);

            addParticlesFadeOutMethod.invoke(null, visualType, handler, 25, duration, true, orangeColor);

            LOGGER.info("[EnhancedVisualsHelper] Heavy pollution effects triggered (orange)");

        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering heavy effects", e);
        }
    }

    /**
     * Trigger severe pollution effects (red particles)
     */
    public static void triggerSevereEffects() {
        LOGGER.info("[EnhancedVisualsHelper] === triggerSevereEffects() ENTRY ===");

        try {
            init();
            if (initFailed) return;

            Object handler = getDamageHandler();
            if (handler == null) return;

            Field waterDrownField = damageHandlerClass.getField("waterDrown");
            Object visualType = waterDrownField.get(handler);

            // Red color (255, 50, 50, 220)
            Object redColor = colorClass.getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(255, 50, 50, 220);

            Object duration = intMinMaxClass.getConstructor(int.class, int.class)
                .newInstance(500, 800);

            addParticlesFadeOutMethod.invoke(null, visualType, handler, 35, duration, true, redColor);

            LOGGER.info("[EnhancedVisualsHelper] Severe pollution effects triggered (red)");

        } catch (Throwable e) {
            LOGGER.error("[EnhancedVisualsHelper] Error triggering severe effects", e);
        }
    }

    /**
     * Check if EnhancedVisuals is present
     */
    public static boolean isEnhancedVisualsPresent() {
        try {
            Class.forName("team.creative.enhancedvisuals.client.VisualManager", false, EnhancedVisualsHelper.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
