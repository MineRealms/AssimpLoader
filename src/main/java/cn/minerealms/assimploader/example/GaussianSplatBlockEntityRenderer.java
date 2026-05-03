package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.render.FrustumCuller;
import cn.minerealms.assimploader.rendering.splat.GaussianSplatRenderer;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;

/**
 * Renderer for GaussianSplatBlockEntity.
 *
 * Renders 3D Gaussian Splatting models in the Minecraft world.
 */
public class GaussianSplatBlockEntityRenderer implements BlockEntityRenderer<GaussianSplatBlockEntity> {

    private GaussianSplatRenderer splatRenderer;
    private boolean rendererInitialized = false;

    public GaussianSplatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GaussianSplatBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Initialize renderer if needed
        if (!rendererInitialized) {
            splatRenderer = new GaussianSplatRenderer();
            try {
                splatRenderer.initialize();
                rendererInitialized = true;
                cn.minerealms.assimploader.debug.DebugManager.info(
                    cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                    "GaussianSplatBlockEntityRenderer: Renderer initialized"
                );
            } catch (IOException e) {
                cn.minerealms.assimploader.debug.DebugManager.error(
                    cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                    "GaussianSplatBlockEntityRenderer: Failed to initialize renderer: {}", e.getMessage()
                );
                return;
            }
        }

        // Get splat data
        GaussianSplatData splatData = blockEntity.getSplatData();
        if (splatData == null || !blockEntity.isInitialized()) {
            return;
        }

        // Frustum culling: check if splat is visible using dynamic bounding box
        AABB boundingBox = getRenderBoundingBox(blockEntity, splatData);
        if (boundingBox != null && !FrustumCuller.getInstance().isVisible(boundingBox)) {
            return; // Skip rendering if outside view frustum
        }

        poseStack.pushPose();

        // Center the model in the block
        poseStack.translate(0.5, 0.5, 0.5);

        // Get camera position
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 blockPos = new Vec3(
            blockEntity.getBlockPos().getX() + 0.5,
            blockEntity.getBlockPos().getY() + 0.5,
            blockEntity.getBlockPos().getZ() + 0.5
        );

        // Calculate view and projection matrices
        Matrix4f viewMatrix = new Matrix4f(poseStack.last().pose());
        Matrix4f projectionMatrix = new Matrix4f(); // TODO: Get actual projection matrix from Minecraft

        // Calculate camera position relative to block
        Vector3f relativeCameraPos = new Vector3f(
            (float) (cameraPos.x - blockPos.x),
            (float) (cameraPos.y - blockPos.y),
            (float) (cameraPos.z - blockPos.z)
        );

        // Calculate FOV (default Minecraft FOV is 70 degrees)
        float fov = (float) Math.toRadians(70.0);
        float fovX = fov;
        float fovY = fov * 9.0f / 16.0f; // Aspect ratio 16:9
        float focal = 1.0f / (float) Math.tan(fov / 2.0f);

        // Render splats
        try {
            splatRenderer.render(
                splatData,
                viewMatrix,
                projectionMatrix,
                relativeCameraPos,
                fovX, fovY, focal,
                1.0f // scale modifier
            );
        } catch (Exception e) {
            cn.minerealms.assimploader.debug.DebugManager.error(
                cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                "GaussianSplatBlockEntityRenderer: Render failed: {}", e.getMessage()
            );
        }

        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 256; // Render distance for splats
    }

    /**
     * Calculate dynamic bounding box from splat data.
     * Uses the pre-computed bounds from GaussianSplatData.
     */
    private AABB getRenderBoundingBox(GaussianSplatBlockEntity blockEntity, GaussianSplatData splatData) {
        GaussianSplatData.BoundingBox bounds = splatData.getBounds();
        if (bounds == null) {
            return null; // No culling if bounds unavailable
        }

        Vector3f min = bounds.getMin();
        Vector3f max = bounds.getMax();
        BlockPos pos = blockEntity.getBlockPos();

        // Transform bounds to world space (centered at block position + 0.5 offset)
        return new AABB(
            pos.getX() + 0.5 + min.x,
            pos.getY() + 0.5 + min.y,
            pos.getZ() + 0.5 + min.z,
            pos.getX() + 0.5 + max.x,
            pos.getY() + 0.5 + max.y,
            pos.getZ() + 0.5 + max.z
        );
    }
}
