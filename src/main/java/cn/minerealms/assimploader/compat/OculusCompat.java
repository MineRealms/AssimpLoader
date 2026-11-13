package cn.minerealms.assimploader.compat;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Optional Oculus access. Reflection handles are resolved once, never looked up in draw loops. */
public final class OculusCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(OculusCompat.class);
    private static volatile Boolean loaded;
    private static volatile Api api;
    private static volatile PipelineApi pipelineApi;
    private static volatile boolean apiFailed;
    private static volatile boolean pipelineFailed;
    private static long frame;
    private static long checkedFrame = Long.MIN_VALUE;
    private static boolean shadersActive;

    private OculusCompat() {}

    public static boolean isOculusLoaded() {
        Boolean known = loaded;
        if (known != null) return known;
        ModList mods = ModList.get();
        if (mods == null) return false;
        loaded = mods.isLoaded("oculus");
        return loaded;
    }

    public static boolean isIntegrationEnabled() {
        return AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.oculusEnabled::get, true)
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.shaderMode::get,
                AssimpLoaderConfig.ShaderMode.DUAL) != AssimpLoaderConfig.ShaderMode.VANILLA;
    }

    /** Actual shader state stays observable even when integration is disabled, so custom GPU programs can yield safely. */
    public static boolean isShadersActive() {
        if (!isOculusLoaded() || apiFailed) return false;
        if (checkedFrame == frame) return shadersActive;
        checkedFrame = frame;
        try {
            Api bridge = api();
            shadersActive = bridge != null && Boolean.TRUE.equals(bridge.isShaderPackInUse.invoke(bridge.instance));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failApi(exception);
        }
        return shadersActive;
    }

    public static boolean isRenderingShadowPass() {
        if (!isIntegrationEnabled() || !isShadersActive()) return false;
        try {
            Api bridge = api;
            return bridge != null && Boolean.TRUE.equals(bridge.isRenderingShadowPass.invoke(bridge.instance));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failApi(exception);
            return false;
        }
    }

    /** Unknown optional API state must not allow this mod to override a possibly active third-party shader. */
    public static boolean shouldAvoidCustomShaders() {
        if (!isOculusLoaded()) return false;
        boolean active = isShadersActive();
        return active || apiFailed;
    }

    public static String getCurrentPhase() {
        if (!isIntegrationEnabled() || !isShadersActive()) return "NONE";
        try {
            PipelineApi bridge = pipelineApi();
            Object pipeline = bridge == null ? null : bridge.pipeline();
            Object phase = pipeline == null ? null : bridge.getPhase.invoke(pipeline);
            return phase == null ? "NONE" : phase.toString();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failPipeline(exception);
            return "NONE";
        }
    }

    /** Pipeline binding diagnostic only: positive IDs may refer to Oculus defaults, not authored material images. */
    public static boolean isPBRSupported() {
        if (!isIntegrationEnabled() || !pbrConfigured() || !isShadersActive()) return false;
        try {
            PipelineApi bridge = pipelineApi();
            Object pipeline = bridge == null ? null : bridge.pipeline();
            return pipeline != null && hasPbrBindings((Integer) bridge.getNormal.invoke(pipeline),
                    (Integer) bridge.getSpecular.invoke(pipeline));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failPipeline(exception);
            return false;
        }
    }

    static boolean pbrConfigured() {
        return AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.pbrEnabled::get, true)
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.oculusPbrSupportEnabled::get, true);
    }

    static boolean hasPbrBindings(int normal, int specular) { return normal > 0 && specular > 0; }

    static void beginFrame() { frame++; }

    private static Api api() throws ReflectiveOperationException {
        Api known = api;
        if (known != null || apiFailed) return known;
        synchronized (OculusCompat.class) {
            if (api == null && !apiFailed) {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, OculusCompat.class.getClassLoader());
                api = new Api(type.getMethod("getInstance").invoke(null), type.getMethod("isShaderPackInUse"),
                        type.getMethod("isRenderingShadowPass"));
            }
            return api;
        }
    }

    private static PipelineApi pipelineApi() throws ReflectiveOperationException {
        PipelineApi known = pipelineApi;
        if (known != null || pipelineFailed) return known;
        synchronized (OculusCompat.class) {
            if (pipelineApi == null && !pipelineFailed) {
                ClassLoader loader = OculusCompat.class.getClassLoader();
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris", false, loader);
                Method manager = iris.getMethod("getPipelineManager");
                Method pipeline = manager.getReturnType().getMethod("getPipelineNullable");
                Class<?> type = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline", false, loader);
                pipelineApi = new PipelineApi(manager, pipeline, type.getMethod("getPhase"),
                        type.getMethod("getCurrentNormalTexture"), type.getMethod("getCurrentSpecularTexture"));
            }
            return pipelineApi;
        }
    }

    private static void failApi(Throwable exception) {
        if (!apiFailed) LOGGER.warn("Oculus shader status API is unavailable; optional integration disabled until reload", exception);
        apiFailed = true;
        api = null;
        shadersActive = false;
    }

    private static void failPipeline(Throwable exception) {
        if (!pipelineFailed) LOGGER.warn("Oculus pipeline metadata is unavailable; PBR/phase detection disabled until reload", exception);
        pipelineFailed = true;
        pipelineApi = null;
    }

    public static synchronized void reset() {
        loaded = null;
        api = null;
        pipelineApi = null;
        apiFailed = false;
        pipelineFailed = false;
        shadersActive = false;
        checkedFrame = Long.MIN_VALUE;
        OculusPBRSupport.reset();
    }

    private record Api(Object instance, Method isShaderPackInUse, Method isRenderingShadowPass) {}
    private record PipelineApi(Method getManager, Method getPipeline, Method getPhase, Method getNormal, Method getSpecular) {
        private Object pipeline() throws ReflectiveOperationException {
            Object manager = getManager.invoke(null);
            return manager == null ? null : getPipeline.invoke(manager);
        }
    }
}
