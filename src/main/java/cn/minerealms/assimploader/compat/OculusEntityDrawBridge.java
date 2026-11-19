package cn.minerealms.assimploader.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional Oculus bridge for immediate draws using its extended entity vertex data. */
public final class OculusEntityDrawBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(OculusEntityDrawBridge.class);
    private static volatile Api api;
    private static volatile boolean failed;
    private static final int[] ENTITY_IDS = new int[3];
    private static final AutoCloseable NO_PHASE_CHANGE = () -> {};

    private OculusEntityDrawBridge() {}

    /** True only during a world pass that will extend NEW_ENTITY buffers for the active pack. */
    public static boolean available() {
        if (failed || !RenderSystem.isOnRenderThread() || !OculusCompat.isIntegrationEnabled()
                || !OculusCompat.isShadersActive()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getOverlay() != null) return false;
        try {
            Api bridge = api();
            // Chat and world editors still render the level. The actual Oculus pass flags
            // exclude inventory/offscreen GUI previews without disabling resident world VBOs.
            if (bridge == null || !bridge.isRenderingLevel.getBoolean(null)
                    || !bridge.renderWithExtendedVertexFormat.getBoolean(null)
                    || (bridge.useExtendedVertexFormat != null
                    && !Boolean.TRUE.equals(bridge.useExtendedVertexFormat.invoke(bridge.worldRenderingSettings)))) return false;
            Object pipeline = bridge.currentPipeline();
            return pipeline != null && bridge.overridePhase.getDeclaringClass().isInstance(pipeline)
                    && bridge.overridePhase.get(pipeline) == null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    /**
     * Temporarily selects Iris' block-entity shader phase for a direct cached draw.
     * Uses the pipeline phase directly rather than GbufferPrograms' begin/end flags,
     * which may already be active for another render wrapper.
     */
    public static AutoCloseable beginBlockEntity() {
        return beginPhase(true);
    }

    /** Selects the entity phase while preserving an enclosing first-person hand pass. */
    public static AutoCloseable beginEntity() {
        return beginPhase(false);
    }

    /** Detach uniform/sampler listeners before changing the program or the material underneath Iris. */
    public static void clearShaderBinding() {
        RenderSystem.assertOnRenderThread();
        try {
            Api bridge = api();
            bridge.clearActiveUniforms.invoke(null);
            bridge.clearActiveSamplers.invoke(null);
            if (bridge.lastAppliedShader != null) bridge.lastAppliedShader.set(null, null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            throw new IllegalStateException("Could not invalidate Oculus shader binding", exception);
        }
    }

    /** An immediate draw must bind its program even when Iris' last-applied cache still names that shader. */
    public static void bindShader(ShaderInstance shader) {
        RenderSystem.assertOnRenderThread();
        if (shader == null || shader.getId() <= 0)
            throw new IllegalStateException("No linked entity shader selected by Oculus");
        ProgramManager.glUseProgram(shader.getId());
    }

    private static AutoCloseable beginPhase(boolean blockEntity) {
        if (!available()) throw new IllegalStateException("Oculus extended entity rendering is unavailable");
        try {
            Api bridge = api();
            Object pipeline = bridge.currentPipeline();
            if (pipeline == null) throw new IllegalStateException("Oculus world pipeline is unavailable");
            Object previous = bridge.getPhase.invoke(pipeline);
            Object requested = blockEntity ? bridge.blockEntityPhase : bridge.entityPhase;
            if (!blockEntity && (previous == bridge.handSolidPhase || previous == bridge.handTranslucentPhase))
                return NO_PHASE_CHANGE;
            if (previous == requested) return NO_PHASE_CHANGE;
            bridge.setPhase.invoke(pipeline, requested);
            return () -> {
                try {
                    bridge.setPhase.invoke(pipeline, previous);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    fail(exception);
                    throw new IllegalStateException("Could not restore Oculus render phase", exception);
                }
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            throw new IllegalStateException("Could not select Oculus block-entity render phase", exception);
        }
    }

    /** Current per-draw shader IDs; the returned array is reused and must not be retained. */
    public static int[] currentEntityIds() {
        RenderSystem.assertOnRenderThread();
        if (!available()) throw new IllegalStateException("Oculus extended entity rendering is unavailable");
        try {
            Api bridge = api();
            Object captured = bridge.capturedRenderingState;
            ENTITY_IDS[0] = (int) bridge.getRenderedEntity.invoke(captured);
            ENTITY_IDS[1] = (int) bridge.getRenderedBlockEntity.invoke(captured);
            ENTITY_IDS[2] = (int) bridge.getRenderedItem.invoke(captured);
            return ENTITY_IDS;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            throw new IllegalStateException("Could not read Oculus entity material IDs", exception);
        }
    }

    /** Require the exact Oculus ENTITY format object, not a guessed stride or attribute count. */
    public static boolean isEntityFormat(VertexFormat format) {
        if (format == null || !available()) return false;
        try {
            Api bridge = api();
            return bridge != null && format == bridge.entityFormat.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);
            return false;
        }
    }

    public static int entityVertexStride() {
        if(!available()) return 0;
        try {
            return ((VertexFormat)api().entityFormat.get(null)).getVertexSize();
        } catch(ReflectiveOperationException | RuntimeException | LinkageError exception) {
            fail(exception);return 0;
        }
    }

    /** Discard cached optional API handles and allow signature discovery after resource reload. */
    public static synchronized void reset() {
        api = null;
        failed = false;
        ENTITY_IDS[0] = ENTITY_IDS[1] = ENTITY_IDS[2] = 0;
    }

    private static Api api() throws ReflectiveOperationException {
        Api known = api;
        if (known != null) return known;
        synchronized (OculusEntityDrawBridge.class) {
            if (api != null) return api;
            ClassLoader loader = OculusEntityDrawBridge.class.getClassLoader();
            Class<?> settingsType = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", false, loader);
            Object settings = settingsType.getField("INSTANCE").get(null);
            // Iris 1.8 on 1.21 extends entity vertices unconditionally during the ImmediateState pass.
            Method useExtended;
            try {
                useExtended = settingsType.getMethod("shouldUseExtendedVertexFormat");
            } catch (NoSuchMethodException noLegacyToggle) {
                useExtended = null;
            }

            Class<?> pipelineType = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline", false, loader);
            Method getManager = Class.forName("net.irisshaders.iris.Iris", false, loader).getMethod("getPipelineManager");
            Method getPipeline = getManager.getReturnType().getMethod("getPipelineNullable");
            Method getPhase = pipelineType.getMethod("getPhase");
            Method setPhase = pipelineType.getMethod("setPhase", getPhase.getReturnType());
            Class<?> phaseType = getPhase.getReturnType();
            Object blockEntityPhase = phaseType.getField("BLOCK_ENTITIES").get(null);
            Object entityPhase = phaseType.getField("ENTITIES").get(null);
            Object handSolidPhase = phaseType.getField("HAND_SOLID").get(null);
            Object handTranslucentPhase = phaseType.getField("HAND_TRANSLUCENT").get(null);
            Field overridePhase = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline", false, loader)
                    .getDeclaredField("overridePhase");
            if (!overridePhase.trySetAccessible()) throw new IllegalAccessException("Cannot inspect Oculus override phase");

            Class<?> formats = Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats", false, loader);
            Field entityFormat = formats.getField("ENTITY");
            Class<?> immediateState = Class.forName("net.irisshaders.iris.vertices.ImmediateState", false, loader);
            Field isRenderingLevel = immediateState.getField("isRenderingLevel");
            Field renderWithExtended = immediateState.getField("renderWithExtendedVertexFormat");
            Class<?> capturedType = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState", false, loader);
            Object captured = capturedType.getField("INSTANCE").get(null);
            Method clearActiveUniforms = Class.forName("net.irisshaders.iris.gl.program.ProgramUniforms", false, loader)
                    .getMethod("clearActiveUniforms");
            Method clearActiveSamplers = Class.forName("net.irisshaders.iris.gl.program.ProgramSamplers", false, loader)
                    .getMethod("clearActiveSamplers");
            Field lastAppliedShader;
            try {
                lastAppliedShader = Class.forName("net.irisshaders.iris.pipeline.programs.ExtendedShader", false, loader)
                        .getDeclaredField("lastApplied");
                if (!lastAppliedShader.trySetAccessible()) throw new IllegalAccessException("Cannot invalidate Oculus shader binding");
            } catch (NoSuchFieldException noLegacyCache) {
                // Iris 1.21 ExtendedShader.apply always binds its program; Oculus caches the last one.
                lastAppliedShader = null;
            }
            api = new Api(settings, useExtended, getManager, getPipeline, getPhase, setPhase, blockEntityPhase, entityPhase,
                    handSolidPhase, handTranslucentPhase,
                    overridePhase, entityFormat, isRenderingLevel, renderWithExtended,
                    capturedType.getMethod("getCurrentRenderedEntity"),
                    capturedType.getMethod("getCurrentRenderedBlockEntity"),
                    capturedType.getMethod("getCurrentRenderedItem"), captured,
                    clearActiveUniforms, clearActiveSamplers, lastAppliedShader);
            return api;
        }
    }

    private static void fail(Throwable exception) {
        if (failed) return;
        failed = true;
        api = null;
        LOGGER.warn("Oculus cached entity draw bridge is unavailable; using the regular vertex path", exception);
    }

    private record Api(Object worldRenderingSettings, Method useExtendedVertexFormat,
                       Method manager, Method pipeline, Method getPhase, Method setPhase,
                       Object blockEntityPhase, Object entityPhase, Object handSolidPhase, Object handTranslucentPhase,
                       Field overridePhase, Field entityFormat, Field isRenderingLevel, Field renderWithExtendedVertexFormat,
                       Method getRenderedEntity, Method getRenderedBlockEntity, Method getRenderedItem,
                       Object capturedRenderingState, Method clearActiveUniforms, Method clearActiveSamplers,
                       Field lastAppliedShader) {
        private Object currentPipeline() throws ReflectiveOperationException {
            Object currentManager = manager.invoke(null);
            return currentManager == null ? null : pipeline.invoke(currentManager);
        }
    }
}
