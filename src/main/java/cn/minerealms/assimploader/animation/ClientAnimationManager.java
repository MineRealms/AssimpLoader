package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.Minecraft;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Client-thread playback owners. Imported scenes remain shared, playback does not. */
public class ClientAnimationManager {
    private static final ClientAnimationManager INSTANCE = new ClientAnimationManager();
    private final Map<String, AnimationState> animationStates = new HashMap<>();
    private final Map<OwnerReference, Map<String, AnimationState>> instances = new HashMap<>();
    private final ReferenceQueue<Object> collectedOwners = new ReferenceQueue<>();

    private static final class OwnerReference extends WeakReference<Object> {
        private final int hash;

        private OwnerReference(Object owner, ReferenceQueue<Object> queue) {
            super(owner, queue);
            hash = System.identityHashCode(owner);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            Object owner = get();
            return owner != null && other instanceof OwnerReference reference && owner == reference.get();
        }
    }

    ClientAnimationManager() {}

    public static ClientAnimationManager getInstance() { return INSTANCE; }

    /** Compatibility entry point: a shared default instance for this model. */
    public AnimationState getOrCreateAnimationState(String modelName) {
        drainCollectedOwners();
        return resolve(animationStates, modelName,
                AssimpModelLoader.getCachedModel(modelName, ModelImportMode.DYNAMIC));
    }

    public AnimationState getOrCreateAnimationState(Object owner, String modelName) {
        return getOrCreateAnimationState(owner, modelName,
                AssimpModelLoader.getCachedModel(modelName, ModelImportMode.DYNAMIC));
    }

    /** Keep a stable owner (e.g. an ItemStack); release explicitly when its lifetime is known. */
    public AnimationState getOrCreateAnimationState(Object owner, String modelName, AssimpScene scene) {
        Objects.requireNonNull(owner, "Animation owner");
        drainCollectedOwners();
        return resolve(instances.computeIfAbsent(new OwnerReference(owner, collectedOwners), ignored -> new HashMap<>()),
                modelName, scene);
    }

    private AnimationState resolve(Map<String, AnimationState> states, String name, AssimpScene scene) {
        Objects.requireNonNull(name, "Model name");
        AnimationState existing = states.get(name);
        if (scene == null || !scene.isDynamic()) {
            if (existing != null) existing.close();
            states.remove(name);
            return null;
        }
        if (existing != null && !existing.isClosed() && existing.getScene() == scene) return existing;
        if (existing != null) existing.close();
        var definition = InstanceDefinitionResources.forModel(scene.getSourceResource());
        if (definition.isEmpty() && !scene.hasAnimations() && !scene.hasSkeleton()
                && scene.getMeshes().stream().noneMatch(mesh -> mesh.hasMorphTargets())) {
            states.remove(name);
            return null;
        }
        AnimationState state;
        if (definition.isPresent()) state = definition.get().instantiate(scene).animation();
        else {
            state = new AnimationState(name, scene);
            state.initialize();
        }
        states.put(name, state);
        return state;
    }

    /** Called once at client tick END; paused single-player worlds do not advance animation. */
    public void tick() {
        drainCollectedOwners();
        if (!Minecraft.getInstance().isPaused()) update(0.05f);
    }

    /** Deterministic simulation step in seconds, independent of rendering and wall-clock stalls. */
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime < 0) {
            throw new IllegalArgumentException("Animation delta must be finite and non-negative");
        }
        drainCollectedOwners();
        updateStates(animationStates, deltaTime);
        for (Map<String, AnimationState> states : instances.values()) updateStates(states, deltaTime);
    }

    private static void updateStates(Map<String, AnimationState> states, float deltaTime) {
        for (AnimationState state : states.values()) {
            if (state.isInitialized()) state.update(deltaTime);
        }
    }

    public void release(Object owner) {
        drainCollectedOwners();
        if (owner == null) return;
        Map<String, AnimationState> removed = instances.remove(new OwnerReference(owner, null));
        if (removed != null) removed.values().forEach(AnimationState::close);
    }

    private void drainCollectedOwners() {
        OwnerReference owner;
        while ((owner = (OwnerReference) collectedOwners.poll()) != null) {
            Map<String, AnimationState> removed = instances.remove(owner);
            if (removed != null) removed.values().forEach(AnimationState::close);
        }
    }

    public void clear() {
        drainCollectedOwners();
        animationStates.values().forEach(AnimationState::close);
        instances.values().forEach(states -> states.values().forEach(AnimationState::close));
        animationStates.clear();
        instances.clear();
    }

    public static void clearCache() { INSTANCE.clear(); }
    public AnimationState getAnimationState(String modelName) { return animationStates.get(modelName); }
}
