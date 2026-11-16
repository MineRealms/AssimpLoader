package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/** Axis-independent aiming in model space, applied after clip blending. */
public final class LookAtModifier implements PoseModifier {
    private final String node;
    private final Vector3f forward;
    private final Vector3f target = new Vector3f();
    private final float weight;
    private final float maxAngle;

    public LookAtModifier(String node, Vector3f localForward, Vector3f target, float weight, float maxAngle) {
        if (node == null || !localForward.isFinite() || localForward.lengthSquared() < 1e-10f
                || !Float.isFinite(weight) || weight < 0 || weight > 1
                || !Float.isFinite(maxAngle) || maxAngle < 0 || maxAngle > (float) Math.PI) {
            throw new IllegalArgumentException("Invalid look-at settings");
        }
        this.node = node;
        this.forward = new Vector3f(localForward).normalize();
        this.weight = weight;
        this.maxAngle = maxAngle;
        setTarget(target);
    }

    public void setTarget(Vector3f value) {
        if (!value.isFinite()) throw new IllegalArgumentException("Non-finite look-at target");
        target.set(value);
    }

    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        if (weight == 0 || maxAngle == 0) return;
        PoseGraph graph = new PoseGraph(scene.getRootNode(), locals);
        Matrix4f world = graph.global(node);
        Vector3f direction = new Vector3f(target).sub(world.getTranslation(new Vector3f()));
        Vector3f axis = world.transformDirection(new Vector3f(forward));
        if (direction.lengthSquared() < 1e-10f || axis.lengthSquared() < 1e-10f) return;
        Quaternionf rotation = new Quaternionf().rotationTo(axis.normalize(), direction.normalize());
        float angle = 2 * (float) Math.acos(Math.min(1, Math.abs(rotation.w)));
        float fraction = angle > maxAngle ? maxAngle / angle : 1;
        graph.rotateModel(node, new Quaternionf().slerp(rotation, fraction * weight));
    }
}
