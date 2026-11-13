package cn.minerealms.assimploader.rendering.splat.data;

import org.joml.Vector3f;

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
        if (shDegree < 0 || shDegree > 3) throw new IllegalArgumentException("SH degree must be 0-3");
        this.splats = List.copyOf(splats);
        this.shDegree = shDegree;
        this.bounds = computeBounds(this.splats);
    }

    public List<GaussianSplat> getSplats() {
        return splats;
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
            return new Vector3f((float) (((double) min.x + max.x) * 0.5),
                    (float) (((double) min.y + max.y) * 0.5), (float) (((double) min.z + max.z) * 0.5));
        }

        public Vector3f getSize() {
            return new Vector3f((float) Math.min(Float.MAX_VALUE, (double) max.x - min.x),
                    (float) Math.min(Float.MAX_VALUE, (double) max.y - min.y),
                    (float) Math.min(Float.MAX_VALUE, (double) max.z - min.z));
        }
    }
}
