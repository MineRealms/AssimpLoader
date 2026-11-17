package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reconstructs skin rest transforms when an FBX node tree was saved in an animated pose. */
public final class SkinBindPose {
    private SkinBindPose() {}

    public static Map<String, Matrix4f> locals(AssimpScene scene) {
        var nodes = new PoseGraph(scene.getRootNode(), Map.of());
        Map<String, Matrix4f> globals = new LinkedHashMap<>();
        for (var mesh : scene.getMeshes()) {
            String meshNode = scene.getNodeNameForMesh(mesh);
            Matrix4f meshBind = meshNode != null && nodes.contains(meshNode) ? nodes.global(meshNode) : new Matrix4f();
            mesh.getBoneOffsets().forEach((name, offset) -> {
                if (Math.abs(offset.determinant()) > 1e-12f)
                    globals.putIfAbsent(name, new Matrix4f(meshBind).mul(new Matrix4f(offset).invert()));
            });
        }
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        collect(scene.getRootNode(), new Matrix4f(), globals, locals);
        return Collections.unmodifiableMap(locals);
    }

    private static void collect(AssimpNode node, Matrix4f parent, Map<String, Matrix4f> globals, Map<String, Matrix4f> locals) {
        if (node == null) return;
        Matrix4f global = globals.get(node.getName());
        Matrix4f local;
        if (global == null || Math.abs(parent.determinant()) < 1e-12f) {
            local = new Matrix4f(node.getTransformation());
            global = new Matrix4f(parent).mul(local);
        } else local = new Matrix4f(parent).invert().mul(global);
        locals.put(node.getName(), local);
        for (var child : node.getChildren()) collect(child, global, globals, locals);
    }
}
