package cn.minerealms.assimploader.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Frustum culling utility for callers with a camera-relative view-projection matrix.
 */
public class FrustumCuller {
    private static FrustumCuller instance;
    private Frustum frustum;
    private Matrix4f lastViewProjection;
    private boolean initialized = false;
    private double cameraX, cameraY, cameraZ;

    private FrustumCuller() {
        this.frustum = new Frustum(new Matrix4f(), new Matrix4f());
        this.lastViewProjection = new Matrix4f();
    }

    public static FrustumCuller getInstance() {
        if (instance == null) {
            instance = new FrustumCuller();
        }
        return instance;
    }

    /**
     * Update frustum with current view-projection matrix
     * Call this once per frame before rendering
     */
    public void updateFrustum(Matrix4f viewProjection) {
        if (!initialized || !lastViewProjection.equals(viewProjection)) {
            lastViewProjection.set(viewProjection);
            frustum = new Frustum(new Matrix4f(), viewProjection);
            frustum.prepare(cameraX, cameraY, cameraZ);
            initialized = true;
        }
    }

    /**
     * Update frustum with camera position
     * Call this once per frame before rendering
     */
    public void updateFrustum(double camX, double camY, double camZ) {
        cameraX = camX;
        cameraY = camY;
        cameraZ = camZ;
        frustum.prepare(camX, camY, camZ);
    }

    /**
     * Check if an AABB is visible in the current frustum
     *
     * @param aabb Axis-aligned bounding box to test
     * @return true if visible, false if culled
     */
    public boolean isVisible(AABB aabb) {
        if (!initialized) {
            return true; // If frustum not initialized, render everything
        }

        return frustum.isVisible(aabb);
    }

    /**
     * Check if a point with radius is visible
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param radius Bounding sphere radius
     * @return true if visible, false if culled
     */
    public boolean isVisible(double x, double y, double z, double radius) {
        if (!initialized) {
            return true;
        }

        AABB aabb = new AABB(
            x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius
        );

        return frustum.isVisible(aabb);
    }

    /**
     * Reset frustum (call on world change)
     */
    public void reset() {
        initialized = false;
        lastViewProjection.identity();
        cameraX = cameraY = cameraZ = 0;
    }
}
