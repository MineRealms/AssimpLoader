package cn.minerealms.assimploader.lookdev.iris;

import java.util.Map;

/** Compatibility facade for the dormant node editor. No live shader source hook is installed. */
public final class OculusShaderInjector {
    private OculusShaderInjector() {}

    public static boolean isAvailable() {
        return false;
    }

    public static String inject(String targetShaderName, String fragmentCode,
                                Map<String, String> uniforms, InjectionPoint injectionPoint) {
        return null;
    }

    public static String injectToGBuffer(String name, String code, Map<String, String> uniforms) {
        return null;
    }

    public static String injectToComposite(String name, String code, Map<String, String> uniforms) {
        return null;
    }

    public enum InjectionPoint {
        BEFORE_DECLARATIONS, BEFORE_FUNCTIONS, MAIN_START, MAIN_END, AFTER_FUNCTION
    }
}
