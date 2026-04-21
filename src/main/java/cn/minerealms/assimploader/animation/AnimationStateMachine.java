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
    private AnimationState currentState;
    private AnimationState previousState;

    private double transitionTime = 0.0;
    private double transitionDuration = 0.3; // 300ms default blend time

    public AnimationStateMachine() {
        this.states = new HashMap<>();
    }

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
        transitionTo(stateName, 0.3);
    }

    /**
     * Update current animation state
     */
    public void update(double deltaTime) {
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
