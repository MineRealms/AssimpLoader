package cn.minerealms.assimploader.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Animation keyframe for position, rotation, or scale.
 * Keyframes define the state of a bone at a specific time.
 * Interpolation between keyframes creates smooth animation.
 */
public class Keyframe<T> {

    /**
     * Pair of keyframes surrounding a given time point
     */
    public record KeyframePair<T>(Keyframe<T> before, Keyframe<T> after) {}

    private final double time;
    private final T value;

    public Keyframe(double time, T value) {
        this.time = time;
        this.value = value;
    }

    public double getTime() {
        return time;
    }

    public T getValue() {
        return value;
    }

    /**
     * Linear interpolation between two Vector3f keyframes
     */
    private static Vector3f interpolateVector3f(Keyframe<Vector3f> k1, Keyframe<Vector3f> k2, double time) {
        double delta = k2.time - k1.time;
        double factor = (time - k1.time) / delta;

        Vector3f result = new Vector3f();
        k1.value.lerp(k2.value, (float) factor, result);
        return result;
    }

    /**
     * Linear interpolation between two position keyframes
     */
    public static Vector3f interpolatePosition(Keyframe<Vector3f> k1, Keyframe<Vector3f> k2, double time) {
        return interpolateVector3f(k1, k2, time);
    }

    /**
     * Spherical linear interpolation between two rotation keyframes
     */
    public static Quaternionf interpolateRotation(Keyframe<Quaternionf> k1, Keyframe<Quaternionf> k2, double time) {
        double delta = k2.time - k1.time;
        double factor = (time - k1.time) / delta;

        Quaternionf result = new Quaternionf();
        k1.value.slerp(k2.value, (float) factor, result);
        return result;
    }

    /**
     * Linear interpolation between two scale keyframes
     */
    public static Vector3f interpolateScale(Keyframe<Vector3f> k1, Keyframe<Vector3f> k2, double time) {
        return interpolateVector3f(k1, k2, time);
    }

    /**
     * Find the two keyframes that surround the given time
     *
     * @return KeyframePair of [before, after] keyframes, or null if time is out of range
     */
    public static <T> KeyframePair<T> findSurroundingKeyframes(Keyframe<T>[] keyframes, double time) {
        if (keyframes.length == 0) {
            return null;
        }

        // Before first keyframe
        if (time <= keyframes[0].time) {
            return new KeyframePair<>(keyframes[0], keyframes[0]);
        }

        // After last keyframe
        if (time >= keyframes[keyframes.length - 1].time) {
            return new KeyframePair<>(keyframes[keyframes.length - 1], keyframes[keyframes.length - 1]);
        }

        // Find surrounding keyframes
        for (int i = 0; i < keyframes.length - 1; i++) {
            if (time >= keyframes[i].time && time < keyframes[i + 1].time) {
                return new KeyframePair<>(keyframes[i], keyframes[i + 1]);
            }
        }

        return null;
    }
}
