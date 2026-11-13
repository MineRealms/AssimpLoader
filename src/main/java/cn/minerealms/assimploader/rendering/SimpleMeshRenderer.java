package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Map;

/** Compatibility entry point for Minecraft's batched vertex path. */
public final class SimpleMeshRenderer {
    private static final MeshDelegate RENDERER = new MeshDelegate();
    private SimpleMeshRenderer() {}

    public static void render(AssimpMesh mesh, PoseStack poses, MultiBufferSource buffers,
                              ResourceLocation texture, int light, int overlay) {
        render(mesh, poses, buffers, texture, light, overlay, null, null);
    }

    public static void render(AssimpMesh mesh, PoseStack poses, MultiBufferSource buffers,
                              ResourceLocation texture, int light, int overlay, AssimpScene scene,
                              Map<String, Matrix4f> localOverrides) {
        AssimpMaterial material = scene == null ? null : ModelTextureCache.material(scene, mesh);
        if (scene != null && scene.isDynamic()) {
            for (var instance : SceneTransforms.instances(scene, localOverrides)) {
                if (instance.mesh() != mesh) continue;
                poses.pushPose();
                try {
                    SceneTransforms.apply(poses, instance.transform());
                    RENDERER.draw(mesh, material, poses, buffers, texture, light, overlay);
                } finally {
                    poses.popPose();
                }
            }
        } else {
            RENDERER.draw(mesh, material, poses, buffers, texture, light, overlay);
        }
    }

    private static final class MeshDelegate extends BaseAssimpRenderer {
        @Override protected String getModelName() { return "mesh"; }
        void draw(AssimpMesh mesh, AssimpMaterial material, PoseStack poses, MultiBufferSource buffers,
                   ResourceLocation texture, int light, int overlay) {
            renderMesh(mesh, material, poses, buffers.getBuffer(getRenderType(material, texture)), light, overlay);
        }
    }
}
