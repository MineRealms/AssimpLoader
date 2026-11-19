package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.animation.MaterialAnimation;
import cn.minerealms.assimploader.render.AnimatedMaterialConsumer;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.AssimpRenderTypes;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.rendering.CachedMeshRenderer;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

/** Shared scene traversal and vanilla fallback consuming one completed pose per draw. */
public abstract class BaseAssimpRenderer {
    protected abstract String getModelName();

    protected boolean checkHotReload(AssimpScene scene, String modelName) {
        return ClientRenderResources.checkGeneration();
    }

    protected void loadEmbeddedTextures(AssimpScene scene, String modelName) {
        // Textures are decoded lazily once when a material is drawn.
    }

    protected ResourceLocation getTextureLocation(AssimpScene scene, AssimpMesh mesh, String modelName) {
        return ModelTextureCache.getTexture(scene, mesh);
    }

    protected RenderType getRenderType(AssimpMaterial material, ResourceLocation texture) {
        ResourceLocation location = texture == null ? ModelTextureCache.WHITE : texture;
        return AssimpRenderTypes.entity(location, material != null && material.isTransparent(), material != null && material.isDoubleSided());
    }

    /** A scoped index mask shares pose/deformation resources with the full character. */
    public void renderSelected(AssimpScene scene, AnimationState state, PoseSnapshot snapshot,
                               cn.minerealms.assimploader.rendering.MeshDrawSelection selection, PoseStack stack,
                               MultiBufferSource buffers, int light, int overlay, float partialTick) {
        try (var ignored = selection.activate()) {
            if (!cn.minerealms.assimploader.rendering.OculusEntityBufferCache.renderPosed(state, snapshot, stack, light, overlay, false))
                try(var transform=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.transform(scene,stack)) {
                    renderSnapshotScene(scene, state, cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.pose(scene,validateSnapshot(scene, snapshot)), stack, buffers, light, overlay);
                }
        }
    }

    protected void renderMeshList(AssimpScene scene, PoseStack stack, MultiBufferSource buffers,
                                  int light, int overlay, String modelName, boolean first) {
        renderScene(scene, null, stack, buffers, light, overlay);
    }

    protected void renderSceneGraph(AssimpScene scene, PoseStack stack, MultiBufferSource buffers,
                                   int light, int overlay, String modelName, boolean first) {
        renderScene(scene, null, stack, buffers, light, overlay);
    }

    public void renderScene(AssimpScene scene, AnimationState state, PoseStack stack,
                            MultiBufferSource buffers, int light, int overlay) {
        renderScene(scene, state, stack, buffers, light, overlay, renderPartialTick());
    }

    public void renderScene(AssimpScene scene, AnimationState state, PoseStack stack,
                            MultiBufferSource buffers, int light, int overlay, float partialTick) {
        ClientRenderResources.checkGeneration();
        PoseSnapshot snapshot = snapshotForScene(scene, state, partialTick);
        try(var transform=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.transform(scene,stack)) {
            renderSnapshotScene(scene, state, cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.pose(scene,snapshot), stack, buffers, light, overlay);
        }
    }

    private void renderSnapshotScene(AssimpScene scene, AnimationState state, PoseSnapshot snapshot, PoseStack stack,
                                     MultiBufferSource buffers, int light, int overlay) {
        List<SceneTransforms.Instance> instances = snapshot == null ? SceneTransforms.instances(scene)
                : snapshot.copyMeshInstances().stream()
                .map(instance -> new SceneTransforms.Instance(instance.mesh(), instance.transform(), instance.node())).toList();
        instances=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.instances(scene,instances);
        ModelInstance configured = state == null ? null : state.getModelInstance();
        if (cn.minerealms.assimploader.rendering.MeshDrawSelection.current() == null)
            cn.minerealms.assimploader.debug.DebugGeometryRenderer.render(scene,snapshot,configured,stack,buffers);
        if(configured!=null) configured.noteRenderPass(OculusCompat.isRenderingShadowPass());
        if (configured != null && configured.hasMaterialAnimation()) {
            for (int pass = 0; pass < 2; pass++) for (SceneTransforms.Instance instance : instances) {
                AssimpMaterial material = ModelTextureCache.material(scene, instance.mesh());
                MaterialAnimation.Sample sample = configured.material(material == null ? "" : material.getName());
                boolean transparent = (material != null && material.isTransparent()) || (sample != null && sample.alpha() < 1);
                if (transparent == (pass == 1)) renderInstance(scene, snapshot, instance, stack, buffers, light, overlay, sample);
            }
            return;
        }
        var optimization = AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                AssimpLoaderConfig.OptimizationLevel.AUTO);
        if (snapshot != null && scene.hasSkeleton()
                && optimization != AssimpLoaderConfig.OptimizationLevel.FALLBACK
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.gpuSkinningEnabled::get, true)
                && !OculusCompat.shouldAvoidCustomShaders()) {
            var skinned = ClientRenderResources.getSkinnedRenderer();
            if (skinned != null) {
                // A palette already contains scene parent transforms, so skinned meshes do not
                // receive their scene-node transform a second time.
                // A small glass overlay must not disable GPU skinning for the opaque body.
                for (int pass = 0; pass < 2; pass++) for (SceneTransforms.Instance instance : instances) {
                    var material = ModelTextureCache.material(scene, instance.mesh());
                    boolean transparent = material != null && material.isTransparent();
                    if (transparent != (pass == 1)) continue;
                    var selection = cn.minerealms.assimploader.rendering.MeshDrawSelection.current();
                    if (selection != null && !selection.contains(instance.mesh())) continue;
                    Matrix4f[] palette = snapshot.getBoneTransforms(instance.mesh());
                    if (!transparent && instance.mesh().hasBones() && palette != null
                            && (!snapshot.hasActiveMorphs(instance.mesh()) || skinned.supportsMorph(instance.mesh()))
                            && skinned.isInitialized() && skinned.supports(palette.length)) {
                        try {
                            skinned.renderWithSnapshot(state, snapshot, instance.mesh(), stack, buffers,
                                    ModelTextureCache.getTexture(scene, instance.mesh()),
                                    Minecraft.getInstance().getTextureManager(), light, overlay,
                                    cn.minerealms.assimploader.debug.MaterialDebugViews.tint(material));
                        } catch (RuntimeException failure) {
                            ClientRenderResources.disableGpuSkinning(failure);
                            renderInstance(scene, snapshot, instance, stack, buffers, light, overlay);
                        }
                    } else {
                        renderInstance(scene, snapshot, instance, stack, buffers, light, overlay);
                    }
                }
                return;
            }
        }
        if (snapshot != null && (scene.hasSkeleton() || snapshot.hasActiveMorphs())) {
            renderSnapshotInstances(scene, snapshot, instances, stack, buffers, light, overlay);
            return;
        }
        if (!cn.minerealms.assimploader.debug.MaterialDebugViews.active()
                && cn.minerealms.assimploader.rendering.MeshDrawSelection.current() == null
                && CachedMeshRenderer.render(scene, instances, stack, buffers, light, overlay)) return;
        renderInstances(scene, instances, stack, buffers, light, overlay);
    }

    static PoseSnapshot snapshotForScene(AssimpScene scene, AnimationState state) {
        PoseSnapshot snapshot = state == null ? null : state.getPoseSnapshot();
        return validateSnapshot(scene, snapshot);
    }

    static PoseSnapshot snapshotForScene(AssimpScene scene, AnimationState state, float partialTick) {
        return validateSnapshot(scene, state == null ? null : state.getRenderPose(partialTick));
    }

    private static PoseSnapshot validateSnapshot(AssimpScene scene, PoseSnapshot snapshot) {
        if (snapshot != null && snapshot.getScene() == scene) return snapshot;
        // Draw imported defaults even when a caller has no animation owner.
        for (AssimpMesh mesh : scene.getMeshes()) if (mesh.hasMorphTargets())
            return PoseSnapshot.capture(0, scene, Map.of());
        return null;
    }

    private static float renderPartialTick() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? 1 : client.getFrameTime();
    }

    private void renderInstance(AssimpScene scene, PoseSnapshot snapshot, SceneTransforms.Instance instance,
                                 PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        renderInstance(scene, snapshot, instance, stack, buffers, light, overlay, null);
    }

    private void renderInstance(AssimpScene scene, PoseSnapshot snapshot, SceneTransforms.Instance instance,
                                 PoseStack stack, MultiBufferSource buffers, int light, int overlay, MaterialAnimation.Sample sample) {
        AssimpMesh mesh = instance.mesh();
        var selection = cn.minerealms.assimploader.rendering.MeshDrawSelection.current();
        if (selection != null && !selection.contains(mesh)) return;
        AssimpMaterial material = ModelTextureCache.material(scene, mesh);
        ResourceLocation texture = getTextureLocation(scene, mesh, getModelName());
        RenderType type = sample != null && sample.alpha() < 1 ? AssimpRenderTypes.entity(texture == null ? ModelTextureCache.WHITE : texture, true, material != null && material.isDoubleSided())
                : getRenderType(material, texture);
        VertexConsumer consumer = buffers.getBuffer(type);
        if (sample != null) consumer = new AnimatedMaterialConsumer(consumer, sample);
        if (snapshot != null && mesh.hasBones()) {
            var geometry = CpuSkinning.geometry(snapshot, mesh);
            renderMesh(mesh, geometry.positions(), geometry.normals(), material, stack, consumer, light, overlay);
        } else {
            stack.pushPose();
            try {
                SceneTransforms.apply(stack, instance.transform());
                if (snapshot != null && snapshot.hasActiveMorphs(mesh)) {
                    var geometry = CpuSkinning.geometry(snapshot, mesh);
                    renderMesh(mesh, geometry.positions(), geometry.normals(), material, stack, consumer, light, overlay);
                } else renderMesh(mesh, material, stack, consumer, light, overlay);
            } finally {
                stack.popPose();
            }
        }
    }

    public void renderInstances(AssimpScene scene, List<SceneTransforms.Instance> instances, PoseStack stack,
                                MultiBufferSource buffers, int light, int overlay) {
        renderSnapshotInstances(scene, snapshotForScene(scene, null), instances, stack, buffers, light, overlay);
    }

    protected void renderInstances(AssimpScene scene, AnimationState state, List<SceneTransforms.Instance> instances,
                                   PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        renderSnapshotInstances(scene, snapshotForScene(scene, state, renderPartialTick()), instances, stack, buffers, light, overlay);
    }

    private void renderSnapshotInstances(AssimpScene scene, PoseSnapshot snapshot, List<SceneTransforms.Instance> instances,
                                         PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        // Glass must not write depth before the model's opaque surfaces have been submitted.
        for (int pass = 0; pass < 2; pass++) {
            boolean transparent = pass == 1;
            for (SceneTransforms.Instance instance : instances) {
                AssimpMaterial material = ModelTextureCache.material(scene, instance.mesh());
                if ((material != null && material.isTransparent()) == transparent) {
                    renderInstance(scene, snapshot, instance, stack, buffers, light, overlay);
                }
            }
        }
    }

    protected void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack stack,
                              VertexConsumer consumer, int light, int overlay) {
        renderMesh(mesh, mesh.getVertices(), mesh.getNormals(), material, stack, consumer, light, overlay);
    }

    protected void renderMesh(AssimpMesh mesh, float[] vertices, float[] normals, AssimpMaterial material, PoseStack stack,
                              VertexConsumer consumer, int light, int overlay) {
        float[] uv = mesh.getTexCoords();
        int[] indices = mesh.getIndices();
        var selection = cn.minerealms.assimploader.rendering.MeshDrawSelection.current();
        int[] selected = selection == null ? null : selection.triangles(mesh);
        float[] color = cn.minerealms.assimploader.debug.MaterialDebugViews.tint(material);
        int count = vertices.length / 3;
        for (int triangle = 0, total = selected == null ? indices.length / 3 : selected.length; triangle < total; triangle++) {
            int i = (selected == null ? triangle : selected[triangle]) * 3;
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            if (a < 0 || b < 0 || c < 0 || a >= count || b >= count || c >= count) continue;
            addVertex(consumer, stack.last().pose(), stack.last().normal(), vertices, normals, uv,
                    c, color[0], color[1], color[2], color[3], light, overlay);
            addVertex(consumer, stack.last().pose(), stack.last().normal(), vertices, normals, uv,
                    b, color[0], color[1], color[2], color[3], light, overlay);
            addVertex(consumer, stack.last().pose(), stack.last().normal(), vertices, normals, uv,
                    a, color[0], color[1], color[2], color[3], light, overlay);
        }
    }

    protected void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                              float[] vertices, float[] normals, float[] uv, int index,
                              float r, float g, float b, float a, int light, int overlay) {
        int p = index * 3, t = index * 2;
        float nx = normals != null && p + 2 < normals.length ? normals[p] : 0;
        float ny = normals != null && p + 2 < normals.length ? normals[p + 1] : 1;
        float nz = normals != null && p + 2 < normals.length ? normals[p + 2] : 0;
        float x = normal.m00() * nx + normal.m10() * ny + normal.m20() * nz;
        float y = normal.m01() * nx + normal.m11() * ny + normal.m21() * nz;
        float z = normal.m02() * nx + normal.m12() * ny + normal.m22() * nz;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length > 1.0e-8f) { x /= length; y /= length; z /= length; }
        else { x = 0; y = 1; z = 0; }
        consumer.vertex(pose, vertices[p], vertices[p + 1], vertices[p + 2])
                .color(r, g, b, a).uv(uv != null && t + 1 < uv.length ? uv[t] : 0,
                        uv != null && t + 1 < uv.length ? uv[t + 1] : 0)
                .overlayCoords(overlay).uv2(light).normal(x, y, z).endVertex();
    }
}
