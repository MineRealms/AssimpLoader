package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
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

/** Shared scene traversal and allocation-free vanilla fallback. */
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
        return AssimpRenderTypes.entity(location, material != null && material.isTransparent());
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
        ClientRenderResources.checkGeneration();
        Map<String, Matrix4f> transforms = state != null && state.getCurrentAnimation() != null
                ? state.getCurrentAnimation().getBoneTransforms() : null;
        List<SceneTransforms.Instance> instances = SceneTransforms.instances(scene, transforms);
        var optimization = AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                AssimpLoaderConfig.OptimizationLevel.AUTO);
        if (state != null && state.hasSkinnedMeshes()
                && optimization != AssimpLoaderConfig.OptimizationLevel.FALLBACK
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.gpuSkinningEnabled::get, true)
                && !OculusCompat.shouldAvoidCustomShaders()
                && SceneTransforms.isOpaque(scene)) {
            var skinned = ClientRenderResources.getSkinnedRenderer();
            if (skinned != null) {
                // A palette already contains scene parent transforms, so skinned meshes do not
                // receive their scene-node transform a second time.
                for (SceneTransforms.Instance instance : instances) {
                    var hierarchy = state.getBoneHierarchy(instance.mesh());
                    if (instance.mesh().hasBones() && hierarchy != null && skinned.isInitialized() && skinned.supports(hierarchy)) {
                        var material = ModelTextureCache.material(scene, instance.mesh());
                        try {
                            skinned.renderWithPose(instance.mesh(), hierarchy, stack, buffers,
                                    ModelTextureCache.getTexture(scene, instance.mesh()),
                                    Minecraft.getInstance().getTextureManager(), light, overlay,
                                    material == null ? new float[]{1,1,1,1} : material.getDiffuseColor());
                        } catch (RuntimeException failure) {
                            ClientRenderResources.disableGpuSkinning(failure);
                            renderInstance(scene, state, instance, stack, buffers, light, overlay);
                        }
                    } else {
                        renderInstance(scene, state, instance, stack, buffers, light, overlay);
                    }
                }
                return;
            }
        }
        if (state != null && state.hasSkinnedMeshes()) {
            renderInstances(scene, state, instances, stack, buffers, light, overlay);
            return;
        }
        if (CachedMeshRenderer.render(scene, instances, stack, buffers, light, overlay)) return;
        renderInstances(scene, instances, stack, buffers, light, overlay);
    }

    private void renderInstance(AssimpScene scene, AnimationState state, SceneTransforms.Instance instance,
                                 PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        AssimpMesh mesh = instance.mesh();
        AssimpMaterial material = ModelTextureCache.material(scene, mesh);
        ResourceLocation texture = getTextureLocation(scene, mesh, getModelName());
        VertexConsumer consumer = buffers.getBuffer(getRenderType(material, texture));
        if (state != null && mesh.hasBones() && state.getBoneHierarchy(mesh) != null) {
            var geometry = CpuSkinning.geometry(state, mesh);
            renderMesh(mesh, geometry.positions(), geometry.normals(), material, stack, consumer, light, overlay);
        } else {
            stack.pushPose();
            try {
                SceneTransforms.apply(stack, instance.transform());
                renderMesh(mesh, material, stack, consumer, light, overlay);
            } finally {
                stack.popPose();
            }
        }
    }

    public void renderInstances(AssimpScene scene, List<SceneTransforms.Instance> instances, PoseStack stack,
                                MultiBufferSource buffers, int light, int overlay) {
        renderInstances(scene, null, instances, stack, buffers, light, overlay);
    }

    protected void renderInstances(AssimpScene scene, AnimationState state, List<SceneTransforms.Instance> instances,
                                   PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        // Glass must not write depth before the model's opaque surfaces have been submitted.
        for (int pass = 0; pass < 2; pass++) {
            boolean transparent = pass == 1;
            for (SceneTransforms.Instance instance : instances) {
                AssimpMaterial material = ModelTextureCache.material(scene, instance.mesh());
                if ((material != null && material.isTransparent()) == transparent) {
                    renderInstance(scene, state, instance, stack, buffers, light, overlay);
                }
            }
        }
    }

    protected void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack stack,
                              VertexConsumer consumer, int light, int overlay) {
        renderMesh(mesh, mesh.getVertices(), mesh.getNormals(), material, stack, consumer, light, overlay);
    }

    private void renderMesh(AssimpMesh mesh, float[] vertices, float[] normals, AssimpMaterial material, PoseStack stack,
                              VertexConsumer consumer, int light, int overlay) {
        float[] uv = mesh.getTexCoords();
        int[] indices = mesh.getIndices();
        float[] color = material == null ? new float[]{1, 1, 1, 1} : material.getDiffuseColor();
        int count = vertices.length / 3;
        for (int i = 0; i + 2 < indices.length; i += 3) {
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
