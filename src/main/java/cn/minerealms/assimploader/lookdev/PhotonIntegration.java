package cn.minerealms.assimploader.lookdev;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraftforge.fml.ModList;

/**
 * Photon integration detection and management.
 *
 * Detects if Photon (or Oculus with Photon) is loaded at runtime
 * and determines the appropriate feature tier.
 *
 * This allows the LookDev system to gracefully degrade when Photon
 * is not available, providing basic node editing without shader integration.
 */
public class PhotonIntegration {

    private static FeatureTier cachedTier = null;
    private static Boolean photonLoaded = null;
    private static Boolean oculusLoaded = null;

    /**
     * Check if Photon mod is loaded
     */
    public static boolean isPhotonLoaded() {
        if (photonLoaded == null) {
            photonLoaded = ModList.get().isLoaded("photon");
            if (photonLoaded) {
                DebugManager.info(DebugCategory.LOOKDEV, "[PhotonIntegration] Photon mod detected");
            }
        }
        return photonLoaded;
    }

    /**
     * Check whether the Oculus/Iris shader integration is installed.
     */
    public static boolean isOculusLoaded() {
        if (oculusLoaded == null) {
            oculusLoaded = cn.minerealms.assimploader.compat.OculusCompat.isOculusLoaded();
            if (oculusLoaded) {
                DebugManager.info(DebugCategory.LOOKDEV, "[PhotonIntegration] Oculus/Iris mod detected");
            }
        }
        return oculusLoaded;
    }

    /**
     * Check if any Photon-compatible mod is loaded
     */
    public static boolean isPhotonAvailable() {
        return isPhotonLoaded();
    }

    /**
     * Get the current feature tier based on loaded mods
     */
    public static FeatureTier getFeatureTier() {
        // The particle editor mod does not supply a node-material compilation backend.
        return FeatureTier.STANDALONE;
    }

    /**
     * Check if shader features are available
     */
    public static boolean hasShaderSupport() {
        return getFeatureTier().hasShaderSupport();
    }

    /**
     * Check if Photon integration features are available
     */
    public static boolean hasPhotonIntegration() {
        return getFeatureTier().hasPhotonIntegration();
    }

    /**
     * Check if real-time preview is available
     */
    public static boolean hasRealtimePreview() {
        return getFeatureTier().hasRealtimePreview();
    }

    /**
     * Get a user-friendly status message
     */
    public static String getStatusMessage() {
        return "Node material editing is inactive";
    }

    /**
     * Reset cached values (for testing)
     */
    public static void resetCache() {
        cachedTier = null;
        photonLoaded = null;
        oculusLoaded = null;
    }
}
