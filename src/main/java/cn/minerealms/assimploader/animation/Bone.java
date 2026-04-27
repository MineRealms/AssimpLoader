package cn.minerealms.assimploader.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a bone in the skeletal hierarchy.
 *
 * Each bone has:
 * - Name for identification
 * - Offset matrix (bind pose transformation)
 * - Parent-child relationships
 * - Final transformation matrix
 */
public class Bone {

    private final String name;
    private final Matrix4f offsetMatrix;
    private final List<Bone> children;
    private Bone parent;

    private Matrix4f localTransform;
    private Matrix4f globalTransform;
    private Matrix4f finalTransform;

    public Bone(String name, Matrix4f offsetMatrix) {
        this.name = name;
        this.offsetMatrix = new Matrix4f(offsetMatrix);
        this.children = new ArrayList<>();
        this.localTransform = new Matrix4f();
        this.globalTransform = new Matrix4f();
        this.finalTransform = new Matrix4f();
    }

    /**
     * Add a child bone to this bone
     */
    public void addChild(Bone child) {
        children.add(child);
        child.parent = this;
    }

    /**
     * Update global transformation based on parent
     */
    public void updateTransforms() {
        if (parent != null) {
            parent.globalTransform.mul(localTransform, globalTransform);
        } else {
            globalTransform.set(localTransform);
        }

        // Final transform = globalTransform * offsetMatrix
        globalTransform.mul(offsetMatrix, finalTransform);

        // Update children recursively
        for (Bone child : children) {
            child.updateTransforms();
        }
    }

    public String getName() {
        return name;
    }

    public Matrix4f getOffsetMatrix() {
        return offsetMatrix;
    }

    public Matrix4f getLocalTransform() {
        return localTransform;
    }

    public void setLocalTransform(Matrix4f localTransform) {
        this.localTransform.set(localTransform);
    }

    public Matrix4f getGlobalTransform() {
        return globalTransform;
    }

    public Matrix4f getFinalTransform() {
        return finalTransform;
    }

    public List<Bone> getChildren() {
        return children;
    }

    public Bone getParent() {
        return parent;
    }

    /**
     * Get world position of this bone.
     * Extracts translation from global transform.
     */
    public Vector3f getWorldPosition() {
        Vector3f position = new Vector3f();
        globalTransform.getTranslation(position);
        return position;
    }

    /**
     * Get world rotation of this bone.
     * Extracts rotation from global transform.
     */
    public Quaternionf getWorldRotation() {
        Quaternionf rotation = new Quaternionf();
        globalTransform.getNormalizedRotation(rotation);
        return rotation;
    }

    /**
     * Get local rotation (extracted from local transform matrix).
     */
    public Quaternionf getLocalRotation() {
        Quaternionf rotation = new Quaternionf();
        localTransform.getNormalizedRotation(rotation);
        return rotation;
    }

    /**
     * Set local transform from position, rotation, and scale.
     * Utility method for IK solvers.
     */
    public void setLocalTransform(Vector3f position, Quaternionf rotation, Vector3f scale) {
        localTransform.identity();
        localTransform.translationRotateScale(position, rotation, scale);
    }

    /**
     * Set only the rotation component of local transform.
     * Preserves position and scale. Utility method for IK solvers.
     */
    public void setLocalRotation(Quaternionf rotation) {
        Vector3f position = new Vector3f();
        Vector3f scale = new Vector3f();
        localTransform.getTranslation(position);
        localTransform.getScale(scale);
        setLocalTransform(position, rotation, scale);
    }
}
