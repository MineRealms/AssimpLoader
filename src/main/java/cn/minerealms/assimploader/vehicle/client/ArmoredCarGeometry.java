package cn.minerealms.assimploader.vehicle.client;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.SceneTransforms;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Wheel articulation in imported scene coordinates, independent of FBX helper-node axes. */
public final class ArmoredCarGeometry {
    private record Wheel(Vector3f pivot, boolean front) {}
    private record Part(AssimpMesh mesh, Matrix4f bind, Wheel wheel) {}
    private final List<Part> parts = new ArrayList<>();

    public ArmoredCarGeometry(AssimpScene scene) {
        collect(scene, scene.getRootNode(), new Matrix4f(), null);
    }

    public List<SceneTransforms.Instance> pose(float roll, float steering) {
        var instances = new ArrayList<SceneTransforms.Instance>(parts.size());
        for (Part part : parts) {
            Matrix4f transform = part.bind();
            if (part.wheel() != null) {
                Wheel wheel = part.wheel();
                Vector3f pivot = wheel.pivot();
                transform = new Matrix4f().translation(pivot);
                if (wheel.front()) transform.rotateY(steering);
                transform.rotateX(roll).translate(-pivot.x, -pivot.y, -pivot.z).mul(part.bind());
            }
            instances.add(new SceneTransforms.Instance(part.mesh(), transform));
        }
        return instances;
    }

    private void collect(AssimpScene scene, AssimpNode node, Matrix4f parent, Wheel inheritedWheel) {
        if (node == null) return;
        Matrix4f global = new Matrix4f(parent).mul(node.getTransformation());
        String name = node.getName().toLowerCase(Locale.ROOT);
        Wheel wheel = inheritedWheel;
        // Only the four named pivot nodes animate; the rear-mounted spare stays fixed.
        if (List.of("wheel_front_left_steerpivot", "wheel_front_right_steerpivot",
                "wheel_rear_left_steerpivot", "wheel_rear_right_steerpivot").contains(name))
            wheel = new Wheel(global.getTranslation(new Vector3f()), name.startsWith("wheel_front_"));
        for (int index : node.getMeshIndices()) {
            if (index >= 0 && index < scene.getMeshCount())
                parts.add(new Part(scene.getMeshes().get(index), global, wheel));
        }
        for (AssimpNode child : node.getChildren()) collect(scene, child, global, wheel);
    }
}
