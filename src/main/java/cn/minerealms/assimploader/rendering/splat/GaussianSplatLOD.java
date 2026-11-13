package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import net.minecraftforge.common.ForgeConfigSpec;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Selects original point indices; it never changes the packed GPU cloud. */
public final class GaussianSplatLOD {
    private GaussianSplatLOD() {}

    public record Settings(boolean enabled, float high, float medium, float low, float cull,
                           int highFactor, int mediumFactor, int lowFactor) {
        public Settings {
            if (!Float.isFinite(high) || !Float.isFinite(medium) || !Float.isFinite(low) || !Float.isFinite(cull)) {
                throw new IllegalArgumentException("LOD distances must be finite");
            }
            high = Math.max(0, high);
            medium = Math.max(high, medium);
            low = Math.max(medium, low);
            cull = Math.max(low, cull);
            highFactor = Math.max(1, highFactor);
            mediumFactor = Math.max(1, mediumFactor);
            lowFactor = Math.max(1, lowFactor);
        }

        public static Settings configured() {
            var config = AssimpLoaderConfig.CLIENT;
            return new Settings(value(config.splatLodEnabled), value(config.splatLodDistanceHigh).floatValue(),
                    value(config.splatLodDistanceMedium).floatValue(), value(config.splatLodDistanceLow).floatValue(),
                    value(config.splatLodDistanceCull).floatValue(), value(config.splatLodFactorHigh),
                    value(config.splatLodFactorMedium), value(config.splatLodFactorLow));
        }

        public int factor(float distance) {
            if (!enabled) return 1;
            if (!Float.isFinite(distance)) return 0;
            if (distance >= cull) return 0;
            if (distance < high) return highFactor;
            if (distance < medium) return mediumFactor;
            if (distance < low) return lowFactor;
            return lowFactor >= Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : lowFactor * 2;
        }
    }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> config) {
        try { return config.get(); }
        catch (IllegalStateException unloaded) { return config.getDefault(); }
    }

    public static int selectIndices(GaussianSplatData data, Vector3f camera, int[] target, Settings settings) {
        if (target.length < data.getSplatCount()) throw new IllegalArgumentException("LOD target is too small");
        if (!camera.isFinite()) return 0;
        int factor = settings.factor(data.getBounds().getCenter().distance(camera));
        if (factor == 0) return 0;
        int count = 0;
        for (int index = 0; index < data.getSplatCount(); index += factor) {
            target[count++] = index;
            if (index > Integer.MAX_VALUE - factor) break;
        }
        return count;
    }

    public static GaussianSplatData applyLOD(GaussianSplatData data, Vector3f cameraPos) {
        if (data == null || data.getSplatCount() == 0) return data;
        int[] indices = new int[data.getSplatCount()];
        int count = selectIndices(data, cameraPos, indices, Settings.configured());
        if (count == data.getSplatCount()) return data;
        List<GaussianSplat> selected = new ArrayList<>(count);
        for (int i = 0; i < count; i++) selected.add(data.getSplats().get(indices[i]));
        return new GaussianSplatData(selected, data.getShDegree());
    }

    public static GaussianSplatData applyLODWithFrustum(GaussianSplatData data, Vector3f cameraPos, Frustum frustum) {
        if (data == null || data.getSplatCount() == 0) return data;
        Settings settings = Settings.configured();
        List<GaussianSplat> selected = new ArrayList<>();
        Vector3f position = new Vector3f();
        for (int i = 0; i < data.getSplatCount(); i++) {
            GaussianSplat splat = data.getSplats().get(i);
            splat.getPosition(position);
            int factor = settings.factor(position.distance(cameraPos));
            if (factor > 0 && i % factor == 0 && frustum.contains(position)) selected.add(splat);
        }
        return new GaussianSplatData(selected, data.getShDegree());
    }

    public static final class Frustum {
        private final Vector3f cameraPos;
        private final Vector3f forward;
        private final float cosine;
        private final float near;
        private final float far;

        public Frustum(Vector3f cameraPos, Vector3f forward, float fov, float near, float far) {
            if (!cameraPos.isFinite() || !forward.isFinite() || forward.lengthSquared() < 1e-12f
                    || !Float.isFinite(fov) || !Float.isFinite(near) || !Float.isFinite(far)
                    || fov <= 0 || fov >= Math.PI || near < 0 || far < near) {
                throw new IllegalArgumentException("Invalid Gaussian frustum");
            }
            this.cameraPos = new Vector3f(cameraPos);
            this.forward = new Vector3f(forward).normalize();
            cosine = (float) Math.cos(fov / 2);
            this.near = near;
            this.far = far;
        }

        public boolean contains(Vector3f point) {
            float x = point.x - cameraPos.x, y = point.y - cameraPos.y, z = point.z - cameraPos.z;
            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            if (!Float.isFinite(distance) || distance < near || distance > far) return false;
            return distance == 0 || (x * forward.x + y * forward.y + z * forward.z) / distance >= cosine;
        }
    }
}
