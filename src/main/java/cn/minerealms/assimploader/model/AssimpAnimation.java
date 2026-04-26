package cn.minerealms.assimploader.model;

import org.lwjgl.assimp.AIAnimation;


/**
 * Wrapper for Assimp AIAnimation containing animation data.
 *
 * Phase 4 implementation - skeletal animation system
 */
public class AssimpAnimation {

    private final AIAnimation aiAnimation;
    private final int index;
    private final String name;
    private final double duration;
    private final double ticksPerSecond;

    public AssimpAnimation(AIAnimation aiAnimation, int index) {
        this.aiAnimation = aiAnimation;
        this.index = index;
        this.name = aiAnimation.mName().dataString();
        this.duration = aiAnimation.mDuration();
        this.ticksPerSecond = aiAnimation.mTicksPerSecond();
    }

    public String getName() {
        return name;
    }

    public double getDuration() {
        return duration;
    }

    public double getTicksPerSecond() {
        return ticksPerSecond != 0 ? ticksPerSecond : 25.0;
    }

    public int getIndex() {
        return index;
    }

    public AIAnimation getNativeAnimation() {
        return aiAnimation;
    }
}
