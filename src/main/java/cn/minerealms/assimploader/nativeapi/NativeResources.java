package cn.minerealms.assimploader.nativeapi;

import com.sun.jna.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Extracts natives from {@code native/<lib>/<arch>/<file>} inside the mod jar and loads them
 * with {@link System#load(String)}.
 *
 * <p>JNA's {@link Platform#RESOURCE_PREFIX} identifies the running platform; it is mapped to the
 * normalized arch directory names used by the generic native manifest. No {@code org.lwjgl.librarypath}
 * or global JNA search path is touched, so other mods are unaffected.
 */
public final class NativeResources {

    private static final Map<String, String> PREFIX_TO_ARCH = Map.ofEntries(
            Map.entry("win32-x86-64", "windows-x86_64"),
            Map.entry("win32-x86", "windows-x86"),
            Map.entry("win32-arm64", "windows-aarch64"),
            Map.entry("linux-x86-64", "linux-x86_64"),
            Map.entry("linux-x86", "linux-x86"),
            Map.entry("linux-aarch64", "linux-aarch64"),
            Map.entry("linux-arm", "linux-armv7"),
            Map.entry("darwin", "macos-x86_64"),
            Map.entry("darwin-x86-64", "macos-x86_64"),
            Map.entry("darwin-aarch64", "macos-aarch64"));

    private static volatile Path lastLoadedPath;

    private NativeResources() {
    }

    /** Absolute path of the most recently extracted/loaded native, for JNA interface binding. */
    public static Path loadedPath() {
        return lastLoadedPath;
    }

    /** Maps the current JNA resource prefix to a normalized arch directory name. */
    public static String archDir() {
        String prefix = Platform.RESOURCE_PREFIX;
        String arch = PREFIX_TO_ARCH.get(prefix);
        if (arch == null) {
            throw new IllegalStateException("No native arch directory mapping for JNA platform '" + prefix + "'");
        }
        return arch;
    }

    /** Classpath resource path of a native, e.g. {@code native/fabrik/windows-x86_64/fabrik.dll}. */
    public static String resourcePath(String lib, String filename) {
        return "native/" + lib + "/" + archDir() + "/" + filename;
    }

    /** True when a matching native is bundled for the current platform. */
    public static boolean isAvailable(String lib, String filename) {
        try {
            return NativeResources.class.getClassLoader().getResource(resourcePath(lib, filename)) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Extracts the native to a private temp directory and loads it.
     *
     * @throws UnsatisfiedLinkError if the native is missing or cannot be extracted/loaded
     */
    public static void load(String lib, String filename) {
        String path = resourcePath(lib, filename);
        ClassLoader loader = NativeResources.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Bundled native not found in jar: " + path);
            }
            Path dir = Files.createTempDirectory("assimploader-native-");
            Path extracted = dir.resolve(filename);
            Files.copy(in, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            Path absolute = extracted.toAbsolutePath();
            System.load(absolute.toString());
            lastLoadedPath = absolute;
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract bundled native " + path + ": " + e.getMessage());
        }
    }
}
