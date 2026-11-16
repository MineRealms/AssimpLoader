package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpAnimation;

import java.util.HashMap;
import java.util.Map;

/**
 * Animation state machine for managing multiple animations.
 *
 * Handles:
 * - Animation transitions with blending
 * - State-based animation playback
 * - Priority system for animation layers
 *
 * Example states: idle, walk, run, jump, attack
 */
public class AnimationStateMachine {

    private final Map<String, AnimationState> states;
    private final cn.minerealms.assimploader.animation.AnimationState instance;
    private final Map<String, StateBinding> bindings = new java.util.LinkedHashMap<>();
    private final Map<String, Double> transitions = new HashMap<>();
    private String currentStateName;
    public record StateBinding(String clip, boolean looping, float speed, double blendSeconds) {}
    private AnimationState currentState;
    private AnimationState previousState;

    private double transitionTime = 0.0;
    private double transitionDuration = 0.3; // 300ms default blend time

    public AnimationStateMachine() {
        this(null);
    }

    /** Bound mode drives the same instance consumed by renderers; its owner still advances time. */
    public AnimationStateMachine(cn.minerealms.assimploader.animation.AnimationState instance) {
        this.states = new HashMap<>();
        this.instance = instance;
    }

    public void addState(String name, String clip, boolean looping, float speed, double blendSeconds) {
        if (instance == null) throw new IllegalStateException("Clip-name bindings require an instance");
        if (!Float.isFinite(speed) || !Double.isFinite(blendSeconds) || blendSeconds < 0) {
            throw new IllegalArgumentException("Invalid state playback options");
        }
        if (!java.util.Arrays.asList(instance.getAvailableAnimations()).contains(clip)) {
            throw new IllegalArgumentException("Unknown state clip: " + clip);
        }
        bindings.put(java.util.Objects.requireNonNull(name), new StateBinding(clip, looping, speed, blendSeconds));
    }

    public void addTransition(String from, String to, double seconds) {
        if (!bindings.containsKey(from) || !bindings.containsKey(to) || !Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Invalid state transition");
        }
        transitions.put(from + "\u0000" + to, seconds);
    }

    public boolean request(String name) {
        StateBinding binding = bindings.get(name);
        if (binding == null) return false;
        if (name.equals(currentStateName) && instance.getCurrentAnimation() != null
                && instance.getCurrentAnimation().getAnimation().getName().equals(binding.clip())) return true;
        double duration = currentStateName == null ? 0
                : transitions.getOrDefault(currentStateName + "\u0000" + name, binding.blendSeconds());
        transitionTo(name, duration);
        return true;
    }

    public String getCurrentStateName() { return currentStateName; }
    void resetSelection() { currentStateName = null; }
    public Map<String, StateBinding> getBindings() { return java.util.Collections.unmodifiableMap(bindings); }

    /**
     * Register an animation state
     */
    public void addState(String name, AssimpAnimation animation, boolean looping) {
        AnimationState state = new AnimationState(name, animation, looping);
        states.put(name, state);
    }

    /**
     * Transition to a new animation state
     *
     * @param stateName Name of the target state
     * @param blendTime Time to blend between animations (seconds)
     */
    public void transitionTo(String stateName, double blendTime) {
        if (instance != null) {
            if (!Double.isFinite(blendTime) || blendTime < 0) throw new IllegalArgumentException("Invalid transition duration");
            StateBinding binding = bindings.get(stateName);
            if (binding != null && instance.transitionTo(binding.clip(), blendTime)) {
                instance.getCurrentAnimation().setLooping(binding.looping());
                instance.getCurrentAnimation().setSpeed(binding.speed());
                currentStateName = stateName;
            }
            return;
        }
        AnimationState newState = states.get(stateName);
        if (newState == null) {
            return;
        }

        if (currentState != null) {
            previousState = currentState;
            currentState.pause();
        }

        currentState = newState;
        currentState.play();

        transitionTime = 0.0;
        transitionDuration = blendTime;
    }

    /**
     * Transition to a new state with default blend time
     */
    public void transitionTo(String stateName) {
        if (instance != null) { request(stateName); return; }
        transitionTo(stateName, 0.3);
    }

    /**
     * Update current animation state
     */
    public void update(double deltaTime) {
        if (instance != null) {
            if (!Double.isFinite(deltaTime) || deltaTime < 0 || deltaTime > Float.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid animation delta");
            }
            instance.update((float) deltaTime);
            return;
        }
        if (currentState == null) {
            return;
        }

        // Update transition blending
        if (previousState != null && transitionTime < transitionDuration) {
            transitionTime += deltaTime;
            previousState.update(deltaTime);

            if (transitionTime >= transitionDuration) {
                previousState = null;
            }
        }

        // Update current state
        currentState.update(deltaTime);
    }

    /**
     * Get blend factor for current transition (0 = previous, 1 = current)
     */
    public float getBlendFactor() {
        if (instance != null) return instance.isTransitioning() ? instance.getTransitionWeight() : 1;
        if (previousState == null || transitionTime >= transitionDuration) {
            return 1.0f;
        }
        return (float) (transitionTime / transitionDuration);
    }

    public AnimationState getCurrentState() {
        return currentState;
    }

    public AnimationState getPreviousState() {
        return previousState;
    }

    /**
     * Animation state wrapper
     */
    public static class AnimationState {
        private final String name;
        private final AnimationPlayer player;

        public AnimationState(String name, AssimpAnimation animation, boolean looping) {
            this.name = name;
            this.player = new AnimationPlayer(animation);
            this.player.setLooping(looping);
        }

        public void play() {
            player.play();
        }

        public void pause() {
            player.pause();
        }

        public void stop() {
            player.stop();
        }

        public void update(double deltaTime) {
            player.update(deltaTime);
        }

        public String getName() {
            return name;
        }

        public AnimationPlayer getPlayer() {
            return player;
        }
    }
}
