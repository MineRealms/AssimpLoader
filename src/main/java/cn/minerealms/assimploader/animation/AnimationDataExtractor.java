package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.assimp.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts animation data from Assimp AIAnimation.
 *
 * Extracts:
 * - Position keyframes (translation)
 * - Rotation keyframes (quaternion)
 * - Scale keyframes
 *
 * Applies coordinate system conversion to all keyframes.
 *
 * References:
 * - https://learnopengl.com/Guest-Articles/2020/Skeletal-Animation
 * - https://github.com/assimp/assimp/blob/master/include/assimp/anim.h
 */
public class AnimationDataExtractor {

    /**
     * Extract animation channels from AIAnimation
     */
    public static List<BoneAnimationChannel> extractChannels(
            AIAnimation aiAnimation,
            CoordinateSystemConverter.CoordinateSystem sourceSystem) {

        List<BoneAnimationChannel> channels = new ArrayList<>();

        int numChannels = aiAnimation.mNumChannels();
        DebugManager.debug(DebugCategory.ANIMATION, "Extracting {} animation channels", numChannels);

        for (int i = 0; i < numChannels; i++) {
            AINodeAnim aiNodeAnim = AINodeAnim.create(aiAnimation.mChannels().get(i));
            String boneName = aiNodeAnim.mNodeName().dataString();

            // Extract keyframes
            Keyframe<Vector3f>[] positionKeys = extractPositionKeys(aiNodeAnim, sourceSystem);
            Keyframe<Quaternionf>[] rotationKeys = extractRotationKeys(aiNodeAnim, sourceSystem);
            Keyframe<Vector3f>[] scaleKeys = extractScaleKeys(aiNodeAnim, sourceSystem);

            BoneAnimationChannel channel = new BoneAnimationChannel(
                boneName,
                positionKeys,
                rotationKeys,
                scaleKeys
            );

            channels.add(channel);

            DebugManager.debug(DebugCategory.ANIMATION, "  Channel: {} ({} pos, {} rot, {} scale keys)",
                boneName,
                positionKeys.length,
                rotationKeys.length,
                scaleKeys.length);
        }

        return channels;
    }

    /**
     * Extract position keyframes
     */
    @SuppressWarnings("unchecked")
    private static Keyframe<Vector3f>[] extractPositionKeys(
            AINodeAnim aiNodeAnim,
            CoordinateSystemConverter.CoordinateSystem sourceSystem) {

        int numKeys = aiNodeAnim.mNumPositionKeys();
        Keyframe<Vector3f>[] keys = new Keyframe[numKeys];

        AIVectorKey.Buffer positionKeys = aiNodeAnim.mPositionKeys();

        for (int i = 0; i < numKeys; i++) {
            AIVectorKey key = positionKeys.get(i);
            double time = key.mTime();

            // Extract position
            AIVector3D aiPos = key.mValue();
            Vector3f position = new Vector3f(aiPos.x(), aiPos.y(), aiPos.z());

            // Apply coordinate system conversion
            position = CoordinateSystemConverter.convertPosition(
                position,
                sourceSystem,
                CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED
            );

            keys[i] = new Keyframe<>(time, position);
        }

        return keys;
    }

    /**
     * Extract rotation keyframes (quaternions)
     */
    @SuppressWarnings("unchecked")
    private static Keyframe<Quaternionf>[] extractRotationKeys(
            AINodeAnim aiNodeAnim,
            CoordinateSystemConverter.CoordinateSystem sourceSystem) {

        int numKeys = aiNodeAnim.mNumRotationKeys();
        Keyframe<Quaternionf>[] keys = new Keyframe[numKeys];

        AIQuatKey.Buffer rotationKeys = aiNodeAnim.mRotationKeys();

        for (int i = 0; i < numKeys; i++) {
            AIQuatKey key = rotationKeys.get(i);
            double time = key.mTime();

            // Extract quaternion
            AIQuaternion aiQuat = key.mValue();
            Quaternionf rotation = new Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w());

            // Apply coordinate system conversion
            rotation = CoordinateSystemConverter.convertRotation(
                rotation,
                sourceSystem,
                CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED
            );

            keys[i] = new Keyframe<>(time, rotation);
        }

        return keys;
    }

    /**
     * Extract scale keyframes
     */
    @SuppressWarnings("unchecked")
    private static Keyframe<Vector3f>[] extractScaleKeys(
            AINodeAnim aiNodeAnim,
            CoordinateSystemConverter.CoordinateSystem sourceSystem) {

        int numKeys = aiNodeAnim.mNumScalingKeys();
        Keyframe<Vector3f>[] keys = new Keyframe[numKeys];

        AIVectorKey.Buffer scaleKeys = aiNodeAnim.mScalingKeys();

        for (int i = 0; i < numKeys; i++) {
            AIVectorKey key = scaleKeys.get(i);
            double time = key.mTime();

            // Extract scale
            AIVector3D aiScale = key.mValue();
            Vector3f scale = new Vector3f(aiScale.x(), aiScale.y(), aiScale.z());

            // Scale doesn't need coordinate system conversion
            // (it's uniform in all coordinate systems)

            keys[i] = new Keyframe<>(time, scale);
        }

        return keys;
    }

    /**
     * Get animation duration in seconds
     */
    public static double getAnimationDuration(AIAnimation aiAnimation) {
        double duration = aiAnimation.mDuration();
        double ticksPerSecond = aiAnimation.mTicksPerSecond();

        if (ticksPerSecond == 0) {
            ticksPerSecond = 25.0; // Default FBX frame rate
        }

        return duration / ticksPerSecond;
    }

    /**
     * Get animation ticks per second
     */
    public static double getTicksPerSecond(AIAnimation aiAnimation) {
        double ticksPerSecond = aiAnimation.mTicksPerSecond();
        return ticksPerSecond != 0 ? ticksPerSecond : 25.0;
    }

    /**
     * Check if animation has any channels
     */
    public static boolean hasChannels(AIAnimation aiAnimation) {
        return aiAnimation.mNumChannels() > 0;
    }

    /**
     * Get channel count
     */
    public static int getChannelCount(AIAnimation aiAnimation) {
        return aiAnimation.mNumChannels();
    }
}
