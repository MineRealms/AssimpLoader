package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import net.minecraft.client.Minecraft;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** Client-thread playback owners. Imported scenes remain shared, playback does not. */
public class ClientAnimationManager {
    private static final ClientAnimationManager INSTANCE = new ClientAnimationManager();
    private final Map<String, AnimationState> animationStates = new HashMap<>();
    private final Map<OwnerReference, Map<String, AnimationState>> instances = new HashMap<>();
    private final ReferenceQueue<Object> collectedOwners = new ReferenceQueue<>();
    private final Function<ResourceLocation, Optional<ModelInstanceDefinition>> definitions;
    private final Set<AssimpScene> invalidDefinitions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AnimationState, AnimationCadence> cadences = new IdentityHashMap<>();
    private long tick;
    private int nextPhase, evaluated, skipped, paused;
    private long totalEvaluated, totalSkipped;
    private RuntimePerformancePolicy.Mode mode = RuntimePerformancePolicy.current().mode();

    public record PerformanceStatistics(RuntimePerformancePolicy.Mode mode, int instances,
            int evaluated, int skipped, int paused, long totalEvaluated, long totalSkipped) {}

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

    ClientAnimationManager() { this(InstanceDefinitionResources::forModel); }

    ClientAnimationManager(Function<ResourceLocation, Optional<ModelInstanceDefinition>> definitions) {
        this.definitions = Objects.requireNonNull(definitions);
    }

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
            if (existing != null) closeState(existing);
            states.remove(name);
            return null;
        }
        if (existing != null && !existing.isClosed() && existing.getScene() == scene) return existing;
        if (existing != null) closeState(existing);
        Optional<ModelInstanceDefinition> definition = Optional.empty();
        AnimationState state = null;
        if (!invalidDefinitions.contains(scene)) {
            try {
                definition = definitions.apply(scene.getSourceResource());
                if (definition.isPresent()) state = definition.get().instantiate(scene).animation();
            } catch (RuntimeException failure) {
                invalidDefinitions.add(scene);
                AssimpLoader.LOGGER.error("Ignoring invalid instance definition for {}; using base playback until reload", name, failure);
            }
        }
        if (definition.isEmpty() && !scene.hasAnimations() && !scene.hasSkeleton()
                && scene.getMeshes().stream().noneMatch(mesh -> mesh.hasMorphTargets())) {
            states.remove(name);
            return null;
        }
        if (state == null) {
            state = new AnimationState(name, scene);
            state.initialize();
        }
        states.put(name, state);
        cadences.put(state, new AnimationCadence(nextPhase++));
        return state;
    }

    /** Called once at client tick END; paused single-player worlds do not advance animation. */
    public void tick() {
        drainCollectedOwners();
        if (!Minecraft.getInstance().isPaused()) updateManagedTick();
    }

    /** Reports visibility only; multiple render passes never advance playback. Distance is squared blocks. */
    public void markRendered(AnimationState state, double distanceSquared) {
        AnimationCadence cadence = cadences.get(state);
        if (cadence != null) cadence.rendered(tick, distanceSquared);
    }

    public void updateManagedTick() { updateManagedTick(RuntimePerformancePolicy.current()); }

    void updateManagedTick(RuntimePerformancePolicy policy) {
        drainCollectedOwners();
        tick++;
        mode = policy.mode();
        evaluated = skipped = paused = 0;
        for (var entry : cadences.entrySet()) {
            AnimationState state = entry.getKey();
            if (!state.isInitialized()) continue;
            if(state.getModelInstance()!=null) state.getModelInstance().prepareTick();
            int assetInterval = state.getPerformanceSettings().updateIntervalTicks();
            float seconds = entry.getValue().step(tick, .05f, policy, assetInterval, state.isPaused());
            if (state.isPaused()) { paused++; continue; }
            if (seconds == 0) { skipped++; totalSkipped++; continue; }
            state.update(seconds);
            evaluated++;
            totalEvaluated++;
        }
    }

    public PerformanceStatistics getPerformanceStatistics() {
        return new PerformanceStatistics(mode, cadences.size(), evaluated, skipped, paused, totalEvaluated, totalSkipped);
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

    private void updateStates(Map<String, AnimationState> states, float deltaTime) {
        for (AnimationState state : states.values()) {
            AnimationCadence cadence = cadences.get(state);
            if (cadence != null) cadence.resetTime();
            if (state.isInitialized()) state.update(deltaTime);
        }
    }

    public void release(Object owner) {
        drainCollectedOwners();
        if (owner == null) return;
        Map<String, AnimationState> removed = instances.remove(new OwnerReference(owner, null));
        if (removed != null) removed.values().forEach(this::closeState);
    }

    private void drainCollectedOwners() {
        OwnerReference owner;
        while ((owner = (OwnerReference) collectedOwners.poll()) != null) {
            Map<String, AnimationState> removed = instances.remove(owner);
            if (removed != null) removed.values().forEach(this::closeState);
        }
    }

    public void clear() {
        drainCollectedOwners();
        animationStates.values().forEach(this::closeState);
        instances.values().forEach(states -> states.values().forEach(this::closeState));
        animationStates.clear();
        instances.clear();
        invalidDefinitions.clear();
        cadences.clear();
        tick = nextPhase = evaluated = skipped = paused = 0;
        totalEvaluated = totalSkipped = 0;
    }

    private void closeState(AnimationState state) { cadences.remove(state); state.close(); }

    public static void clearCache() { INSTANCE.clear(); }
    public AnimationState getAnimationState(String modelName) { return animationStates.get(modelName); }
}
