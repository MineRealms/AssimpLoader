package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.animation.AnimationDataExtractor;
import cn.minerealms.assimploader.animation.BoneAnimationChannel;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.lwjgl.assimp.AIAnimation;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of one animation clip.
 *
 * <p>The keyframes are copied out of native memory at construction time (coordinate system
 * conversion included), so the clip stays playable after the {@code aiScene} is released.
 */
public class AssimpAnimation {

    private final int index;
    private final String name;
    private final double duration;
    private final double ticksPerSecond;
    private final List<BoneAnimationChannel> channels;

    public AssimpAnimation(AIAnimation aiAnimation, int index,
                           CoordinateSystemConverter.CoordinateSystem sourceSystem) {
        this.index = index;
        this.name = aiAnimation.mName().dataString();
        this.duration = aiAnimation.mDuration();
        this.ticksPerSecond = aiAnimation.mTicksPerSecond() != 0 ? aiAnimation.mTicksPerSecond() : 25.0;
        this.channels = Collections.unmodifiableList(
                AnimationDataExtractor.extractChannels(aiAnimation, sourceSystem));
    }

    public String getName() {
        return name;
    }

    /** Duration in seconds. */
    public double getDuration() {
        return duration;
    }

    public double getTicksPerSecond() {
        return ticksPerSecond;
    }

    public int getIndex() {
        return index;
    }

    public List<BoneAnimationChannel> getChannels() {
        return channels;
    }
}
