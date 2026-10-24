package cn.minerealms.assimploader;

/**
 * Bootstrap for LWJGL Assimp.
 *
 * <p>Native extraction is handled by LWJGL itself ({@code SharedLibraryLoader}) from the
 * natives that are packaged inside this mod's jar, so there is nothing to extract here.
 * Doing it by hand used to leave stale, unversioned DLLs in the temp folder and could
 * hijack the global {@code org.lwjgl.librarypath} of every other mod.
 */
public final class AssimpBootstrap {

    private static boolean initialized = false;

    private AssimpBootstrap() {
    }

    /**
     * Verifies that the Assimp bindings can be loaded. Never fatal: a missing native only
     * disables model import, it must not prevent the game from starting.
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!isAvailable()) {
            AssimpLoader.LOGGER.warn("LWJGL Assimp is not on the classpath - model import is disabled");
            return;
        }

        try {
            AssimpLoader.LOGGER.info("LWJGL Assimp initialized: version {}", getVersion());
        } catch (Throwable t) {
            AssimpLoader.LOGGER.warn("LWJGL Assimp bindings present but the native library failed to load", t);
        }
    }

    public static boolean isAvailable() {
        try {
            Class.forName("org.lwjgl.assimp.Assimp");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * @return the native Assimp version, or {@code "unavailable"} when the native is missing
     */
    public static String getVersion() {
        try {
            return String.format("%d.%d.%d",
                    org.lwjgl.assimp.Assimp.aiGetVersionMajor(),
                    org.lwjgl.assimp.Assimp.aiGetVersionMinor(),
                    org.lwjgl.assimp.Assimp.aiGetVersionRevision());
        } catch (Throwable t) {
            return "unavailable";
        }
    }
}
