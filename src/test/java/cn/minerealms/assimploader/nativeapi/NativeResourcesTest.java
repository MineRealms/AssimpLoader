package cn.minerealms.assimploader.nativeapi;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeResourcesTest {
    @Test
    void jnaPlatformPrefixesResolveToBundledFabrikLibraries() throws Exception {
        Method prefixMethod = Platform.class.getDeclaredMethod(
                "getNativeLibraryResourcePrefix", int.class, String.class, String.class);
        prefixMethod.setAccessible(true);

        assertBundled(prefixMethod, Platform.WINDOWS, "x86", "Windows", "windows-x86", "fabrik.dll");
        assertBundled(prefixMethod, Platform.WINDOWS, "amd64", "Windows", "windows-x86_64", "fabrik.dll");
        assertBundled(prefixMethod, Platform.WINDOWS, "aarch64", "Windows", "windows-aarch64", "fabrik.dll");
        assertBundled(prefixMethod, Platform.LINUX, "amd64", "Linux", "linux-x86_64", "libfabrik.so");
        assertBundled(prefixMethod, Platform.LINUX, "aarch64", "Linux", "linux-aarch64", "libfabrik.so");
        assertBundled(prefixMethod, Platform.MAC, "x86_64", "Mac OS X", "macos-x86_64", "libfabrik.dylib");
        assertBundled(prefixMethod, Platform.MAC, "aarch64", "Mac OS X", "macos-aarch64", "libfabrik.dylib");
    }

    @Test
    void linuxArmHardFloatPrefixResolvesToBundledLibrary() {
        // JNA probes the host's /proc/self/exe for ARM float ABI even when passed a simulated CPU.
        assertBundled("linux-arm", "linux-armv7", "libfabrik.so");
    }

    @Test
    void linuxArmSoftFloatDoesNotSelectHardFloatLibrary() {
        assertThrows(IllegalStateException.class, () -> NativeResources.archDir("linux-armel"));
    }

    private static void assertBundled(Method prefixMethod, int os, String cpu, String osName,
                                      String expectedDirectory, String filename) throws Exception {
        String prefix = (String) prefixMethod.invoke(null, os, cpu, osName);
        assertBundled(prefix, expectedDirectory, filename);
    }

    private static void assertBundled(String prefix, String expectedDirectory, String filename) {
        String directory = NativeResources.archDir(prefix);
        assertEquals(expectedDirectory, directory, prefix);
        String resource = "native/fabrik/" + directory + "/" + filename;
        assertNotNull(NativeResources.class.getClassLoader().getResource(resource), resource);
    }
}
