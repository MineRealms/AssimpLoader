package cn.minerealms.assimploader.nativeapi.fabrik;

import cn.minerealms.assimploader.nativeapi.NativeResources;
import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.nio.file.Path;

/** Loads the bundled FabriK native and exposes its JNA interface and backend information. */
public final class FabriKNative {

    private static final String LIB = "fabrik";

    private static volatile boolean attempted;
    private static volatile FabriKLibrary library;
    private static volatile String backend = "unavailable";

    private FabriKNative() {
    }

    /** True when a compatible FabriK native is bundled and was loaded successfully. */
    public static boolean available() {
        ensureLoaded();
        return library != null;
    }

    /** The loaded JNA interface, or {@code null} when unavailable. */
    public static FabriKLibrary library() {
        ensureLoaded();
        return library;
    }

    /** Human-readable version/SIMD backend, or an explanation of why loading failed. */
    public static String backend() {
        ensureLoaded();
        return backend;
    }

    public static String nativeFilename() {
        if (Platform.isWindows()) return "fabrik.dll";
        if (Platform.isMac()) return "libfabrik.dylib";
        return "libfabrik.so";
    }

    private static synchronized void ensureLoaded() {
        if (attempted) {
            return;
        }
        attempted = true;
        String filename = nativeFilename();
        if (!NativeResources.isAvailable(LIB, filename)) {
            backend = "unavailable: no native bundled for JNA platform " + Platform.RESOURCE_PREFIX;
            return;
        }
        try {
            NativeResources.load(LIB, filename);
            Path extracted = NativeResources.loadedPath();
            library = extracted == null
                    ? Native.load(LIB, FabriKLibrary.class)
                    : Native.load(extracted.toString(), FabriKLibrary.class);
            try {
                backend = library.fk_version() + " (" + library.fk_simd_backend() + ")";
            } catch (Throwable ignored) {
                backend = "loaded";
            }
        } catch (Throwable t) {
            library = null;
            backend = "unavailable: " + t;
        }
    }
}
