package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpNode;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable evaluation view over sparse instance locals; imported nodes remain read-only. */
public final class PoseGraph {
    private final Map<String, AssimpNode> nodes = new LinkedHashMap<>();
    private final Map<AssimpNode, AssimpNode> parents = new IdentityHashMap<>();
    private final Map<AssimpNode, Matrix4f> globals = new IdentityHashMap<>();
    private final Map<String, Matrix4f> locals;

    public PoseGraph(AssimpNode root, Map<String, Matrix4f> locals) {
        this.locals = java.util.Objects.requireNonNull(locals);
        if (root != null) collect(root, null);
    }

    private void collect(AssimpNode node, AssimpNode parent) {
        nodes.putIfAbsent(node.getName(), node);
        parents.put(node, parent);
        for (AssimpNode child : node.getChildren()) collect(child, node);
    }

    public boolean contains(String node) { return nodes.containsKey(node); }
    public boolean isAncestor(String ancestor, String descendant) {
        AssimpNode target = nodes.get(descendant);
        while (target != null) {
            if (target == nodes.get(ancestor)) return true;
            target = parents.get(target);
        }
        return false;
    }

    public Matrix4f local(String name) {
        AssimpNode node = requireNode(name);
        return new Matrix4f(locals.getOrDefault(name, node.getTransformation()));
    }

    public Matrix4f global(String name) { return new Matrix4f(global(requireNode(name))); }
    public Vector3f position(String name) { return global(name).getTranslation(new Vector3f()); }

    private Matrix4f global(AssimpNode node) {
        Matrix4f value = globals.get(node);
        if (value != null) return value;
        AssimpNode parent = parents.get(node);
        value = parent == null ? new Matrix4f() : new Matrix4f(global(parent));
        value.mul(locals.getOrDefault(node.getName(), node.getTransformation()));
        globals.put(node, value);
        return value;
    }

    public void setLocal(String name, Matrix4f matrix) {
        requireNode(name);
        if (!matrix.isFinite()) throw new IllegalArgumentException("Non-finite pose matrix");
        locals.put(name, new Matrix4f(matrix));
        globals.clear();
    }

    public void setGlobal(String name,Matrix4f matrix) {
        AssimpNode parent=parents.get(requireNode(name));
        if(parent==null) { setLocal(name,matrix);return; }
        Matrix4f transform=global(parent);
        if(Math.abs(transform.determinant())<1e-10f) throw new IllegalArgumentException("Singular parent transform: "+name);
        setLocal(name,new Matrix4f(transform).invert().mul(matrix));
    }

    /** Rotate about a joint's model-space origin, preserving its translation and scale. */
    public void rotateModel(String name, Quaternionf rotation) {
        AssimpNode node = requireNode(name);
        Matrix4f world = global(name);
        Vector3f origin = world.getTranslation(new Vector3f());
        Matrix4f result = new Matrix4f().translation(origin).rotate(rotation)
                .translate(-origin.x, -origin.y, -origin.z).mul(world);
        AssimpNode parent = parents.get(node);
        if (parent != null) {
            Matrix4f parentWorld = global(parent);
            if (Math.abs(parentWorld.determinant()) < 1e-10f) {
                throw new IllegalArgumentException("Singular parent transform: " + name);
            }
            result = new Matrix4f(parentWorld).invert().mul(result);
        }
        setLocal(name, result);
    }

    private AssimpNode requireNode(String name) {
        AssimpNode node = nodes.get(name);
        if (node == null) throw new IllegalArgumentException("Unknown pose node: " + name);
        return node;
    }
}
