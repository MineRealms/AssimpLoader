package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
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
    private final Map<String, AnimationPoseLayer> layers = new LinkedHashMap<>();
    private final Map<String, PoseModifier> modifiers = new LinkedHashMap<>();
    private final Map<AssimpMesh, float[]> morphWeights = new HashMap<>();
    private Map<String, Matrix4f> evaluatedBasePose = Map.of();
    private final AnimationStateMachine stateMachine = new AnimationStateMachine(this);
    private double simulationTime;
    private boolean stopped;
    private ModelInstance modelInstance;
    private InstancePerformanceSettings performance = InstancePerformanceSettings.DEFAULT;
    private AnimationPlayer currentAnimation;
    private AnimationPlayer transitionSource;
    private Map<String, Matrix4f> transitionSourcePose;
    private Map<AssimpMesh, float[]> transitionSourceMorphs;
    private Map<AssimpMesh, float[]> evaluatedBaseMorphs = Map.of();
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
        if (!initialized || paused || stopped || deltaTime == 0) return;
        if (currentAnimation != null) currentAnimation.update(deltaTime);
        if (transitionSource != null) transitionSource.update(deltaTime);
        layers.values().forEach(layer -> layer.update(deltaTime));
        simulationTime += deltaTime;
        if (isTransitioning()) {
            transitionTime = Math.min(transitionDuration, transitionTime + deltaTime);
            if (transitionTime >= transitionDuration) clearTransition();
        }
        publishPose(deltaTime);
    }

    /** Legacy immediate switch retains the target clip's playback position. */
    public boolean playAnimation(String animationName) {
        requireOpen();
        if (!initialized) initialize();
        AnimationPlayer next = animationPlayers.get(animationName);
        if (next == null) return false;
        stopped = false;
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
        Map<String, Matrix4f> displayed = evaluatedBasePose;
        Map<AssimpMesh, float[]> displayedMorphs = evaluatedBaseMorphs;
        AnimationPlayer previous = currentAnimation;
        clearTransition();
        if (interrupted && previous != null) previous.pause();
        currentAnimation = next;
        stopped = false;
        next.stop();
        next.play();
        if (durationSeconds > 0) {
            transitionSource = interrupted ? null : previous;
            transitionSourcePose = copyTransforms(displayed);
            transitionSourceMorphs = copyWeights(displayedMorphs);
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
    public float getTransitionWeight() { return isTransitioning() ? (float) (transitionTime / transitionDuration) : 1; }
    public boolean isPaused() { return paused; }
    public void pause() { requireOpen(); paused = true; }
    public void resume() { requireOpen(); paused = false; stopped = false; }

    /** Stops all clips and publishes the bind pose, including nodes absent from the last clip. */
    public void stop() {
        requireOpen();
        if (!initialized) initialize();
        clearTransition();
        for (AnimationPlayer player : animationPlayers.values()) player.stop();
        currentAnimation = null;
        stateMachine.resetSelection();
        layers.clear();
        modifiers.values().forEach(PoseModifier::reset);
        stopped = true;
        if (modelInstance != null) modelInstance.resetEffects();
        publishPose();
    }

    private void clearTransition() {
        if (transitionSource != null && transitionSource != currentAnimation) transitionSource.pause();
        transitionSource = null;
        transitionSourcePose = null;
        transitionSourceMorphs = null;
        transitionTime = 0;
        transitionDuration = 0;
    }

    private Map<String, Matrix4f> currentLocals(AnimationPlayer player) {
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        if (player != null) player.getBoneTransforms().forEach((name, matrix) -> locals.put(name, new Matrix4f(matrix)));
        return locals;
    }

    private void publishPose() {
        publishPose(0);
    }

    private void publishPose(double deltaSeconds) {
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
        evaluatedBasePose = copyTransforms(locals);
        if (!stopped) {
            layers.values().forEach(layer -> layer.apply(locals, bindTransforms));
            var policy = RuntimePerformancePolicy.current();
            for (PoseModifier.Category category:PoseModifier.Category.values()) for (PoseModifier modifier : modifiers.values()) {
                if(modifier.category()!=category) continue;
                boolean enabled = switch (modifier.category()) {
                    case GENERAL -> true;
                    case GROUND, IK -> policy.ikEnabled() && performance.ik();
                    case SPRING -> policy.springBonesEnabled() && performance.springBones();
                };
                if (enabled) modifier.apply(scene, locals, deltaSeconds);
                else modifier.reset();
            }
        }
        Map<AssimpMesh, float[]> sampledMorphs = sampleMorphs(currentAnimation);
        if (isTransitioning()) {
            Map<AssimpMesh, float[]> source = transitionSource == null ? transitionSourceMorphs : sampleMorphs(transitionSource);
            float weight = getTransitionWeight();
            for (AssimpMesh mesh : scene.getMeshes()) {
                if (!mesh.hasMorphTargets()) continue;
                float[] from = source.getOrDefault(mesh, mesh.getDefaultMorphWeights());
                float[] to = sampledMorphs.getOrDefault(mesh, mesh.getDefaultMorphWeights());
                float[] blended = new float[to.length];
                for (int i = 0; i < to.length; i++) blended[i] = from[i] + (to[i] - from[i]) * weight;
                sampledMorphs.put(mesh, blended);
            }
        }
        evaluatedBaseMorphs = copyWeights(sampledMorphs);
        if (!stopped) for (AnimationPoseLayer layer : layers.values()) {
            Map<AssimpMesh, float[]> layerMorphs=sampleMorphs(layer.getPlayer());
            layerMorphs.forEach((mesh,values) -> {
                float mask=layer.getMask().isEmpty()?1:layer.getMask().getOrDefault(mesh.getName(),0f);
                if (!layer.getMask().isEmpty()) for (var channel:layer.getPlayer().getAnimation().getMorphChannels()) {
                    if (nodeOwnsMesh(scene.getRootNode(),channel.getMeshName(),scene.getMeshes().indexOf(mesh)))
                        mask=Math.max(mask,layer.getMask().getOrDefault(channel.getMeshName(),0f));
                }
                float weight=layer.getWeight()*mask;
                if(weight==0) return;
                float[] defaults=mesh.getDefaultMorphWeights();
                float[] base=sampledMorphs.getOrDefault(mesh,defaults).clone();
                for(int i=0;i<base.length;i++) base[i]=layer.isAdditive()?base[i]+(values[i]-defaults[i])*weight:base[i]+(values[i]-base[i])*weight;
                sampledMorphs.put(mesh,base);
            });
        }
        sampledMorphs.putAll(morphWeights);
        poseSnapshot = PoseSnapshot.capture(++frameId, scene, locals, sampledMorphs);
        if (modelInstance != null && !stopped) modelInstance.afterPose();
    }

    private Map<AssimpMesh, float[]> sampleMorphs(AnimationPlayer player) {
        Map<AssimpMesh, float[]> result = new HashMap<>();
        if (player == null) return result;
        for (var channel : player.getAnimation().getMorphChannels()) {
            for (int i = 0; i < scene.getMeshes().size(); i++) {
                AssimpMesh mesh = scene.getMeshes().get(i);
                if (mesh.hasMorphTargets() && (mesh.getName().equals(channel.getMeshName())
                        || nodeOwnsMesh(scene.getRootNode(), channel.getMeshName(), i))) {
                    result.put(mesh, channel.sample(player.getCurrentTime(), mesh.getMorphTargets().size()));
                }
            }
        }
        return result;
    }

    private static boolean nodeOwnsMesh(AssimpNode node, String name, int meshIndex) {
        if (node == null) return false;
        if (node.getName().equals(name)) for (int index : node.getMeshIndices()) if (index == meshIndex) return true;
        for (AssimpNode child : node.getChildren()) if (nodeOwnsMesh(child, name, meshIndex)) return true;
        return false;
    }

    private static Map<AssimpMesh, float[]> copyWeights(Map<AssimpMesh, float[]> source) {
        Map<AssimpMesh, float[]> result = new HashMap<>();
        source.forEach((mesh, values) -> result.put(mesh, values.clone()));
        return result;
    }

    public AnimationStateMachine getStateMachine() { return stateMachine; }
    public PoseModifier getModifier(String id) { return modifiers.get(id); }
    public ModelInstance getModelInstance() { return modelInstance; }
    public InstancePerformanceSettings getPerformanceSettings() { return performance; }
    public void setPerformanceSettings(InstancePerformanceSettings settings) {
        requireOpen();
        performance = java.util.Objects.requireNonNull(settings);
        modifiers.values().forEach(PoseModifier::reset);
        if (modelInstance != null) modelInstance.resetEffects();
        publishPose();
    }
    void setModelInstance(ModelInstance instance) { modelInstance = instance; }
    public double getSimulationTime() { return simulationTime; }

    public AnimationPoseLayer setLayer(String id, String clip, float weight, boolean additive, Map<String, Float> mask) {
        requireOpen();
        if (!initialized) initialize();
        var animation = scene.getAnimations().stream().filter(value -> value.getName().equals(clip)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown layer clip: " + clip));
        var layer = new AnimationPoseLayer(animation, scene.getRootNode(), weight, additive, mask);
        layers.put(java.util.Objects.requireNonNull(id), layer);
        stopped = false;
        publishPose();
        return layer;
    }

    public AnimationPoseLayer getLayer(String id) { return layers.get(id); }
    public void setLayerWeight(String id, float weight) {
        requireOpen();
        AnimationPoseLayer layer = layers.get(id);
        if (layer == null) throw new IllegalArgumentException("Unknown layer: " + id);
        layer.setWeight(weight);
        publishPose();
    }
    public void removeLayer(String id) { requireOpen(); layers.remove(id); publishPose(); }

    public void addModifier(String id, PoseModifier modifier) {
        requireOpen();
        PoseModifier old = modifiers.put(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(modifier));
        if (old != null && old != modifier) old.close();
        stopped = false;
        publishPose();
    }

    public void removeModifier(String id) {
        requireOpen();
        PoseModifier old = modifiers.remove(id);
        if (old != null) old.close();
        publishPose();
    }

    public void resetDynamics() {
        requireOpen();
        modifiers.values().forEach(PoseModifier::reset);
        if (modelInstance != null) modelInstance.resetEffects();
        publishPose();
    }

    public void setMorphWeights(AssimpMesh mesh, float[] weights) {
        requireOpen();
        if (!initialized) initialize();
        if (scene == null || !scene.getMeshes().contains(mesh)) throw new IllegalArgumentException("Mesh is not in this scene");
        if (weights.length != mesh.getMorphTargets().size()) throw new IllegalArgumentException("Morph target count mismatch");
        for (float weight : weights) {
            if (!Float.isFinite(weight)) throw new IllegalArgumentException("Morph weight must be finite");
        }
        morphWeights.put(mesh, weights.clone());
        publishPose();
    }

    public void resetMorphWeights() { requireOpen(); morphWeights.clear(); publishPose(); }

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
        modifiers.values().forEach(PoseModifier::close);
        modifiers.clear();
        layers.clear();
        morphWeights.clear();
        if (modelInstance != null) modelInstance.release();
        modelInstance = null;
        poseSnapshot = null;
        cachedBoneTransforms.clear();
        boneHierarchies.clear();
        closed = true;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Animation instance is closed");
    }
}
