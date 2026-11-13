package cn.minerealms.assimploader.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyframeTest {
    @Test
    void constantChannelRemainsFiniteBeforeAtAndAfterItsOnlyKey() {
        Keyframe<Vector3f> position = new Keyframe<>(0, new Vector3f(2, 3, 4));
        Keyframe<Quaternionf> rotation = new Keyframe<>(0, new Quaternionf().rotationY(.7f));
        Keyframe<Vector3f> scale = new Keyframe<>(0, new Vector3f(1, 2, 3));
        BoneAnimationChannel channel = new BoneAnimationChannel("constant",
                vectorKeys(position), rotationKeys(rotation), vectorKeys(scale));

        for (double time : new double[]{-1, 0, .5, 10}) {
            assertVector(position.getValue(), channel.getPosition(time));
            assertVector(scale.getValue(), channel.getScale(time));
            Quaternionf actual = channel.getRotation(time);
            assertTrue(actual.isFinite());
            assertEquals(1, Math.abs(rotation.getValue().dot(actual)), 1e-5f);
        }
    }

    @Test
    void multiKeyChannelClampsToEndpointsAndInterpolatesInterior() {
        BoneAnimationChannel channel = new BoneAnimationChannel("moving",
                vectorKeys(new Keyframe<>(2, new Vector3f(2, 3, 4)),
                        new Keyframe<>(4, new Vector3f(4, 5, 6))), null, null);
        assertVector(new Vector3f(2, 3, 4), channel.getPosition(-1));
        assertVector(new Vector3f(2, 3, 4), channel.getPosition(2));
        assertVector(new Vector3f(3, 4, 5), channel.getPosition(3));
        assertVector(new Vector3f(4, 5, 6), channel.getPosition(4));
        assertVector(new Vector3f(4, 5, 6), channel.getPosition(20));
    }

    @Test
    void coincidentKeyTimesHaveAStableFiniteValue() {
        Keyframe<Vector3f> first = new Keyframe<>(1, new Vector3f(2, 3, 4));
        Keyframe<Vector3f> second = new Keyframe<>(1, new Vector3f(4, 5, 6));
        assertVector(first.getValue(), Keyframe.interpolatePosition(first, second, 1));
        assertVector(new Vector3f(2, 3, 4), first.getValue());
    }

    @SafeVarargs
    private static Keyframe<Vector3f>[] vectorKeys(Keyframe<Vector3f>... keys) {
        return keys;
    }

    @SafeVarargs
    private static Keyframe<Quaternionf>[] rotationKeys(Keyframe<Quaternionf>... keys) {
        return keys;
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertTrue(actual.isFinite());
        assertEquals(expected.x, actual.x, 1e-5f);
        assertEquals(expected.y, actual.y, 1e-5f);
        assertEquals(expected.z, actual.z, 1e-5f);
    }
}
