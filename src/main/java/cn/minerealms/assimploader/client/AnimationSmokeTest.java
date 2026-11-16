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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Opt-in FBX validation only. Frozen simulation samples permit delayed texture capture. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class AnimationSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("animationtest.enabled");
    private static final ResourceLocation MODEL = new ResourceLocation("assimploader", "models/validation/animation_probe.fbx");
    private static final String[] STAGES = {"independent", "crossfade", "interrupted", "stopped", "reloaded"};
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final long SETTLE_MS = 10_000;
    private static final int SIZE = 384;
    private static final Object OWNER_A = new Object(), OWNER_B = new Object();
    private static long changedAt = System.currentTimeMillis();
    private static int stage = -1;
    private static boolean waiting, done;
    private static SmokeScreen screen;

    private AnimationSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            long elapsed = System.currentTimeMillis() - changedAt;
            require(elapsed < 120_000, "timeout at stage " + stage);
            if (stage < 0) {
                if (mc.screen instanceof TitleScreen && elapsed >= 3_000) {
                    screen = new SmokeScreen();
                    screen.load();
                    stage = 0;
                    screen.prepare(stage);
                    mc.setScreen(screen);
                    changedAt = System.currentTimeMillis();
                }
                return;
            }
            if (waiting || elapsed < SETTLE_MS || !screen.rendered) return;
            screen.verifyPixels();
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error before capture");
            String name = "assimp-animation-" + RUN + "-" + STAGES[stage] + ".png";
            waiting = true;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                try {
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)),
                            "screenshot missing: " + message.getString());
                    AssimpLoader.LOGGER.info("[AnimationSmoke] Captured {}", name);
                    advance(mc);
                } catch (Exception | LinkageError failure) { fail(mc, failure); }
            }));
        } catch (Exception | LinkageError failure) { fail(mc, failure); }
    }

    private static void advance(Minecraft mc) {
        if (++stage == STAGES.length) {
            done = true;
            AssimpLoader.LOGGER.info("[AnimationSmoke] SUCCESS: independent FBX instances, crossfade, interruption, bind stop, attachments, CPU/GPU masks and reload; run={}", RUN);
            screen.release();
            mc.stop();
        } else if (stage == 4) {
            AnimationState oldA = screen.a, oldB = screen.b;
            PoseSnapshot oldPose = oldA.getPoseSnapshot();
            Matrix4f oldSocket = oldPose.getNodeTransform("ToolSocket");
            AssimpScene oldScene = screen.scene;
            changedAt = System.currentTimeMillis();
            mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
                try {
                    if (failure != null) throw new IllegalStateException("resource reload failed", failure);
                    require(oldA.isClosed() && oldB.isClosed(), "reload did not close managed instances");
                    require(oldSocket.equals(oldPose.getNodeTransform("ToolSocket"), 1e-5f), "reload mutated retained snapshot");
                    screen.load();
                    require(screen.scene != oldScene, "reload reused previous scene");
                    screen.prepare(stage);
                    waiting = false;
                    changedAt = System.currentTimeMillis();
                } catch (Exception | LinkageError error) { fail(mc, error); }
            }));
        } else {
            screen.prepare(stage);
            waiting = false;
            changedAt = System.currentTimeMillis();
        }
    }

    private static void fail(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        AssimpLoader.LOGGER.error("[AnimationSmoke] FAILED at stage " + stage, failure);
        if (screen != null) screen.release();
        mc.stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class SmokeScreen extends Screen {
        private final TextureTarget[] targets = new TextureTarget[4];
        private final CpuRenderer cpu = new CpuRenderer();
        private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        private AssimpScene scene;
        private AnimationState a, b;
        private PoseSnapshot unchangedB;
        private SkinnedMeshRenderer gpu;
        private Matrix4f projection, view;
        private boolean rendered;

        SmokeScreen() { super(Component.literal("AssimpLoader Animation Validation")); }

        void load() throws Exception {
            rendered = false;
            scene = new AssimpModelLoader().loadModelFromResources(Minecraft.getInstance().getResourceManager(), MODEL, ModelImportMode.DYNAMIC);
            require(scene.hasSkeleton() && scene.getAnimations().size() >= 3, "FBX must contain a skeleton and three clips");
            ClientRenderResources.checkGeneration();
            var manager = ClientAnimationManager.getInstance();
            a = manager.getOrCreateAnimationState(OWNER_A, MODEL.toString(), scene);
            b = manager.getOrCreateAnimationState(OWNER_B, MODEL.toString(), scene);
            require(a != null && b != null && a != b, "independent instances unavailable");
            require(a.getPoseSnapshot().getNodeTransform("ToolSocket") != null, "missing ToolSocket");
            AssimpLoader.LOGGER.info("[AnimationSmoke] Imported clips={}, meshes={}, bounds={}",
                    Arrays.toString(a.getAvailableAnimations()), scene.getMeshCount(), a.getPoseSnapshot().getBounds());
            gpu = ClientRenderResources.getSkinnedRenderer();
            require(gpu != null && gpu.isInitialized(), "GPU renderer unavailable; fallback cannot pass this smoke");
            for (AssimpMesh mesh : scene.getMeshes()) {
                if (mesh.hasBones()) require(gpu.supports(a.getPoseSnapshot().getBoneCount(mesh)), "GPU palette unsupported");
            }
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] == null) targets[i] = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
                targets[i].setClearColor(0, 0, 0, 0);
            }
        }

        private String clip(String suffix) {
            return Arrays.stream(a.getAvailableAnimations()).filter(name -> name.endsWith(suffix))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing clip " + suffix));
        }

        void prepare(int currentStage) {
            rendered = false;
            a.resume();
            if (currentStage == 0 || currentStage == 4) {
                b.resume();
                require(a.transitionTo(clip("Idle"), 0), "Idle unavailable");
                a.update(.25f);
                require(b.transitionTo(clip("Reach"), 0), "Reach unavailable");
                b.update(.9f);
                b.pause();
                unchangedB = b.getPoseSnapshot();
                require(!a.getPoseSnapshot().getNodeTransform("ToolSocket").equals(unchangedB.getNodeTransform("ToolSocket"), 1e-4f), "clips produce identical socket positions");
            } else if (currentStage == 1) {
                require(a.transitionTo(clip("Reach"), 2), "Reach transition unavailable");
                a.update(1);
                require(a.isTransitioning(), "crossfade midpoint did not remain active");
            } else if (currentStage == 2) {
                Matrix4f before = a.getPoseSnapshot().getNodeTransform("ToolSocket");
                require(a.transitionTo(clip("Fold"), 2), "Fold transition unavailable");
                require(before.equals(a.getPoseSnapshot().getNodeTransform("ToolSocket"), 1e-4f), "interrupting crossfade caused a jump");
                a.update(.5f);
            } else if (currentStage == 3) {
                a.stop();
                PoseSnapshot bind = PoseSnapshot.capture(0, scene, Map.of());
                require(a.getCurrentAnimation() == null && !a.isTransitioning(), "stop retained playback");
                require(bind.getNodeTransform("ToolSocket").equals(a.getPoseSnapshot().getNodeTransform("ToolSocket"), 1e-4f), "stop did not restore bind pose");
            }
            a.pause();
            require(b.getPoseSnapshot() == unchangedB, "changing A advanced B");
            for (AnimationState state : new AnimationState[]{a, b}) {
                PoseSnapshot pose = state.getPoseSnapshot();
                Matrix4f expected = pose.getNodeTransform("ToolSocket").mul(new Matrix4f().translation(.15f, 0, 0));
                require(expected.equals(pose.getAttachmentTransform("ToolSocket", new Matrix4f().translation(.15f, 0, 0)), 1e-5f), "attachment composition mismatch");
            }
            AABB box = a.getPoseSnapshot().getBounds().minmax(b.getPoseSnapshot().getBounds());
            float radius = (float) Math.max(.001, Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) * .65);
            projection = new Matrix4f().ortho(-radius, radius, -radius, radius, -radius * 8, radius * 8);
            view = new Matrix4f().rotateX(.18f).rotateY(-.4f).translate((float) -(box.minX + box.maxX) / 2,
                    (float) -(box.minY + box.maxY) / 2, (float) -(box.minZ + box.maxZ) / 2);
            AssimpLoader.LOGGER.info("[AnimationSmoke] Prepared stage {}; capture waits at least {} ms", STAGES[currentStage], SETTLE_MS);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xff23282c);
                if (waiting && stage == 4) return;
                graphics.flush();
                int side = Math.min((width - 48) / 2, (height - 82) / 2);
                int left = (width - side * 2 - 16) / 2;
                for (int i = 0; i < targets.length; i++) {
                    drawTarget(i, i < 2 ? a : b, (i & 1) != 0);
                    int x = left + (i & 1) * (side + 16), y = 32 + (i / 2) * (side + 24);
                    blit(graphics, targets[i].getColorTextureId(), x, y, side);
                    Vector4f point = new Vector4f(0, 0, 0, 1);
                    (i < 2 ? a : b).getPoseSnapshot().getAttachmentTransform("ToolSocket", new Matrix4f().translation(.15f, 0, 0)).transform(point);
                    view.transform(point);
                    projection.transform(point);
                    int px = x + Math.round((point.x / point.w + 1) * side / 2);
                    int py = y + Math.round((1 - point.y / point.w) * side / 2);
                    graphics.fill(px - 4, py - 1, px + 5, py + 2, 0xffffd84b);
                    graphics.fill(px - 1, py - 4, px + 2, py + 5, 0xffffd84b);
                    graphics.drawCenteredString(font, (i < 2 ? "A" : "B") + ((i & 1) == 0 ? " / CPU" : " / GPU"), x + side / 2, y + side + 4, 0xffeeeeee);
                    graphics.flush();
                }
                graphics.drawCenteredString(font, "FBX / " + STAGES[stage], width / 2, 10, 0xffeeeeee);
                rendered = true;
            } catch (Exception | LinkageError failure) { fail(Minecraft.getInstance(), failure); }
        }

        private void drawTarget(int index, AnimationState state, boolean accelerated) {
            Minecraft mc = Minecraft.getInstance();
            TextureTarget target = targets[index];
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
                PoseSnapshot snapshot = state.getPoseSnapshot();
                List<SceneTransforms.Instance> instances = snapshot.copyMeshInstances().stream()
                        .map(instance -> new SceneTransforms.Instance(instance.mesh(), instance.transform())).toList();
                if (accelerated) {
                    for (SceneTransforms.Instance instance : instances) {
                        AssimpMesh mesh = instance.mesh();
                        if (!mesh.hasBones()) continue;
                        var material = ModelTextureCache.material(scene, mesh);
                        gpu.renderWithPose(mesh, snapshot.getBoneTransforms(mesh), poses, buffers, ModelTextureCache.getTexture(scene, mesh),
                                mc.getTextureManager(), 0x00f000f0, OverlayTexture.NO_OVERLAY,
                                material == null ? new float[]{1, 1, 1, 1} : material.getDiffuseColor());
                    }
                    cpu.draw(scene, state, instances.stream().filter(instance -> !instance.mesh().hasBones()).toList(), poses, buffers);
                } else cpu.draw(scene, state, instances, poses, buffers);
                buffers.endBatch();
            } finally {
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);
                if (!previousDepth) RenderSystem.disableDepthTest();
                mc.getMainRenderTarget().bindWrite(true);
            }
        }

        void verifyPixels() {
            require(b.getPoseSnapshot() == unchangedB, "waiting for textures advanced instance B");
            for (int row = 0; row < 2; row++) {
                boolean[] cpuMask = mask(targets[row * 2]), gpuMask = mask(targets[row * 2 + 1]);
                int union = 0, difference = 0, cpuPixels = 0, gpuPixels = 0;
                for (int i = 0; i < cpuMask.length; i++) {
                    if (cpuMask[i]) cpuPixels++;
                    if (gpuMask[i]) gpuPixels++;
                    if (cpuMask[i] || gpuMask[i]) union++;
                    if (cpuMask[i] != gpuMask[i]) difference++;
                }
                require(cpuPixels > 100 && gpuPixels > 100, "blank CPU/GPU target for row " + row);
                double mismatch = difference / (double) union;
                require(mismatch <= .02, "CPU/GPU silhouette mismatch " + mismatch + " for row " + row);
                AssimpLoader.LOGGER.info("[AnimationSmoke] {} row {} CPU pixels={} GPU pixels={} silhouette mismatch={}", STAGES[stage], row, cpuPixels, gpuPixels, mismatch);
            }
        }

        void release() {
            ClientAnimationManager.getInstance().release(OWNER_A);
            ClientAnimationManager.getInstance().release(OWNER_B);
            for (TextureTarget target : targets) if (target != null) target.destroyBuffers();
        }
    }

    private static final class CpuRenderer extends BaseAssimpRenderer {
        @Override protected String getModelName() { return "animation_validation"; }
        void draw(AssimpScene scene, AnimationState state, List<SceneTransforms.Instance> instances,
                  PoseStack stack, MultiBufferSource buffers) {
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

    private static void blit(GuiGraphics graphics, int texture, int x, int y, int side) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
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
