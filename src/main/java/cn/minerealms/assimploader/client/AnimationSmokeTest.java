package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.MaterialAnimation;
import cn.minerealms.assimploader.animation.ik.LookAtModifier;
import cn.minerealms.assimploader.animation.physics.SpringBoneModifier;
import cn.minerealms.assimploader.render.AttachmentTrailRenderer;
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
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Opt-in FBX validation only. Frozen simulation samples permit delayed texture capture. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class AnimationSmokeTest {
    private static final boolean DEER = Boolean.getBoolean("deertest.enabled");
    private static final boolean ENABLED = Boolean.getBoolean("animationtest.enabled") || DEER;
    private static final String SOCKET = DEER ? "head" : "ToolSocket";
    private static final ResourceLocation MODEL = new ResourceLocation("assimploader", DEER ? "models/validation/deer_showcase.glb" : "models/validation/animation_probe.fbx");
    private static final ResourceLocation MORPH_MODEL = new ResourceLocation("assimploader", "models/validation/animation_probe_morph.glb");
    private static final String[] STAGES = DEER ? new String[]{"deer-idle", "deer-walk", "deer-gallop", "deer-baked-ik", "deer-reloaded", "deer-runtime-ik", "deer-spring"} : new String[]{"independent", "crossfade", "interrupted", "stopped", "reloaded",
            "masked-layer", "look-at", "spring", "morph-zero", "morph-one", "morph-restored", "trail", "morph-reloaded", "morph-active-reloaded"};
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final long SETTLE_MS = 10_000;
    private static final int SIZE = 384;
    private static final Object OWNER_A = new Object(), OWNER_B = new Object();
    private static long changedAt = System.currentTimeMillis();
    private static int stage = -1;
    private static boolean waiting, done;
    private static SmokeScreen screen;

    private AnimationSmokeTest() {}

    public static boolean deerComplete() { return DEER && done && stage == STAGES.length; }

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
            if (DEER) mc.setScreen(new TitleScreen());
            else mc.stop();
        } else if (stage == 4 || stage == 12) {
            AnimationState oldA = screen.a, oldB = screen.b;
            PoseSnapshot oldPose = oldA.getPoseSnapshot();
            Matrix4f oldSocket = oldPose.getNodeTransform(SOCKET);
            AssimpScene oldScene = screen.scene;
            changedAt = System.currentTimeMillis();
            mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
                try {
                    if (failure != null) throw new IllegalStateException("resource reload failed", failure);
                    require(oldA.isClosed() && oldB.isClosed(), "reload did not close managed instances");
                    require(oldSocket.equals(oldPose.getNodeTransform(SOCKET), 1e-5f), "reload mutated retained snapshot");
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
        private boolean[] morphZeroMask;
        private boolean[] morphOneGpuMask;

        SmokeScreen() { super(Component.literal("AssimpLoader Animation Validation")); }

        void load() throws Exception {
            rendered = false;
            ResourceLocation resource = stage >= 8 ? MORPH_MODEL : MODEL;
            scene = new AssimpModelLoader().loadModelFromResources(Minecraft.getInstance().getResourceManager(), resource, ModelImportMode.DYNAMIC);
            require(scene.hasSkeleton() && scene.getAnimations().size() >= 3, "FBX must contain a skeleton and three clips");
            ClientRenderResources.checkGeneration();
            var manager = ClientAnimationManager.getInstance();
            a = manager.getOrCreateAnimationState(OWNER_A, resource.toString(), scene);
            b = manager.getOrCreateAnimationState(OWNER_B, resource.toString(), scene);
            require(a != null && b != null && a != b, "independent instances unavailable");
            require(a.getModelInstance() != null && b.getModelInstance() != null, "JSON instance sidecar was not loaded");
            require(a.getPoseSnapshot().getNodeTransform(SOCKET) != null, "missing attachment node");
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
            if (DEER) { prepareDeer(currentStage); return; }
            if (currentStage == 8) {
                ClientAnimationManager.getInstance().release(OWNER_A);
                ClientAnimationManager.getInstance().release(OWNER_B);
                try { load(); } catch (Exception failure) { throw new IllegalStateException("Morph GLB load failed",failure); }
            }
            a.resume();
            if (currentStage == 0 || currentStage == 4 || currentStage == 8 || currentStage == 12) {
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
            } else if (currentStage == 5) {
                Matrix4f before=a.getPoseSnapshot().getNodeTransform("Elbow");
                a.setLayerWeight("upper",.85f);
                a.update(.25f);
                require(!before.equals(a.getPoseSnapshot().getNodeTransform("Elbow"),1e-4f),"layer did not change pose");
            } else if (currentStage == 6) {
                a.removeLayer("upper");
                a.addModifier("aim",new LookAtModifier("Elbow",new Vector3f(0,1,0),new Vector3f(1,2,1),.8f,1.5f));
                a.update(.05f);
            } else if (currentStage == 7) {
                a.removeModifier("aim");
                SpringBoneModifier spring=new SpringBoneModifier(new SpringBoneModifier.Settings("Elbow",new Vector3f(0,1.2f,0),
                        15,3,new Vector3f(4,-9.81f,0),.03f,1.0/120,8,4));
                a.addModifier("spring",spring);
                for(int i=0;i<30;i++) a.update(.05f);
                require(spring.getTailPosition()!=null && spring.getTailPosition().isFinite(),"spring did not integrate");
            } else if (currentStage == 9 || currentStage == 10 || currentStage == 13) {
                AssimpMesh morph=scene.getMeshes().stream().filter(AssimpMesh::hasMorphTargets).findFirst().orElseThrow();
                boolean active=currentStage!=10;
                a.setMorphWeights(morph,new float[]{active ? 1 : 0});
                require(a.getPoseSnapshot().hasActiveMorphs(morph)==active,"morph weight publication failed");
            } else if (currentStage == 11) {
                require(a.getModelInstance().requestState("reach"),"JSON state request failed");
                for(int i=0;i<15;i++) a.update(.05f);
                require(a.getModelInstance().trails().get("blade").samples().size()>2,"trail did not sample ticks");
                a.getModelInstance().setMaterialAnimation("*",new MaterialAnimation(.3f,.1f,.7f,1,.9f,.85f,1,.25f));
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

        private void prepareDeer(int currentStage) {
            a.resume();
            if (currentStage == 0 || currentStage == 4) {
                b.resume();
                require(b.transitionTo("idle", 0), "deer idle missing");
                b.update(.25f);
                b.pause();
                unchangedB = b.getPoseSnapshot();
            }
            a.removeModifier("deer-ik");
            String clip = switch (currentStage) { case 1 -> "walk"; case 2 -> "gallop"; case 3 -> "ik_probe"; default -> "idle"; };
            require(a.transitionTo(clip, 0), "deer clip missing: " + clip);
            a.update(currentStage == 3 ? .5f : .35f);
            if (currentStage == 5) {
                var target = a.getPoseSnapshot().getNodeTransform("hoofFL").getTranslation(new Vector3f()).add(0, .15f, .04f);
                a.addModifier("deer-ik", new cn.minerealms.assimploader.animation.ik.ChainIkModifier(
                        List.of("legFL0", "legFL1", "legFL2", "hoofFL"), target, 1, 32, .0001f));
                a.update(.05f);
                require(a.getPoseSnapshot().getNodeTransform("hoofFL").getTranslation(new Vector3f()).distance(target) < .003f,
                        "deer runtime IK missed target");
            }
            if (currentStage == 6) for (int i = 0; i < 30; i++) a.update(.05f);
            a.pause();
            require(b.getPoseSnapshot() == unchangedB, "deer state leaked into B");
            var box = a.getPoseSnapshot().getBounds().minmax(b.getPoseSnapshot().getBounds());
            float radius = 1.8f;
            projection = new Matrix4f().ortho(-radius, radius, -radius, radius, -16, 16);
            view = new Matrix4f().rotateX(.18f).rotateY(-.6f).translate((float) -(box.minX + box.maxX) / 2,
                    (float) -(box.minY + box.maxY) / 2, (float) -(box.minZ + box.maxZ) / 2);
            AssimpLoader.LOGGER.info("[AnimationSmoke] Prepared {}; capture waits {} ms", STAGES[currentStage], SETTLE_MS);
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (done) return;
            try {
                graphics.fill(0, 0, width, height, 0xff23282c);
                if (waiting && (stage == 4 || stage == 12)) return;
                graphics.flush();
                int side = Math.min((width - 48) / 2, (height - 82) / 2);
                int left = (width - side * 2 - 16) / 2;
                for (int i = 0; i < targets.length; i++) {
                    drawTarget(i, i < 2 ? a : b, (i & 1) != 0);
                    int x = left + (i & 1) * (side + 16), y = 32 + (i / 2) * (side + 24);
                    blit(graphics, targets[i].getColorTextureId(), x, y, side);
                    Vector4f point = new Vector4f(0, 0, 0, 1);
                    (i < 2 ? a : b).getPoseSnapshot().getAttachmentTransform(SOCKET, new Matrix4f().translation(.15f, 0, 0)).transform(point);
                    view.transform(point);
                    projection.transform(point);
                    int px = x + Math.round((point.x / point.w + 1) * side / 2);
                    int py = y + Math.round((1 - point.y / point.w) * side / 2);
                    graphics.fill(px - 4, py - 1, px + 5, py + 2, 0xffffd84b);
                    graphics.fill(px - 1, py - 4, px + 2, py + 5, 0xffffd84b);
                    boolean fallback=(i<2?a:b).getModelInstance().hasMaterialAnimation();
                    graphics.drawCenteredString(font, (i < 2 ? "A" : "B") + ((i & 1) == 0 ? " / CPU" : fallback?" / fallback":" / GPU"), x + side / 2, y + side + 4, 0xffeeeeee);
                    graphics.flush();
                }
                graphics.drawCenteredString(font, (stage>=8?"GLB":"FBX")+" / " + STAGES[stage], width / 2, 10, 0xffeeeeee);
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
                if (state.getModelInstance().hasMaterialAnimation()) cpu.renderScene(scene,state,poses,buffers,0x00f000f0,OverlayTexture.NO_OVERLAY);
                else if (accelerated) {
                    for (SceneTransforms.Instance instance : instances) {
                        AssimpMesh mesh = instance.mesh();
                        if (!mesh.hasBones()) continue;
                        if(snapshot.hasActiveMorphs(mesh)) require(gpu.supportsMorph(mesh),"GPU Morph unavailable; this stage cannot pass via CPU fallback");
                        int previousMorphTexture=morphTextureBinding();
                        var material = ModelTextureCache.material(scene, mesh);
                        gpu.renderWithPose(mesh, snapshot.getBoneTransforms(mesh), poses, buffers, ModelTextureCache.getTexture(scene, mesh),
                                mc.getTextureManager(), 0x00f000f0, OverlayTexture.NO_OVERLAY,
                                material == null ? new float[]{1, 1, 1, 1} : material.getDiffuseColor(),
                                snapshot.hasActiveMorphs(mesh)?snapshot.getMorphWeights(mesh):null);
                        require(previousMorphTexture==morphTextureBinding(),"GPU Morph leaked texture unit 4 binding");
                    }
                    cpu.draw(scene, state, instances.stream().filter(instance -> !instance.mesh().hasBones()).toList(), poses, buffers);
                } else cpu.draw(scene, state, instances, poses, buffers);
                if (stage == 11) AttachmentTrailRenderer.render(state.getModelInstance(),poses,buffers,0x00f000f0);
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
            if (a.getPoseSnapshot().hasActiveMorphs()) AssimpLoader.LOGGER.info("[AnimationSmoke] Active Morph rendered through GPU delta TBO before skinning; no CPU fallback in accelerated column");
            if (stage==8) morphZeroMask=mask(targets[0]);
            if (stage==9) {
                morphOneGpuMask=mask(targets[1]);
                boolean[] deformed=mask(targets[0]); int changed=0;
                for(int i=0;i<deformed.length;i++) if(deformed[i]!=morphZeroMask[i]) changed++;
                require(changed>20,"Morph did not visibly change silhouette");
                AssimpLoader.LOGGER.info("[AnimationSmoke] Morph changed {} silhouette pixels",changed);
            }
            if (stage==10) require(Arrays.equals(morphZeroMask,mask(targets[0])),"Morph zero did not restore original silhouette");
            if (stage==13) require(Arrays.equals(morphOneGpuMask,mask(targets[1])),"GPU Morph changed after resource recreation");
            if (stage==11) AssimpLoader.LOGGER.info("[AnimationSmoke] Material UV/tint uses parameterized vanilla CPU submission; accelerated column explicitly uses same fallback");
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

    private static int morphTextureBinding() {
        int previous=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE4);
        int binding=GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        RenderSystem.activeTexture(previous);
        return binding;
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
