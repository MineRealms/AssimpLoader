package cn.minerealms.assimploader.nativeapi;

import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeResourcesTest {
    @Test
    void jnaPlatformPrefixesResolveToBundledFabrikLibraries() throws Exception {
        Method prefixMethod = Platform.class.getDeclaredMethod(
                "getNativeLibraryResourcePrefix", int.class, String.class, String.class);
        prefixMethod.setAccessible(true);

        assertBundled(prefixMethod, Platform.WINDOWS, "x86", "Windows", "windows-x86", "fabrik.dll");
        assertBundled(prefixMethod, Platform.WINDOWS, "amd64", "Windows", "windows-x86_64", "fabrik.dll");
        assertBundled(prefixMethod, Platform.WINDOWS, "aarch64", "Windows", "windows-aarch64", "fabrik.dll");
        assertBundled(prefixMethod, Platform.LINUX, "arm", "Linux", "linux-armv7", "libfabrik.so");
        assertBundled(prefixMethod, Platform.LINUX, "amd64", "Linux", "linux-x86_64", "libfabrik.so");
        assertBundled(prefixMethod, Platform.LINUX, "aarch64", "Linux", "linux-aarch64", "libfabrik.so");
        assertBundled(prefixMethod, Platform.MAC, "x86_64", "Mac OS X", "macos-x86_64", "libfabrik.dylib");
        assertBundled(prefixMethod, Platform.MAC, "aarch64", "Mac OS X", "macos-aarch64", "libfabrik.dylib");
    }

    private static void assertBundled(Method prefixMethod, int os, String cpu, String osName,
                                      String expectedDirectory, String filename) throws Exception {
        String prefix = (String) prefixMethod.invoke(null, os, cpu, osName);
        String directory = NativeResources.archDir(prefix);
        assertEquals(expectedDirectory, directory, prefix);
        String resource = "native/fabrik/" + directory + "/" + filename;
        assertNotNull(NativeResources.class.getClassLoader().getResource(resource), resource);
    }
}
