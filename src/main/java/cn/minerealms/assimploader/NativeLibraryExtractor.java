package cn.minerealms.assimploader;

import org.lwjgl.system.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Extracts LWJGL Assimp native libraries from the classpath.
 */
public class NativeLibraryExtractor {

    private static final String NATIVES_DIR = ".lwjgl_natives";

    /**
     * Extract Assimp native library to a temporary directory and set library path.
     *
     * @return true if extraction succeeded
     */
    public static boolean extractAndLoadNatives() {
        try {
            // Determine platform-specific library name
            String libraryName = getLibraryName();
            if (libraryName == null) {
                AssimpLoader.LOGGER.error("Unsupported platform: {}", Platform.get());
                return false;
            }

            // Get or create natives directory
            Path nativesDir = getNativesDirectory();
            Path libraryPath = nativesDir.resolve(libraryName);

            // Extract if not already present
            if (!Files.exists(libraryPath)) {
                AssimpLoader.LOGGER.info("Extracting native library: {}", libraryName);
                if (!extractNativeLibrary(libraryName, libraryPath)) {
                    return false;
                }
            }

            // Set LWJGL library path
            String existingPath = System.getProperty("org.lwjgl.librarypath");
            if (existingPath == null || existingPath.isEmpty()) {
                System.setProperty("org.lwjgl.librarypath", nativesDir.toAbsolutePath().toString());
                AssimpLoader.LOGGER.info("Set LWJGL library path: {}", nativesDir.toAbsolutePath());
            } else {
                AssimpLoader.LOGGER.info("LWJGL library path already set: {}", existingPath);
            }

            return true;
        } catch (Exception e) {
            AssimpLoader.LOGGER.error("Failed to extract native libraries", e);
            return false;
        }
    }

    /**
     * Get platform-specific library name.
     */
    private static String getLibraryName() {
        Platform platform = Platform.get();
        switch (platform) {
            case WINDOWS:
                return "assimp.dll";
            case LINUX:
                return "libassimp.so";
            case MACOSX:
                return "libassimp.dylib";
            default:
                return null;
        }
    }

    /**
     * Get or create natives directory in user's temp folder.
     */
    private static Path getNativesDirectory() throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        Path nativesDir = Paths.get(tempDir, NATIVES_DIR);

        if (!Files.exists(nativesDir)) {
            Files.createDirectories(nativesDir);
            AssimpLoader.LOGGER.info("Created natives directory: {}", nativesDir);
        }

        return nativesDir;
    }

    /**
     * Extract native library from classpath to file system.
     */
    private static boolean extractNativeLibrary(String libraryName, Path targetPath) {
        // Try to find the library in the natives JAR
        String resourcePath = getNativeResourcePath(libraryName);

        try (InputStream is = NativeLibraryExtractor.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                AssimpLoader.LOGGER.error("Native library not found in classpath: {}", resourcePath);
                return false;
            }

            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            AssimpLoader.LOGGER.info("Extracted native library to: {}", targetPath);
            return true;
        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to extract native library: {}", libraryName, e);
            return false;
        }
    }

    /**
     * Get the resource path for the native library based on platform.
     */
    private static String getNativeResourcePath(String libraryName) {
        Platform platform = Platform.get();
        String arch = System.getProperty("os.arch");

        // LWJGL natives are stored in org/lwjgl/assimp/ subdirectories
        String lwjglPath = "org/lwjgl/assimp/";

        switch (platform) {
            case WINDOWS:
                return "windows/x64/" + lwjglPath + libraryName;
            case LINUX:
                return "linux/x64/" + lwjglPath + libraryName;
            case MACOSX:
                // macOS can be x64 or arm64
                if (arch.contains("aarch64") || arch.contains("arm")) {
                    return "macos/arm64/" + lwjglPath + libraryName;
                } else {
                    return "macos/x64/" + lwjglPath + libraryName;
                }
            default:
                return libraryName;
        }
    }
}
