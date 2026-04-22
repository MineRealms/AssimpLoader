package cn.minerealms.assimploader;

import org.lwjgl.system.Configuration;

/**
 * Bootstrap class for LWJGL Assimp initialization.
 *
 * Must be called BEFORE any Assimp classes are loaded.
 */
public class AssimpBootstrap {

    private static boolean initialized = false;

    /**
     * Initialize LWJGL Assimp configuration.
     * This sets up the native library name for Assimp.
     */
    public static void init() {
        if (initialized) {
            return;
        }

        try {
            // Extract native libraries first (may fail in dev environment)
            try {
                if (!NativeLibraryExtractor.extractAndLoadNatives()) {
                    AssimpLoader.LOGGER.warn("Failed to extract Assimp native libraries (may be normal in dev)");
                }
            } catch (Exception e) {
                AssimpLoader.LOGGER.warn("Native library extraction failed (may be normal in dev): {}", e.getMessage());
            }

            // Set Assimp library name
            Configuration.ASSIMP_LIBRARY_NAME.set("assimp");

            AssimpLoader.LOGGER.info("LWJGL Assimp bootstrap initialized");
            initialized = true;
        } catch (Exception e) {
            AssimpLoader.LOGGER.error("Failed to initialize LWJGL Assimp bootstrap", e);
            throw new RuntimeException("LWJGL Assimp initialization failed", e);
        }
    }

    /**
     * Check if LWJGL Assimp is available.
     *
     * @return true if Assimp classes can be loaded
     */
    public static boolean isAvailable() {
        try {
            Class.forName("org.lwjgl.assimp.Assimp");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Get LWJGL Assimp version string.
     *
     * @return version string or "unknown" if not available
     */
    public static String getVersion() {
        try {
            int major = org.lwjgl.assimp.Assimp.aiGetVersionMajor();
            int minor = org.lwjgl.assimp.Assimp.aiGetVersionMinor();
            int revision = org.lwjgl.assimp.Assimp.aiGetVersionRevision();
            return String.format("%d.%d.%d", major, minor, revision);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
