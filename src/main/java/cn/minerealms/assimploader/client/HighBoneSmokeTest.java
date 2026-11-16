package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Explicit 256-weighted-bone + Morph acceptance run; normal clients never open this screen. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class HighBoneSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("highbonetest.enabled");
    private static final ResourceLocation MODEL = new ResourceLocation("assimploader", "models/validation/highbone_probe.glb");
    private static final String[] STAGES = {"wave-zero", "wave-morph", "wave-restored", "wave-morph-reloaded"};
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final long SETTLE_MS = 10_000;
    private static final int SIZE = 512;
    private static final Object OWNER = new Object();
    private static int stage = -1;
    private static long changedAt = System.currentTimeMillis();
    private static boolean waiting, done;
    private static ProbeScreen screen;

    private HighBoneSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            long elapsed = System.currentTimeMillis() - changedAt;
            require(elapsed < 120_000, "timeout at stage " + stage);
            if (stage < 0) {
                if (mc.screen instanceof TitleScreen && elapsed >= 3_000) {
                    screen = new ProbeScreen();
                    screen.load();
                    stage = 0;
                    screen.prepare();
                    mc.setScreen(screen);
                    changedAt = System.currentTimeMillis();
                }
                return;
            }
            if (waiting || elapsed < SETTLE_MS || !screen.rendered) return;
            if (!screen.texturesReady()) return;
            screen.verify();
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL error before capture");
            String filename = "assimp-highbone-" + RUN + "-" + STAGES[stage] + ".png";
            waiting = true;
            Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                try {
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(filename)), "missing " + filename);
                    AssimpLoader.LOGGER.info("[HighBoneSmoke] Captured {} after at least {} ms", filename, SETTLE_MS);
                    advance(mc);
                } catch (Exception | LinkageError failure) { fail(mc, failure); }
            }));
        } catch (Exception | LinkageError failure) { fail(mc, failure); }
    }

    private static void advance(Minecraft mc) {
        if (++stage == STAGES.length) {
            done = true;
            AssimpLoader.LOGGER.info("[HighBoneSmoke] SUCCESS run={} actual 256-bone TBO, combined Morph, zero restore, CPU/GPU masks and recreation", RUN);
            screen.release();
            mc.stop();
            return;
        }
        if (stage == 3) {
            AnimationState oldState = screen.state;
            AssimpScene oldScene = screen.scene;
            PoseSnapshot oldPose = oldState.getPoseSnapshot();
            Matrix4f retained = oldPose.getNodeTransform("B255");
            changedAt = System.currentTimeMillis();
            mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
                try {
                    if (failure != null) throw new IllegalStateException("reload failed", failure);
                    require(oldState.isClosed(), "reload retained old managed state");
                    require(retained.equals(oldPose.getNodeTransform("B255"), 1e-6f), "retained high bone snapshot mutated");
                    screen.load();
                    require(screen.scene != oldScene, "resource recreation reused imported scene");
                    screen.prepare();
                    waiting = false;
                    changedAt = System.currentTimeMillis();
                } catch (Exception | LinkageError error) { fail(mc, error); }
            }));
        } else {
            screen.prepare();
            waiting = false;
            changedAt = System.currentTimeMillis();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void fail(Minecraft mc, Throwable error) {
        if (done) return;
        done = true;
        AssimpLoader.LOGGER.error("[HighBoneSmoke] FAILED stage=" + stage, error);
        if (screen != null) screen.release();
        mc.stop();
    }

    private static final class ProbeScreen extends Screen {
        private final TextureTarget[] targets = new TextureTarget[2];
        private final CpuRenderer cpu = new CpuRenderer();
        private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        private final Matrix4f projection = new Matrix4f().ortho(-2.5f, 2.5f, -2.5f, 2.5f, -16, 16);
        private final Matrix4f view = new Matrix4f().rotateX(.06f).rotateY(-.18f).translate(0, -1.9f, 0);
        private AssimpScene scene;
        private AssimpMesh mesh;
        private AnimationState state;
        private SkinnedMeshRenderer gpu;
        private boolean rendered;
        private boolean[] zeroMask, activeMask;
        private int frames;
        private int startQuery, endQuery;
        private long gpuNanos = -1;
        private long cpuSubmitNanos, gpuSubmitNanos;
        private int measuredFrames;

        ProbeScreen() { super(Component.literal("AssimpLoader 256 Bone Validation")); }

        void load() throws Exception {
            scene = new AssimpModelLoader().loadModelFromResources(Minecraft.getInstance().getResourceManager(), MODEL, ModelImportMode.DYNAMIC);
            require(scene.getMeshes().size() == 1, "high bone fixture must remain one primitive");
            mesh = scene.getMeshes().get(0);
            require(mesh.getBoneNames().size() == 256 && mesh.hasMorphTargets(), "fixture lost its 256 weighted bones or Morph");
            ClientRenderResources.checkGeneration();
            state = ClientAnimationManager.getInstance().getOrCreateAnimationState(OWNER, MODEL.toString(), scene);
            require(state != null && state.getModelInstance() != null, "high bone configured instance missing");
            require(state.getPoseSnapshot().getBoneCount(mesh) == 256, "published palette is not 256 bones");
            gpu = ClientRenderResources.getSkinnedRenderer();
            require(gpu != null && gpu.isInitialized() && gpu.supports(256), "actual 256-bone TBO unavailable; uniform/CPU fallback cannot pass");
            require(gpu.supportsMorph(mesh), "actual GPU Morph unavailable; CPU fallback cannot pass");
            for (int i = 0; i < 2; i++) {
                if (targets[i] == null) targets[i] = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
                targets[i].setClearColor(0, 0, 0, 0);
            }
            AssimpLoader.LOGGER.info("[HighBoneSmoke] Imported vertices={} bones={} Morph={} paletteBytesPerDraw={} immutableMorphDeltaBytes={}",
                    mesh.getVertexCount(), mesh.getBoneNames().size(), mesh.getMorphTargets().size(), 256 * 64,
                    (long) mesh.getVertexCount() * mesh.getMorphTargets().size() * 2 * 16);
        }

        void prepare() {
            clearQueries();
            rendered = false;
            frames = measuredFrames = 0;
            cpuSubmitNanos = gpuSubmitNanos = 0;
            state.stop();
            state.resume();
            require(state.transitionTo("Wave", 0), "Wave missing");
            state.setMorphWeights(mesh, new float[]{stage == 1 || stage == 3 ? 1 : 0});
            state.update(.65f);
            state.pause();
            require(state.getPoseSnapshot().getBoneCount(mesh) == 256, "palette truncated");
            require(state.getPoseSnapshot().getNodeTransform("B255") != null, "last bone missing");
            AssimpLoader.LOGGER.info("[HighBoneSmoke] Prepared {}; capture waits {} ms", STAGES[stage], SETTLE_MS);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xff252a2e);
                if (waiting && stage == 3) return;
                graphics.flush();
                int side = Math.min((width - 48) / 2, height - 64);
                int left = (width - side * 2 - 16) / 2;
                long start = System.nanoTime();
                draw(false);
                long middle = System.nanoTime();
                draw(true);
                long end = System.nanoTime();
                if (frames >= 64) { cpuSubmitNanos += middle - start; gpuSubmitNanos += end - middle; measuredFrames++; }
                for (int i = 0; i < 2; i++) {
                    int x = left + i * (side + 16);
                    blit(graphics, targets[i].getColorTextureId(), x, 28, side);
                    graphics.drawCenteredString(font, i == 0 ? "CPU / 256 bones" : "GPU TBO / 256 bones", x + side / 2, side + 36, 0xffeeeeee);
                }
                graphics.drawCenteredString(font, "GLB / " + STAGES[stage], width / 2, 9, 0xffeeeeee);
                graphics.flush();
                frames++;
                pollTimer();
                rendered = true;
            } catch (Exception | LinkageError error) { fail(Minecraft.getInstance(), error); }
        }

        private void draw(boolean accelerated) {
            Minecraft mc = Minecraft.getInstance();
            TextureTarget target = targets[accelerated ? 1 : 0];
            Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            boolean previousDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            PoseStack modelView = RenderSystem.getModelViewStack();
            modelView.pushPose();
            try {
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.enableDepthTest();
                modelView.setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                mc.gameRenderer.lightTexture().turnOnLightLayer();
                mc.gameRenderer.overlayTexture().setupOverlayColor();
                PoseStack poses = new PoseStack();
                poses.last().pose().set(view);
                poses.last().normal().set(view).invert().transpose();
                PoseSnapshot pose = state.getPoseSnapshot();
                if (accelerated) {
                    int priorBones = frames == 0 ? textureBufferBinding(GL13.GL_TEXTURE3) : 0;
                    int priorMorph = frames == 0 ? textureBufferBinding(GL13.GL_TEXTURE4) : 0;
                    boolean time = frames == 64 && (GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query);
                    if (time) {
                        startQuery = GL15.glGenQueries(); endQuery = GL15.glGenQueries();
                        GL33.glQueryCounter(startQuery, GL33.GL_TIMESTAMP);
                    }
                    var material = ModelTextureCache.material(scene, mesh);
                    gpu.renderWithPose(mesh, pose.getBoneTransforms(mesh), poses, buffers, ModelTextureCache.getTexture(scene, mesh),
                            mc.getTextureManager(), 0x00f000f0, OverlayTexture.NO_OVERLAY,
                            material == null ? new float[]{1, 1, 1, 1} : material.getDiffuseColor(),
                            pose.hasActiveMorphs(mesh) ? pose.getMorphWeights(mesh) : null);
                    if (time) GL33.glQueryCounter(endQuery, GL33.GL_TIMESTAMP);
                    if (frames == 0) {
                        require(priorBones == textureBufferBinding(GL13.GL_TEXTURE3), "high bone TBO leaked texture unit 3");
                        require(priorMorph == textureBufferBinding(GL13.GL_TEXTURE4), "high bone Morph leaked texture unit 4");
                    }
                } else cpu.draw(scene, state, pose.copyMeshInstances().stream()
                        .map(instance -> new SceneTransforms.Instance(instance.mesh(), instance.transform())).toList(), poses, buffers);
                buffers.endBatch();
            } finally {
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);
                if (!previousDepth) RenderSystem.disableDepthTest();
                mc.getMainRenderTarget().bindWrite(true);
            }
        }

        private void pollTimer() {
            if (endQuery == 0 || gpuNanos >= 0) return;
            if (GL15.glGetQueryObjecti(endQuery, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
            gpuNanos = GL33.glGetQueryObjectui64(endQuery, GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjectui64(startQuery, GL15.GL_QUERY_RESULT);
            require(gpuNanos >= 0, "negative GPU timestamp interval");
        }

        void verify() {
            boolean[] cpuMask = mask(targets[0]), gpuMask = mask(targets[1]);
            int union = 0, difference = 0, pixels = 0;
            for (int i = 0; i < cpuMask.length; i++) {
                if (cpuMask[i]) pixels++;
                if (cpuMask[i] || gpuMask[i]) union++;
                if (cpuMask[i] != gpuMask[i]) difference++;
            }
            require(pixels > 5000, "blank or truncated high bone target");
            double mismatch = difference / (double) union;
            require(mismatch <= .01, "256-bone CPU/GPU mismatch " + mismatch);
            if (stage == 0) zeroMask = cpuMask;
            if (stage == 1) {
                int changed = 0;
                for (int i = 0; i < cpuMask.length; i++) if (cpuMask[i] != zeroMask[i]) changed++;
                require(changed > 100, "combined Morph did not change the 256-bone silhouette");
                activeMask = gpuMask;
                AssimpLoader.LOGGER.info("[HighBoneSmoke] Combined Morph changed {} pixels", changed);
            }
            if (stage == 2) require(Arrays.equals(zeroMask, cpuMask), "Morph zero did not restore high bone geometry");
            if (stage == 3) require(Arrays.equals(activeMask, gpuMask), "GPU 256-bone/Morph recreation changed pixels");
            AssimpLoader.LOGGER.info("[HighBoneSmoke] {} pixels={} mismatch={} warmedFrames={} CPU-submit-us={} GPU-submit-us={} GPU-timestamp-us={} (one warmed timestamp pair, nonblocking retrieval)",
                    STAGES[stage], pixels, mismatch, measuredFrames,
                    measuredFrames == 0 ? -1 : cpuSubmitNanos / 1000.0 / measuredFrames,
                    measuredFrames == 0 ? -1 : gpuSubmitNanos / 1000.0 / measuredFrames, gpuNanos / 1000.0);
        }

        boolean texturesReady() {
            var material = ModelTextureCache.material(scene, mesh);
            require(material != null, "high bone checker material missing");
            ModelTextureCache.getTexture(scene, mesh);
            var status = ModelTextureCache.status(scene, material);
            require(!status.failed(), "high bone albedo failed to load");
            return status.ready();
        }

        private void clearQueries() {
            if (startQuery != 0) GL15.glDeleteQueries(startQuery);
            if (endQuery != 0) GL15.glDeleteQueries(endQuery);
            startQuery = endQuery = 0; gpuNanos = -1;
        }

        void release() {
            clearQueries();
            ClientAnimationManager.getInstance().release(OWNER);
            for (TextureTarget target : targets) if (target != null) target.destroyBuffers();
        }
    }

    private static final class CpuRenderer extends BaseAssimpRenderer {
        @Override protected String getModelName() { return "highbone_validation"; }
        void draw(AssimpScene scene, AnimationState state, List<SceneTransforms.Instance> instances, PoseStack stack, MultiBufferSource buffers) {
            renderInstances(scene, state, instances, stack, buffers, 0x00f000f0, OverlayTexture.NO_OVERLAY);
        }
    }

    private static boolean[] mask(TextureTarget target) {
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            boolean[] result = new boolean[SIZE * SIZE];
            for (int i = 0; i < result.length; i++) result[i] = (pixels.get(i * 4 + 3) & 255) > 127;
            return result;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous);
            MemoryUtil.memFree(pixels);
        }
    }

    private static int textureBufferBinding(int unit) {
        int previous = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(unit);
        int texture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        RenderSystem.activeTexture(previous);
        return texture;
    }

    private static void blit(GuiGraphics graphics, int texture, int x, int y, int side) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader); RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x, y + side, 0).uv(0, 0).endVertex();
        buffer.vertex(pose, x + side, y + side, 0).uv(1, 0).endVertex();
        buffer.vertex(pose, x + side, y, 0).uv(1, 1).endVertex();
        buffer.vertex(pose, x, y, 0).uv(0, 1).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
