package cn.minerealms.assimploader.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Blends multiple animations together.
 *
 * Supports:
 * - Linear blending between two animations
 * - Additive blending
 * - Layered animation (upper body + lower body)
 * - Smooth transitions
 *
 * Blending formula:
 * result = anim1 * (1 - weight) + anim2 * weight
 *
 * For rotations, use SLERP (Spherical Linear Interpolation)
 *
 * References:
 * - https://www.gdcvault.com/play/1020583/Animation-Bootcamp-An-Indie-Approach
 * - https://theorangeduck.com/page/spring-roll-call#animation
 */
public class AnimationBlender {

    /**
     * Animation layer for blending
     */
    public static class AnimationLayer {
        private final AnimationPlayer player;
        private float weight;
        private boolean additive;

        public AnimationLayer(AnimationPlayer player, float weight, boolean additive) {
            this.player = player;
            this.weight = weight;
            this.additive = additive;
        }

        public AnimationPlayer getPlayer() {
            return player;
        }

        public float getWeight() {
            return weight;
        }

        public void setWeight(float weight) {
            this.weight = Math.max(0.0f, Math.min(1.0f, weight));
        }

        public boolean isAdditive() {
            return additive;
        }
    }

    private final List<AnimationLayer> layers = new ArrayList<>();

    /**
     * Add animation layer
     */
    public void addLayer(AnimationPlayer player, float weight, boolean additive) {
        layers.add(new AnimationLayer(player, weight, additive));
    }

    /**
     * Remove animation layer
     */
    public void removeLayer(AnimationPlayer player) {
        layers.removeIf(layer -> layer.getPlayer() == player);
    }

    /**
     * Clear all layers
     */
    public void clearLayers() {
        layers.clear();
    }

    /**
     * Update all animation layers
     */
    public void update(float deltaTime) {
        for (AnimationLayer layer : layers) {
            layer.getPlayer().update(deltaTime);
        }
    }

    /**
     * Blend all layers and apply to bone hierarchy
     */
    public void applyToHierarchy(BoneHierarchyBuilder hierarchy) {
        if (layers.isEmpty()) {
            return;
        }

        // Normalize weights
        float totalWeight = 0.0f;
        for (AnimationLayer layer : layers) {
            if (!layer.isAdditive()) {
                totalWeight += layer.getWeight();
            }
        }

        if (totalWeight > 0.0f) {
            for (AnimationLayer layer : layers) {
                if (!layer.isAdditive()) {
                    layer.setWeight(layer.getWeight() / totalWeight);
                }
            }
        }

        // Apply blended transforms to each bone
        for (String boneName : hierarchy.getAllBones().keySet()) {
            Bone bone = hierarchy.getBone(boneName);
            if (bone == null) continue;

            Vector3f blendedPosition = new Vector3f();
            Quaternionf blendedRotation = new Quaternionf();
            Vector3f blendedScale = new Vector3f();
            boolean hasTransform = false;

            // Blend all layers
            for (AnimationLayer layer : layers) {
                AnimationPlayer player = layer.getPlayer();
                float weight = layer.getWeight();

                // Get transform from this layer
                Vector3f position = player.getBonePosition(boneName);
                Quaternionf rotation = player.getBoneRotation(boneName);
                Vector3f scale = player.getBoneScale(boneName);

                if (position == null || rotation == null || scale == null) {
                    continue;
                }

                if (!hasTransform) {
                    // First layer - copy directly
                    blendedPosition.set(position).mul(weight);
                    blendedRotation.set(rotation);
                    if (weight < 1.0f) {
                        blendedRotation.slerp(new Quaternionf(), 1.0f - weight);
                    }
                    blendedScale.set(scale).mul(weight);
                    hasTransform = true;
                } else {
                    if (layer.isAdditive()) {
                        // Additive blending
                        blendedPosition.add(position.mul(weight, new Vector3f()));
                        blendedRotation.mul(rotation);
                        blendedScale.add(scale.mul(weight, new Vector3f()));
                    } else {
                        // Linear blending
                        blendedPosition.add(position.mul(weight, new Vector3f()));
                        blendedRotation.slerp(rotation, weight);
                        blendedScale.add(scale.mul(weight, new Vector3f()));
                    }
                }
            }

            // Apply blended transform to bone
            if (hasTransform) {
                Matrix4f localTransform = new Matrix4f()
                    .translate(blendedPosition)
                    .rotate(blendedRotation)
                    .scale(blendedScale);
                bone.setLocalTransform(localTransform);
            }
        }

        // Update hierarchy transforms
        hierarchy.updateTransforms();
    }

    /**
     * Blend two animations with a weight
     */
    public static Matrix4f blendTransforms(Matrix4f transform1, Matrix4f transform2, float weight) {
        // Decompose matrices
        Vector3f pos1 = new Vector3f();
        Quaternionf rot1 = new Quaternionf();
        Vector3f scale1 = new Vector3f();
        transform1.getTranslation(pos1);
        transform1.getUnnormalizedRotation(rot1);
        transform1.getScale(scale1);

        Vector3f pos2 = new Vector3f();
        Quaternionf rot2 = new Quaternionf();
        Vector3f scale2 = new Vector3f();
        transform2.getTranslation(pos2);
        transform2.getUnnormalizedRotation(rot2);
        transform2.getScale(scale2);

        // Blend components
        Vector3f blendedPos = pos1.lerp(pos2, weight);
        Quaternionf blendedRot = rot1.slerp(rot2, weight);
        Vector3f blendedScale = scale1.lerp(scale2, weight);

        // Compose result
        return new Matrix4f()
            .translate(blendedPos)
            .rotate(blendedRot)
            .scale(blendedScale);
    }

    /**
     * Create smooth transition between two animations
     */
    public static AnimationBlender createTransition(
            AnimationPlayer fromAnim,
            AnimationPlayer toAnim,
            float transitionDuration) {

        AnimationBlender blender = new AnimationBlender();
        blender.addLayer(fromAnim, 1.0f, false);
        blender.addLayer(toAnim, 0.0f, false);

        // Transition logic would be handled by updating weights over time
        // This is a simplified version - full implementation would need a transition controller

        return blender;
    }

    /**
     * Get number of layers
     */
    public int getLayerCount() {
        return layers.size();
    }

    /**
     * Get layer by index
     */
    public AnimationLayer getLayer(int index) {
        return layers.get(index);
    }

    /**
     * Check if blender has any layers
     */
    public boolean hasLayers() {
        return !layers.isEmpty();
    }
}
