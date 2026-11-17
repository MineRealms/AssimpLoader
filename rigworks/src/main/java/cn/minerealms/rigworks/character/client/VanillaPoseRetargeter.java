package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.AnimationBlender;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.VanillaPoseSelection;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/** Retargets independent vanilla part rotations onto a hierarchical rig without changing limb lengths. */
final class VanillaPoseRetargeter implements PoseModifier {
    record Part(float pitch, float yaw, float roll, float x, float y, float z) {}
    private record Bone(String node, Matrix4f local, Quaternionf global, Quaternionf correction, Vector3f origin,
                        Matrix4f parentInverse, Vector3f scale) {}
    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final Quaternionf axes;
    private final Vector3f pixelScale;
    private final Vector3f torsoPivot;
    private Matrix4f root = new Matrix4f();
    private Map<String, Part> parts = Map.of();
    private boolean enabled;
    private VanillaPoseSelection selection = VanillaPoseSelection.ALL;

    VanillaPoseRetargeter(CharacterProfile profile, AssimpScene scene) {
        var nativeLocals = new LinkedHashMap<>(SkinBindPose.locals(scene));
        profile.humanoid().values().forEach(value -> {
            Matrix4f local = nativeLocals.get(value.node());
            if (local != null) nativeLocals.put(value.node(), calibratedLocal(local, value.nativeRotation()));
        });
        var bind = new PoseGraph(scene.getRootNode(), nativeLocals);
        profile.humanoid().forEach((role, value) -> {
            if (bind.contains(value.node())) {
                Quaternionf global = bind.global(value.node()).getUnnormalizedRotation(new Quaternionf()).normalize();
                Quaternionf correction = new Quaternionf(global).mul(value.referenceRotation().quaternion())
                        .mul(new Quaternionf(global).invert()).normalize();
                Matrix4f local = bind.local(value.node()), model = bind.global(value.node());
                Matrix4f parentInverse = new Matrix4f(model).mul(new Matrix4f(local).invert()).invert();
                bones.put(role, new Bone(value.node(), local, global, correction, bind.position(value.node()),
                        parentInverse, model.getScale(new Vector3f())));
            }
        });
        axes = new Quaternionf().rotationY((float)Math.toRadians(-profile.modelYawDegrees())).rotateX((float)Math.PI);
        var head = bones.get("head"); var hips = bones.get("hips");
        float vertical = head == null || hips == null ? 1f / 16 : head.origin.distance(hips.origin) / 12;
        var left = bones.get("left_upper_arm"); var right = bones.get("right_upper_arm");
        float horizontal = left == null || right == null ? vertical : left.origin.distance(right.origin) / 10;
        pixelScale = new Vector3f(horizontal, vertical, vertical);
        torsoPivot = head == null ? new Vector3f() : new Vector3f(head.origin);
    }

    static Matrix4f calibratedLocal(Matrix4f local, CharacterProfile.Rotation correction) {
        if (correction.equals(CharacterProfile.Rotation.IDENTITY)) return new Matrix4f(local);
        Vector3f scale = local.getScale(new Vector3f());
        if (Math.min(scale.x, Math.min(scale.y, scale.z)) < 1e-8f)
            throw new IllegalArgumentException("Native calibration requires a nonsingular bind scale");
        Matrix3f rotation = new Matrix3f(local);
        if (rotation.determinant() < 0) scale.x = -scale.x;
        rotation.scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        Quaternionf orientation = rotation.getNormalizedRotation(new Quaternionf()).normalize().mul(correction.quaternion());
        return new Matrix4f().translationRotateScale(local.getTranslation(new Vector3f()), orientation, scale);
    }

    void update(Matrix4f root, Map<String, Part> parts) {
        update(root, parts, VanillaPoseSelection.ALL);
    }

    void update(Matrix4f root, Map<String, Part> parts, VanillaPoseSelection selection) {
        if (!root.isFinite()) throw new IllegalArgumentException("Non-finite vanilla root transform");
        this.root = new Matrix4f(root);
        this.parts = Map.copyOf(parts);
        this.selection = java.util.Objects.requireNonNull(selection);
        enabled = selection.enabled();
    }
    void disable() { enabled = false; }
    @Override public Category category() { return Category.MOTION; }
    @Override public void reset() { disable(); }

    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        if (!enabled) return;
        // Evaluate an independent native pose before touching the incoming clip locals.
        var candidate = new LinkedHashMap<String, Matrix4f>(locals);
        bones.values().forEach(bone -> candidate.put(bone.node, new Matrix4f(bone.local)));
        var graph = new PoseGraph(scene.getRootNode(), candidate);
        for (String role : new String[]{"hips", "head", "left_upper_arm", "right_upper_arm", "left_thigh", "right_thigh"}) {
            Bone bone = bones.get(role);
            Part part = parts.get(role);
            if (bone == null || part == null) continue;
            Matrix4f global = graph.global(bone.node);
            Quaternionf partRotation = rotationOf(part, bone);
            Quaternionf rotation = new Quaternionf(partRotation).mul(bone.global).normalize();
            Vector3f position = new Vector3f(bone.origin);
            if (role.equals("hips")) partRotation.transform(position.sub(torsoPivot)).add(torsoPivot);
            position.add(axes.transform(new Vector3f(part.x, part.y, part.z).mul(pixelScale)));
            Vector3f scale = global.getScale(new Vector3f());
            graph.setGlobal(bone.node, new Matrix4f().translationRotateScale(position, rotation, scale));
        }
        for (String role : new String[]{"left_shin", "right_shin", "left_forearm", "right_forearm"}) {
            Bone bone = bones.get(role);
            Part part = parts.get(role);
            // Knees are articulated children: preserve their pivot and follow the posed thigh/helper frame.
            if (bone != null && part != null) graph.setLocal(bone.node, relativeLocal(bone, part, bone.local));
        }
        for (var entry : bones.entrySet()) {
            float weight = selection.weight(entry.getKey());
            if (weight == 0) continue;
            Bone bone = entry.getValue();
            Matrix4f nativeLocal = candidate.get(bone.node);
            Matrix4f incoming = locals.getOrDefault(bone.node, bone.local);
            Part part = parts.get(entry.getKey());
            if (selection.body() < 1 && !entry.getKey().equals("hips") && part != null) {
                // A local overlay follows the authored shoulder/neck/hip frame, including helper bones.
                Matrix4f relative = relativeLocal(bone, part, incoming);
                nativeLocal = selection.body() == 0 ? relative
                        : AnimationBlender.blendTransforms(relative, nativeLocal, selection.body());
            }
            locals.put(bone.node, weight == 1 ? new Matrix4f(nativeLocal) : AnimationBlender.blendTransforms(incoming, nativeLocal, weight));
        }
        // Renderer orientation is applied once to the composed skeleton, including unselected limbs.
        Bone hips = bones.get("hips");
        if (hips != null && selection.root() > 0) {
            var composed = new PoseGraph(scene.getRootNode(), locals);
            Matrix4f rootBlend = selection.root() == 1 ? root : AnimationBlender.blendTransforms(new Matrix4f(), root, selection.root());
            composed.setGlobal(hips.node, new Matrix4f(rootBlend).mul(composed.global(hips.node)));
        }
    }

    private Matrix4f relativeLocal(Bone bone, Part part, Matrix4f incoming) {
        Vector3f offset = axes.transform(new Vector3f(part.x, part.y, part.z).mul(pixelScale));
        Matrix4f relative = part.pitch == 0 && part.yaw == 0 && part.roll == 0 ? new Matrix4f(bone.local)
                : new Matrix4f(bone.parentInverse).mul(new Matrix4f().translationRotateScale(
                        new Vector3f(bone.origin), rotationOf(part, bone).mul(bone.global), bone.scale));
        bone.parentInverse.transformDirection(offset);
        return relative.setTranslation(incoming.m30() + offset.x, incoming.m31() + offset.y, incoming.m32() + offset.z);
    }

    private Quaternionf rotationOf(Part part, Bone bone) {
        return new Quaternionf(bone.correction).mul(axes)
                .mul(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch))
                .mul(new Quaternionf(axes).invert()).mul(new Quaternionf(bone.correction).invert()).normalize();
    }
}
