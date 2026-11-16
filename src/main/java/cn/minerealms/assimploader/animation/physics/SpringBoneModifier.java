package cn.minerealms.assimploader.animation.physics;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.collision.CapsuleCollider;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Model-space secondary motion. Work per update is capped; excess hitch time is discarded. */
public final class SpringBoneModifier implements PoseModifier {
    public record Settings(String node, Vector3f tail, float stiffness, float damping, Vector3f gravity,
                           float radius, double fixedStep, int maxSubsteps, float teleportDistance) {
        public Settings {
            if (node == null || node.isBlank()) throw new IllegalArgumentException("Missing spring node");
            tail = finite(tail); gravity = finite(gravity);
            if (!Float.isFinite(tail.lengthSquared()) || tail.lengthSquared() < 1.0e-12f || !nonnegative(stiffness) || !nonnegative(damping)
                    || !nonnegative(radius) || !Double.isFinite(fixedStep) || fixedStep < 1.0e-6 || fixedStep > 0.1
                    || stiffness * fixedStep * fixedStep > 1
                    || maxSubsteps < 1 || maxSubsteps > 128 || !Float.isFinite(teleportDistance) || teleportDistance <= 0)
                throw new IllegalArgumentException("Invalid spring settings");
        }
        @Override public Vector3f tail() { return new Vector3f(tail); }
        @Override public Vector3f gravity() { return new Vector3f(gravity); }
    }

    /** Null node means model space; otherwise points are relative to that node's current pose. */
    public sealed interface Collider permits Sphere, Capsule {
        CapsuleCollider at(PoseGraph graph);
    }

    public record Sphere(String node, Vector3f center, float radius) implements Collider {
        public Sphere { center = finite(center); positive(radius); }
        @Override public Vector3f center() { return new Vector3f(center); }
        @Override public CapsuleCollider at(PoseGraph graph) {
            Matrix4f transform = node == null ? new Matrix4f() : graph.global(node);
            Vector3f point = transform.transformPosition(new Vector3f(center));
            return new CapsuleCollider(point, point, radius * conservativeScale(transform));
        }
    }

    public record Capsule(String node, Vector3f start, Vector3f end, float radius) implements Collider {
        public Capsule { start = finite(start); end = finite(end); positive(radius); }
        @Override public Vector3f start() { return new Vector3f(start); }
        @Override public Vector3f end() { return new Vector3f(end); }
        @Override public CapsuleCollider at(PoseGraph graph) {
            Matrix4f transform = node == null ? new Matrix4f() : graph.global(node);
            return new CapsuleCollider(transform.transformPosition(new Vector3f(start)), transform.transformPosition(new Vector3f(end)),
                    radius * conservativeScale(transform));
        }
    }

    private final Settings settings;
    private final List<Collider> colliders;
    private final Vector3f position = new Vector3f(), velocity = new Vector3f(), previousOrigin = new Vector3f();
    private boolean initialized;
    private double accumulator;
    private int lastSubsteps;

    public SpringBoneModifier(Settings settings, List<? extends Collider> colliders) {
        this.settings = Objects.requireNonNull(settings);
        this.colliders = List.copyOf(colliders);
    }

    public SpringBoneModifier(Settings settings) { this(settings, List.of()); }
    public Settings settings() { return settings; }
    public Vector3f getTailPosition() { return initialized ? new Vector3f(position) : null; }
    public int getLastSubsteps() { return lastSubsteps; }

    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) throw new IllegalArgumentException("Invalid spring delta");
        PoseGraph graph = new PoseGraph(scene.getRootNode(), locals);
        Matrix4f transform = graph.global(settings.node());
        Vector3f origin = transform.getTranslation(new Vector3f());
        Vector3f target = transform.transformPosition(settings.tail());
        Vector3f restDirection = new Vector3f(target).sub(origin);
        float length = restDirection.length();
        if (!Float.isFinite(length) || length < 1.0e-8f) throw new IllegalArgumentException("Collapsed spring transform");
        if (!initialized || origin.distanceSquared(previousOrigin) > settings.teleportDistance() * settings.teleportDistance()) {
            position.set(target); velocity.zero(); accumulator = 0; initialized = true;
        }
        previousOrigin.set(origin);
        List<CapsuleCollider> volumes = colliders.stream().map(value -> value.at(graph)).toList();
        double step = settings.fixedStep();
        accumulator = Math.min(accumulator + deltaSeconds, step * settings.maxSubsteps());
        lastSubsteps = 0;
        while (accumulator + step * 1.0e-7 >= step && lastSubsteps < settings.maxSubsteps()) {
            integrate(origin, target, restDirection, length, volumes, (float) step);
            accumulator = Math.max(0, accumulator - step);
            lastSubsteps++;
        }
        Vector3f direction = new Vector3f(position).sub(origin);
        if (direction.lengthSquared() > 1.0e-12f) {
            Quaternionf rotation = new Quaternionf().rotationTo(restDirection.normalize(), direction.normalize());
            graph.rotateModel(settings.node(), rotation);
        }
    }

    private void integrate(Vector3f origin, Vector3f target, Vector3f restDirection, float length,
                           List<CapsuleCollider> volumes, float step) {
        Vector3f previous = new Vector3f(position);
        Vector3f acceleration = new Vector3f(target).sub(position).mul(settings.stiffness()).add(settings.gravity());
        velocity.fma(step, acceleration).mul((float) Math.exp(-settings.damping() * step));
        position.fma(step, velocity);
        // Alternating projections handle particle contacts while preserving the bone length.
        for (int pass = 0; pass < 4; pass++) {
            Vector3f direction = new Vector3f(position).sub(origin);
            if (direction.lengthSquared() < 1.0e-12f) direction.set(restDirection);
            position.set(direction.normalize(length).add(origin));
            for (CapsuleCollider collider : volumes) position.set(collider.project(position, settings.radius(), restDirection));
        }
        Vector3f direction = new Vector3f(position).sub(origin);
        if (direction.lengthSquared() < 1.0e-12f) direction.set(restDirection);
        position.set(direction.normalize(length).add(origin));
        velocity.set(position).sub(previous).div(step);
        if (!position.isFinite() || !velocity.isFinite()) { position.set(target); velocity.zero(); }
    }

    @Override public void reset() {
        initialized = false; accumulator = 0; lastSubsteps = 0; velocity.zero();
    }

    private static Vector3f finite(Vector3fc value) {
        if (value == null || !Float.isFinite(value.x()) || !Float.isFinite(value.y()) || !Float.isFinite(value.z()))
            throw new IllegalArgumentException("Non-finite spring vector");
        return new Vector3f(value);
    }
    private static boolean nonnegative(float value) { return Float.isFinite(value) && value >= 0; }
    private static void positive(float value) { if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid collider radius"); }

    /** sqrt(norm1 * normInfinity) bounds maximum scale, and is exact for axis-aligned TRS. */
    public static float conservativeScale(Matrix4f matrix) {
        float one = Math.max(Math.abs(matrix.m00()) + Math.abs(matrix.m01()) + Math.abs(matrix.m02()),
                Math.max(Math.abs(matrix.m10()) + Math.abs(matrix.m11()) + Math.abs(matrix.m12()),
                        Math.abs(matrix.m20()) + Math.abs(matrix.m21()) + Math.abs(matrix.m22())));
        float infinity = Math.max(Math.abs(matrix.m00()) + Math.abs(matrix.m10()) + Math.abs(matrix.m20()),
                Math.max(Math.abs(matrix.m01()) + Math.abs(matrix.m11()) + Math.abs(matrix.m21()),
                        Math.abs(matrix.m02()) + Math.abs(matrix.m12()) + Math.abs(matrix.m22())));
        float scale = (float) Math.sqrt(one * infinity);
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Collapsed collider transform");
        return scale;
    }
}
