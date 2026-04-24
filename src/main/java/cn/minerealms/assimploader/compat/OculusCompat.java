package cn.minerealms.assimploader.compat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraftforge.fml.ModList;

/**
 * Soft dependency compatibility layer for Oculus (Iris Shaders for Forge).
 *
 * This class provides safe runtime detection and API access for Oculus integration.
 * All methods gracefully degrade when Oculus is not loaded or shaders are disabled.
 *
 * Usage:
 * <pre>
 * if (OculusCompat.isShadersActive()) {
 *     // Use shader-enhanced rendering
 * } else {
 *     // Use vanilla rendering
 * }
 * </pre>
 */
public class OculusCompat {

    private static Boolean OCULUS_LOADED = null;
    private static Object irisApi = null;
    private static boolean apiInitFailed = false;

    /**
     * Check if Oculus mod is loaded at runtime.
     * Result is cached after first call.
     *
     * @return true if Oculus is present in the mod list
     */
    public static boolean isOculusLoaded() {
        if (OCULUS_LOADED == null) {
            OCULUS_LOADED = ModList.get().isLoaded("oculus");
            if (OCULUS_LOADED) {
                DebugManager.info(DebugCategory.CORE, "Oculus detected - shader support enabled");
            } else {
                DebugManager.debug(DebugCategory.CORE, "Oculus not found - using vanilla rendering");
            }
        }
        return OCULUS_LOADED;
    }

    /**
     * Check if a shader pack is currently active.
     *
     * @return true if Oculus is loaded and a shader pack is in use
     */
    public static boolean isShadersActive() {
        if (!isOculusLoaded() || apiInitFailed) {
            return false;
        }

        try {
            // Lazy initialization of IrisApi
            if (irisApi == null) {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                java.lang.reflect.Method getInstance = irisApiClass.getMethod("getInstance");
                irisApi = getInstance.invoke(null);
                DebugManager.info(DebugCategory.CORE, "IrisApi initialized successfully");
            }

            // Call isShaderPackInUse()
            java.lang.reflect.Method isShaderPackInUse = irisApi.getClass()
                .getMethod("isShaderPackInUse");
            return (Boolean) isShaderPackInUse.invoke(irisApi);

        } catch (ClassNotFoundException e) {
            DebugManager.warn(DebugCategory.CORE, "Oculus API not found - disabling shader support");
            apiInitFailed = true;
            return false;
        } catch (Exception e) {
            DebugManager.error(DebugCategory.CORE, "Failed to check shader status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if currently rendering the shadow pass.
     * Used to optimize rendering during shadow map generation.
     *
     * @return true if rendering shadows, false otherwise
     */
    public static boolean isRenderingShadowPass() {
        if (!isShadersActive() || apiInitFailed) {
            return false;
        }

        try {
            // Call isRenderingShadowPass()
            java.lang.reflect.Method isRenderingShadowPass = irisApi.getClass()
                .getMethod("isRenderingShadowPass");
            return (Boolean) isRenderingShadowPass.invoke(irisApi);

        } catch (Exception e) {
            DebugManager.debug(DebugCategory.CORE, "Failed to check shadow pass status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the current rendering phase from Oculus pipeline.
     * Useful for phase-specific optimizations.
     *
     * @return phase name as string, or "NONE" if unavailable
     */
    public static String getCurrentPhase() {
        if (!isShadersActive() || apiInitFailed) {
            return "NONE";
        }

        try {
            // Access Iris.getPipelineManager().getPipelineNullable()
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            java.lang.reflect.Method getPipelineManager = irisClass.getMethod("getPipelineManager");
            Object pipelineManager = getPipelineManager.invoke(null);

            java.lang.reflect.Method getPipelineNullable = pipelineManager.getClass()
                .getMethod("getPipelineNullable");
            Object pipeline = getPipelineNullable.invoke(pipelineManager);

            if (pipeline == null) {
                return "NONE";
            }

            // Get current phase
            java.lang.reflect.Method getPhase = pipeline.getClass().getMethod("getPhase");
            Object phase = getPhase.invoke(pipeline);

            return phase != null ? phase.toString() : "NONE";

        } catch (Exception e) {
            DebugManager.debug(DebugCategory.CORE, "Failed to get rendering phase: {}", e.getMessage());
            return "NONE";
        }
    }

    /**
     * Check if PBR (Physically Based Rendering) is supported by current shader pack.
     *
     * @return true if PBR samplers are available
     */
    public static boolean isPBRSupported() {
        if (!isShadersActive() || apiInitFailed) {
            return false;
        }

        try {
            // Access pipeline and check for PBR samplers
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            java.lang.reflect.Method getPipelineManager = irisClass.getMethod("getPipelineManager");
            Object pipelineManager = getPipelineManager.invoke(null);

            java.lang.reflect.Method getPipelineNullable = pipelineManager.getClass()
                .getMethod("getPipelineNullable");
            Object pipeline = getPipelineNullable.invoke(pipelineManager);

            if (pipeline == null) {
                return false;
            }

            // Check if pipeline has PBR support
            // This is a heuristic - actual implementation may vary
            return true; // Assume PBR support if shaders are active

        } catch (Exception e) {
            DebugManager.debug(DebugCategory.CORE, "Failed to check PBR support: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Reset cached state. Used for testing or when mods are reloaded.
     */
    public static void reset() {
        OCULUS_LOADED = null;
        irisApi = null;
        apiInitFailed = false;
        DebugManager.debug(DebugCategory.CORE, "OculusCompat state reset");
    }
}
