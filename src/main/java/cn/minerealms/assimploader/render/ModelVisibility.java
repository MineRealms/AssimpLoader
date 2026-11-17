package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounds and visibility math shared with the common-side block entity; no client or GL types. */
public final class ModelVisibility {
    private static final AABB FALLBACK = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);
    private static final Map<AssimpScene, AABB> BOUNDS = Collections.synchronizedMap(new WeakHashMap<>());

    private ModelVisibility() {}

    public static AABB modelBounds(AssimpScene scene) {
        return scene == null ? FALLBACK : BOUNDS.computeIfAbsent(scene, ModelVisibility::calculate);
    }

    public static AABB worldBounds(AABB modelBounds, BlockPos position) {
        return modelBounds.move(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
    }

    public static AABB renderBounds(AABB modelBounds, BlockPos position, boolean frustumCulling,
                                    boolean alwaysRenderLargeModels, double threshold) {
        return new RenderBounds(worldBounds(modelBounds, position).inflate(0.25),
                !frustumCulling || bypassCulling(modelBounds, alwaysRenderLargeModels, threshold));
    }

    /** Only our marked, finite boxes can opt out of the engine's per-entity frustum check. */
    public static boolean bypassFrustum(AABB bounds) {
        return bounds instanceof RenderBounds rendered && rendered.bypassFrustum;
    }

    public static boolean bypassCulling(AABB bounds, boolean enabled, double threshold) {
        return enabled && Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) >= threshold;
    }

    public static boolean withinDistance(AABB bounds, Vec3 camera, double distance) {
        return distanceSquared(bounds,camera) <= distance * distance;
    }

    public static double distanceSquared(AABB bounds, Vec3 camera) {
        double dx = Math.max(Math.max(bounds.minX - camera.x, camera.x - bounds.maxX), 0);
        double dy = Math.max(Math.max(bounds.minY - camera.y, camera.y - bounds.maxY), 0);
        double dz = Math.max(Math.max(bounds.minZ - camera.z, camera.z - bounds.maxZ), 0);
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class RenderBounds extends AABB {
        private final boolean bypassFrustum;

        private RenderBounds(AABB bounds, boolean bypassFrustum) {
            super(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
            this.bypassFrustum = bypassFrustum;
        }
    }

    private static AABB calculate(AssimpScene scene) {
        BoundsBuilder bounds = new BoundsBuilder();
        if (scene.isDynamic() && scene.getRootNode() != null) {
            collect(scene, scene.getRootNode(), new Matrix4f(), bounds);
        } else {
            Matrix4f identity = new Matrix4f();
            for (AssimpMesh mesh : scene.getMeshes()) bounds.add(mesh, identity);
        }
        return bounds.box();
    }

    private static void collect(AssimpScene scene, AssimpNode node, Matrix4f parent, BoundsBuilder bounds) {
        Matrix4f transform = new Matrix4f(parent).mul(node.getTransformation());
        for (int index : node.getMeshIndices()) {
            if (index >= 0 && index < scene.getMeshCount()) bounds.add(scene.getMeshes().get(index), transform);
        }
        for (AssimpNode child : node.getChildren()) collect(scene, child, transform, bounds);
    }

    private static final class BoundsBuilder {
        private double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        private double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        private final Vector3f point = new Vector3f();

        void add(AssimpMesh mesh, Matrix4f transform) {
            float[] vertices = mesh.getVertices();
            for (int index = 0; index + 2 < vertices.length; index += 3) {
                transform.transformPosition(point.set(vertices[index], vertices[index + 1], vertices[index + 2]));
                if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z)) continue;
                minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
            }
        }

        AABB box() {
            return Double.isFinite(minX) ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : FALLBACK;
        }
    }
}
