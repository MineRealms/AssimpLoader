package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Single-writer playback state. Renderers and attachments consume a completed snapshot. */
public class AnimationState implements AutoCloseable {
    private final String modelName;
    private final AssimpScene scene;
    private final Map<String, AnimationPlayer> animationPlayers = new LinkedHashMap<>();
    private final Map<String, Matrix4f> bindTransforms = new LinkedHashMap<>();
    private final Map<AssimpMesh, BoneHierarchyBuilder> boneHierarchies = new HashMap<>();
    private final Map<AssimpMesh, Matrix4f[]> cachedBoneTransforms = new HashMap<>();
    private AnimationPlayer currentAnimation;
    private AnimationPlayer transitionSource;
    private Map<String, Matrix4f> transitionSourcePose;
    private double transitionTime;
    private double transitionDuration;
    private long frameId;
    private long hierarchyFrame = -1;
    private long paletteFrame = -1;
    private volatile PoseSnapshot poseSnapshot;
    private boolean initialized;
    private boolean paused;
    private boolean closed;

    public AnimationState(String modelName) {
        this(modelName, AssimpModelLoader.getCachedModel(modelName, ModelImportMode.DYNAMIC));
    }

    public AnimationState(String modelName, AssimpScene scene) {
        this.modelName = modelName;
        this.scene = scene;
    }

    public void initialize() {
        requireOpen();
        if (initialized || scene == null) return;
        collectBindTransforms(scene.getRootNode());
        for (AssimpMesh mesh : scene.getMeshes()) {
            for (String bone : mesh.getBoneOffsets().keySet()) bindTransforms.putIfAbsent(bone, new Matrix4f());
        }
        for (var animation : scene.getAnimations()) {
            var player = new AnimationPlayer(animation, scene.getRootNode());
            player.setLooping(true);
            animationPlayers.put(animation.getName(), player);
        }
        if (!animationPlayers.isEmpty()) {
            currentAnimation = animationPlayers.values().iterator().next();
            currentAnimation.play();
        }
        initialized = true;
        publishPose();
    }

    private void collectBindTransforms(AssimpNode node) {
        if (node == null) return;
        bindTransforms.putIfAbsent(node.getName(), new Matrix4f(node.getTransformation()));
        for (AssimpNode child : node.getChildren()) collectBindTransforms(child);
    }

    /** Simulation seconds; drawing a snapshot never advances this clock. */
    public void update(float deltaTime) {
        requireOpen();
        if (!Float.isFinite(deltaTime) || deltaTime < 0) {
            throw new IllegalArgumentException("Animation delta must be finite and non-negative");
        }
        if (!initialized || paused || deltaTime == 0 || currentAnimation == null) return;
        if (!currentAnimation.isPlaying() && !isTransitioning()) return;
        currentAnimation.update(deltaTime);
        if (transitionSource != null) transitionSource.update(deltaTime);
        if (isTransitioning()) {
            transitionTime = Math.min(transitionDuration, transitionTime + deltaTime);
            if (transitionTime >= transitionDuration) clearTransition();
        }
        publishPose();
    }

    /** Legacy immediate switch retains the target clip's playback position. */
    public boolean playAnimation(String animationName) {
        requireOpen();
        if (!initialized) initialize();
        AnimationPlayer next = animationPlayers.get(animationName);
        if (next == null) return false;
        clearTransition();
        if (currentAnimation != null && currentAnimation != next) currentAnimation.pause();
        currentAnimation = next;
        next.play();
        publishPose();
        return true;
    }

    /** Starts the target at its first sample and blends local TRS; interruption preserves the displayed pose. */
    public boolean transitionTo(String animationName, double durationSeconds) {
        requireOpen();
        if (!Double.isFinite(durationSeconds) || durationSeconds < 0) {
            throw new IllegalArgumentException("Transition duration must be finite and non-negative");
        }
        if (!initialized) initialize();
        AnimationPlayer next = animationPlayers.get(animationName);
        if (next == null) return false;
        if (next == currentAnimation) return true;
        boolean interrupted = isTransitioning();
        Map<String, Matrix4f> displayed = poseSnapshot == null ? Map.of() : poseSnapshot.copyLocalOverrides();
        AnimationPlayer previous = currentAnimation;
        clearTransition();
        if (interrupted && previous != null) previous.pause();
        currentAnimation = next;
        next.stop();
        next.play();
        if (durationSeconds > 0) {
            transitionSource = interrupted ? null : previous;
            transitionSourcePose = copyTransforms(displayed);
            transitionDuration = durationSeconds;
            transitionTime = 0;
        } else if (previous != null) {
            previous.pause();
        }
        publishPose();
        return true;
    }

    public boolean playAnimation(String animationName, double durationSeconds) {
        return transitionTo(animationName, durationSeconds);
    }

    public boolean isTransitioning() { return transitionSourcePose != null; }
    public boolean isPaused() { return paused; }
    public void pause() { requireOpen(); paused = true; }
    public void resume() { requireOpen(); paused = false; }

    /** Stops all clips and publishes the bind pose, including nodes absent from the last clip. */
    public void stop() {
        requireOpen();
        if (!initialized) initialize();
        clearTransition();
        for (AnimationPlayer player : animationPlayers.values()) player.stop();
        currentAnimation = null;
        publishPose();
    }

    private void clearTransition() {
        if (transitionSource != null && transitionSource != currentAnimation) transitionSource.pause();
        transitionSource = null;
        transitionSourcePose = null;
        transitionTime = 0;
        transitionDuration = 0;
    }

    private Map<String, Matrix4f> currentLocals(AnimationPlayer player) {
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        if (player != null) player.getBoneTransforms().forEach((name, matrix) -> locals.put(name, new Matrix4f(matrix)));
        return locals;
    }

    private void publishPose() {
        if (!initialized || scene == null) return;
        Map<String, Matrix4f> locals = currentLocals(currentAnimation);
        if (isTransitioning()) {
            Map<String, Matrix4f> source = transitionSource == null ? transitionSourcePose : currentLocals(transitionSource);
            float weight = (float) (transitionTime / transitionDuration);
            var names = new LinkedHashSet<>(source.keySet());
            names.addAll(locals.keySet());
            for (String name : names) {
                Matrix4f from = source.getOrDefault(name, bindTransforms.getOrDefault(name, new Matrix4f()));
                Matrix4f to = locals.getOrDefault(name, bindTransforms.getOrDefault(name, new Matrix4f()));
                locals.put(name, weight == 0 || from.equals(to) ? new Matrix4f(from)
                        : AnimationBlender.blendTransforms(from, to, weight));
            }
        }
        poseSnapshot = PoseSnapshot.capture(++frameId, scene, locals);
    }

    private static Map<String, Matrix4f> copyTransforms(Map<String, Matrix4f> source) {
        Map<String, Matrix4f> copy = new LinkedHashMap<>();
        source.forEach((name, matrix) -> copy.put(name, new Matrix4f(matrix)));
        return copy;
    }

    public PoseSnapshot getPoseSnapshot() { return poseSnapshot; }

    /** Compatibility view; new consumers should use getPoseSnapshot() instead. */
    public BoneHierarchyBuilder getBoneHierarchy(AssimpMesh mesh) {
        ensureCompatibilityHierarchies();
        return boneHierarchies.get(mesh);
    }

    public Map<AssimpMesh, BoneHierarchyBuilder> getBoneHierarchies() {
        ensureCompatibilityHierarchies();
        return Collections.unmodifiableMap(boneHierarchies);
    }

    private void ensureCompatibilityHierarchies() {
        PoseSnapshot snapshot = poseSnapshot;
        if (snapshot == null || hierarchyFrame == snapshot.getFrameId()) return;
        Map<String, Matrix4f> locals = snapshot.copyLocalOverrides();
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (!mesh.hasBones()) continue;
            BoneHierarchyBuilder hierarchy = boneHierarchies.computeIfAbsent(mesh, value -> {
                var result = new BoneHierarchyBuilder();
                result.buildFromMesh(value, scene.getRootNode());
                return result;
            });
            hierarchy.resetToBindPose();
            locals.forEach(hierarchy::updateBoneTransform);
            hierarchy.updateTransforms();
        }
        hierarchyFrame = snapshot.getFrameId();
    }

    /** Legacy palette cache. Consumers must not modify the returned arrays or matrices. */
    public Matrix4f[] getCachedBoneTransforms(AssimpMesh mesh) {
        PoseSnapshot snapshot = poseSnapshot;
        if (snapshot == null) return null;
        if (paletteFrame != snapshot.getFrameId()) {
            cachedBoneTransforms.clear();
            for (AssimpMesh candidate : scene.getMeshes()) {
                Matrix4f[] palette = snapshot.getBoneTransforms(candidate);
                if (palette != null) cachedBoneTransforms.put(candidate, palette);
            }
            paletteFrame = snapshot.getFrameId();
        }
        return cachedBoneTransforms.get(mesh);
    }

    public void invalidateCache() {
        requireOpen();
        publishPose();
    }

    public String[] getAvailableAnimations() { return animationPlayers.keySet().toArray(new String[0]); }
    public AnimationPlayer getCurrentAnimation() { return currentAnimation; }
    public boolean hasAnimations() { return !animationPlayers.isEmpty(); }
    public boolean hasSkinnedMeshes() { return scene != null && scene.hasSkeleton(); }
    public boolean isInitialized() { return initialized && !closed; }
    public boolean isClosed() { return closed; }
    public AssimpScene getScene() { return scene; }
    public String getModelName() { return modelName; }

    @Override public void close() {
        if (closed) return;
        clearTransition();
        for (AnimationPlayer player : animationPlayers.values()) player.pause();
        currentAnimation = null;
        poseSnapshot = null;
        cachedBoneTransforms.clear();
        boneHierarchies.clear();
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Animation instance is closed");
    }
}
