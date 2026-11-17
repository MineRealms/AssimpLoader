package cn.minerealms.assimploader.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.character.core.CharacterProfile;
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
        if (!root.isFinite()) throw new IllegalArgumentException("Non-finite vanilla root transform");
        this.root = new Matrix4f(root);
        this.parts = Map.copyOf(parts);
        enabled = true;
    }
    void disable() { enabled = false; }
    @Override public Category category() { return Category.MOTION; }
    @Override public void reset() { disable(); }

    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        if (!enabled) return;
        bones.values().forEach(bone -> locals.put(bone.node, new Matrix4f(bone.local)));
        var graph = new PoseGraph(scene.getRootNode(), locals);
        Bone hips = bones.get("hips");
        if (hips != null) graph.setGlobal(hips.node, new Matrix4f(root).mul(graph.global(hips.node)));
        Quaternionf rootRotation = root.getUnnormalizedRotation(new Quaternionf()).normalize();
        for (String role : new String[]{"hips", "head", "left_upper_arm", "right_upper_arm", "left_thigh", "right_thigh"}) {
            Bone bone = bones.get(role);
            Part part = parts.get(role);
            if (bone == null || part == null) continue;
            Matrix4f global = graph.global(bone.node);
            Quaternionf partRotation = rotationOf(part, bone);
            Quaternionf rotation = new Quaternionf(rootRotation).mul(partRotation).mul(bone.global).normalize();
            Vector3f position = new Vector3f(bone.origin);
            if (role.equals("hips")) partRotation.transform(position.sub(torsoPivot)).add(torsoPivot);
            position.add(axes.transform(new Vector3f(part.x, part.y, part.z).mul(pixelScale)));
            root.transformPosition(position);
            Vector3f scale = global.getScale(new Vector3f());
            graph.setGlobal(bone.node, new Matrix4f().translationRotateScale(position, rotation, scale));
        }
    }

    private Quaternionf rotationOf(Part part, Bone bone) {
        return new Quaternionf(bone.correction).mul(axes)
                .mul(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch))
                .mul(new Quaternionf(axes).invert()).mul(new Quaternionf(bone.correction).invert()).normalize();
    }
}
