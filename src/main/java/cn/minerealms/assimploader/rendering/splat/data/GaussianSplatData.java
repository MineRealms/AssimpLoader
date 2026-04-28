package cn.minerealms.assimploader.rendering.splat.data;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container for Gaussian splat point cloud data.
 *
 * Holds a collection of splats with metadata like SH degree and bounding box.
 */
public class GaussianSplatData {

    private final List<GaussianSplat> splats;
    private final int shDegree;
    private final BoundingBox bounds;

    public GaussianSplatData(List<GaussianSplat> splats, int shDegree) {
        this.splats = new ArrayList<>(splats);
        this.shDegree = shDegree;
        this.bounds = computeBounds(splats);
    }

    public List<GaussianSplat> getSplats() {
        return Collections.unmodifiableList(splats);
    }

    public int getSplatCount() {
        return splats.size();
    }

    public int getShDegree() {
        return shDegree;
    }

    public int getShDim() {
        return (shDegree + 1) * (shDegree + 1);
    }

    public BoundingBox getBounds() {
        return bounds;
    }

    private static BoundingBox computeBounds(List<GaussianSplat> splats) {
        if (splats.isEmpty()) {
            return new BoundingBox(new Vector3f(), new Vector3f());
        }

        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

        for (GaussianSplat splat : splats) {
            Vector3f pos = splat.getPosition();
            min.min(pos);
            max.max(pos);
        }

        return new BoundingBox(min, max);
    }

    public static class BoundingBox {
        private final Vector3f min;
        private final Vector3f max;

        public BoundingBox(Vector3f min, Vector3f max) {
            this.min = new Vector3f(min);
            this.max = new Vector3f(max);
        }

        public Vector3f getMin() {
            return new Vector3f(min);
        }

        public Vector3f getMax() {
            return new Vector3f(max);
        }

        public Vector3f getCenter() {
            return new Vector3f(min).add(max).mul(0.5f);
        }

        public Vector3f getSize() {
            return new Vector3f(max).sub(min);
        }
    }
}
