package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.AnimationBlender;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.VanillaPoseSelection;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/** Retargets independent vanilla part rotations onto a hierarchical rig without changing limb lengths. */
final class VanillaPoseRetargeter implements PoseModifier {
    record Part(float pitch, float yaw, float roll, float x, float y, float z) {}
    private record Bone(String node, Matrix4f local, Quaternionf global, Quaternionf correction, Vector3f origin) {}
    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final Quaternionf axes;
    private final Vector3f pixelScale;
    private final Vector3f torsoPivot;
    private Matrix4f root = new Matrix4f();
    private Map<String, Part> parts = Map.of();
    private boolean enabled;
    private VanillaPoseSelection selection = VanillaPoseSelection.ALL;

    VanillaPoseRetargeter(CharacterProfile profile, AssimpScene scene) {
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        profile.humanoid().forEach((role, value) -> {
            if (bind.contains(value.node())) {
                Quaternionf global = bind.global(value.node()).getUnnormalizedRotation(new Quaternionf()).normalize();
                Quaternionf correction = new Quaternionf(global).mul(value.referenceRotation().quaternion())
                        .mul(new Quaternionf(global).invert()).normalize();
                bones.put(role, new Bone(value.node(), bind.local(value.node()), global, correction, bind.position(value.node())));
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
        for (var entry : bones.entrySet()) {
            float weight = selection.weight(entry.getKey());
            if (weight == 0) continue;
            Bone bone = entry.getValue();
            Matrix4f nativeLocal = candidate.get(bone.node);
            Matrix4f incoming = locals.getOrDefault(bone.node, bone.local);
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

    private Quaternionf rotationOf(Part part, Bone bone) {
        return new Quaternionf(bone.correction).mul(axes)
                .mul(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch))
                .mul(new Quaternionf(axes).invert()).mul(new Quaternionf(bone.correction).invert()).normalize();
    }
}
