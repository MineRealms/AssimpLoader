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
    @Override public Category category() { return Category.SPRING; }
    @Override public java.util.Set<String> controlledBones() { return java.util.Set.of(settings.node()); }
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
            Quaternionf rotation = shortestArc(restDirection, direction);
            graph.rotateModel(settings.node(), rotation);
        }
    }

    /** JOML 1.10.5 can return a nonunit rotation for nearly opposite directions. */
    private static Quaternionf shortestArc(Vector3fc from, Vector3fc to) {
        Quaternionf rotation = new Quaternionf().rotationTo(from, to);
        if (rotation.w >= .01f) return rotation;

        // The cross product still resolves the angle when the dot product rounds to -1.
        double fx = from.x(), fy = from.y(), fz = from.z(), tx = to.x(), ty = to.y(), tz = to.z();
        double x = fy * tz - fz * ty, y = fz * tx - fx * tz, z = fx * ty - fy * tx;
        double crossLength = Math.sqrt(x * x + y * y + z * z);
        if (crossLength == 0) {
            // Avoid a degenerate perpendicular axis for an exact half turn.
            return (Math.abs(fx) > Math.abs(fz) ? rotation.set(-from.y(), from.x(), 0, 0)
                    : rotation.set(0, -from.z(), from.y(), 0)).normalize();
        }
        double halfAngle = .5 * Math.atan2(crossLength, fx * tx + fy * ty + fz * tz);
        double scale = Math.sin(halfAngle) / crossLength;
        return rotation.set((float) (x * scale), (float) (y * scale), (float) (z * scale), (float) Math.cos(halfAngle));
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

    /** Bounds the maximum stretch, without inflating orthogonal TRS columns when they rotate. */
    public static float conservativeScale(Matrix4f matrix) {
        double a = matrix.m00(), b = matrix.m01(), c = matrix.m02();
        double d = matrix.m10(), e = matrix.m11(), f = matrix.m12();
        double g = matrix.m20(), h = matrix.m21(), i = matrix.m22();
        double one = Math.max(Math.abs(a) + Math.abs(b) + Math.abs(c),
                Math.max(Math.abs(d) + Math.abs(e) + Math.abs(f), Math.abs(g) + Math.abs(h) + Math.abs(i)));
        double infinity = Math.max(Math.abs(a) + Math.abs(d) + Math.abs(g),
                Math.max(Math.abs(b) + Math.abs(e) + Math.abs(h), Math.abs(c) + Math.abs(f) + Math.abs(i)));

        // The largest eigenvalue of A^T A is bounded by its largest absolute row sum.
        double ad = Math.abs(a * d + b * e + c * f);
        double ag = Math.abs(a * g + b * h + c * i);
        double dg = Math.abs(d * g + e * h + f * i);
        double gram = Math.max(a * a + b * b + c * c + ad + ag,
                Math.max(d * d + e * e + f * f + ad + dg, g * g + h * h + i * i + ag + dg));
        double bound = Math.sqrt(Math.min(one * infinity, gram));
        float scale = (float) bound;
        if (scale < bound) scale = Math.nextUp(scale);
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Collapsed collider transform");
        return scale;
    }
}
