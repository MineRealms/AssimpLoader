package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Level-of-detail system for Gaussian splats.
 *
 * Reduces splat count based on distance from camera to improve performance
 * for large point clouds.
 */
public class GaussianSplatLOD {

    // LOD distance thresholds (in blocks)
    private static final float LOD_DISTANCE_HIGH = 16.0f;    // Full detail
    private static final float LOD_DISTANCE_MEDIUM = 32.0f;  // 50% splats
    private static final float LOD_DISTANCE_LOW = 64.0f;     // 25% splats
    private static final float LOD_DISTANCE_CULL = 128.0f;   // Cull completely

    // LOD reduction factors
    private static final int LOD_FACTOR_HIGH = 1;    // Every splat
    private static final int LOD_FACTOR_MEDIUM = 2;  // Every 2nd splat
    private static final int LOD_FACTOR_LOW = 4;     // Every 4th splat

    /**
     * Apply LOD filtering to splat data based on camera distance
     *
     * @param data Original splat data
     * @param cameraPos Camera position
     * @return Filtered splat data with LOD applied
     */
    public static GaussianSplatData applyLOD(GaussianSplatData data, Vector3f cameraPos) {
        if (data == null || data.getSplatCount() == 0) {
            return data;
        }

        // Calculate center of splat cloud
        Vector3f center = calculateCenter(data);
        float distance = center.distance(cameraPos);

        // Determine LOD level
        int lodFactor = getLODFactor(distance);

        // If full detail or culled, return as-is
        if (lodFactor == LOD_FACTOR_HIGH) {
            return data; // Full detail
        }
        if (distance > LOD_DISTANCE_CULL) {
            return new GaussianSplatData(new ArrayList<>(), data.getShDegree()); // Empty (culled)
        }

        // Apply LOD filtering
        return filterSplats(data, lodFactor);
    }

    /**
     * Apply LOD filtering with frustum culling
     *
     * @param data Original splat data
     * @param cameraPos Camera position
     * @param frustum View frustum for culling
     * @return Filtered splat data
     */
    public static GaussianSplatData applyLODWithFrustum(GaussianSplatData data,
                                                         Vector3f cameraPos,
                                                         Frustum frustum) {
        if (data == null || data.getSplatCount() == 0) {
            return data;
        }

        List<GaussianSplat> filteredSplats = new ArrayList<>();
        List<GaussianSplat> splats = data.getSplats();

        for (int i = 0; i < splats.size(); i++) {
            GaussianSplat splat = splats.get(i);
            Vector3f pos = splat.getPosition();

            // Frustum culling
            if (!frustum.contains(pos)) {
                continue;
            }

            // Distance-based LOD
            float distance = pos.distance(cameraPos);
            int lodFactor = getLODFactor(distance);

            if (distance > LOD_DISTANCE_CULL) {
                continue; // Cull
            }

            // Apply LOD sampling
            if (i % lodFactor == 0) {
                filteredSplats.add(splat);
            }
        }

        return new GaussianSplatData(filteredSplats, data.getShDegree());
    }

    /**
     * Get LOD factor based on distance
     */
    private static int getLODFactor(float distance) {
        if (distance < LOD_DISTANCE_HIGH) {
            return LOD_FACTOR_HIGH;
        } else if (distance < LOD_DISTANCE_MEDIUM) {
            return LOD_FACTOR_MEDIUM;
        } else if (distance < LOD_DISTANCE_LOW) {
            return LOD_FACTOR_LOW;
        } else {
            return Integer.MAX_VALUE; // Cull
        }
    }

    /**
     * Filter splats by LOD factor
     */
    private static GaussianSplatData filterSplats(GaussianSplatData data, int lodFactor) {
        List<GaussianSplat> filteredSplats = new ArrayList<>();
        List<GaussianSplat> splats = data.getSplats();

        for (int i = 0; i < splats.size(); i++) {
            if (i % lodFactor == 0) {
                filteredSplats.add(splats.get(i));
            }
        }

        return new GaussianSplatData(filteredSplats, data.getShDegree());
    }

    /**
     * Calculate center of splat cloud
     */
    private static Vector3f calculateCenter(GaussianSplatData data) {
        Vector3f sum = new Vector3f(0, 0, 0);
        List<GaussianSplat> splats = data.getSplats();

        for (GaussianSplat splat : splats) {
            sum.add(splat.getPosition());
        }

        return sum.div(splats.size());
    }

    /**
     * Simple frustum for culling
     */
    public static class Frustum {
        private final Vector3f cameraPos;
        private final Vector3f forward;
        private final float fov;
        private final float near;
        private final float far;

        public Frustum(Vector3f cameraPos, Vector3f forward, float fov, float near, float far) {
            this.cameraPos = cameraPos;
            this.forward = forward;
            this.fov = fov;
            this.near = near;
            this.far = far;
        }

        /**
         * Check if point is inside frustum (simplified)
         */
        public boolean contains(Vector3f point) {
            Vector3f toPoint = new Vector3f(point).sub(cameraPos);
            float distance = toPoint.length();

            // Distance culling
            if (distance < near || distance > far) {
                return false;
            }

            // Angle culling (simplified cone test)
            float angle = toPoint.normalize().dot(forward);
            float threshold = (float) Math.cos(fov / 2.0f);

            return angle > threshold;
        }
    }
}
