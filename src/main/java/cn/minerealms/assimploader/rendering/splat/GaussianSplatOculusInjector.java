package cn.minerealms.assimploader.rendering.splat;

/** Reserved capability marker. No real Iris shader-source hook is installed. */
public final class GaussianSplatOculusInjector {
    private GaussianSplatOculusInjector() {}
    public static void initialize() {}
    public static boolean isInjected() { return false; }
}
