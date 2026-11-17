package cn.minerealms.assimploader.client.transform;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EditableTransformTest {
    @Test void eulerUsesJomlXyzOrderAndReturnsFreshNormalizedQuaternions() {
        var value = new EditableTransform(.1f, -.2f, .3f, 25, -60, 135, .7f);
        var expected = new Quaternionf().rotateX((float) Math.toRadians(25)).rotateY((float) Math.toRadians(-60))
                .rotateZ((float) Math.toRadians(135));
        assertTrue(Math.abs(expected.dot(value.quaternion())) > 1 - 1e-6);
        assertEquals(1, value.quaternion().lengthSquared(), 1e-6);
        value.quaternion().identity();
        assertTrue(Math.abs(expected.dot(value.quaternion())) > 1 - 1e-6);
    }

    @Test void quaternionRoundTripHandlesGimbalLockAntipodesAndRealMountRanges() {
        for (float y : new float[]{-90, -89.9999f, -45, 0, 89.9999f, 90})
            for (float x : new float[]{-170, -90, 25, 140}) for (float z : new float[]{-135, 0, 70, 180})
                roundTrip(new EditableTransform(.12f, -.3f, .015f, x, y, z, .75f).quaternion());
        Random random = new Random(81723);
        for (int i = 0; i < 1000; i++) roundTrip(new Quaternionf().rotationXYZ(random.nextFloat() * 12 - 6,
                random.nextFloat() * 12 - 6, random.nextFloat() * 12 - 6));
        roundTrip(new Quaternionf(-.001f, .707f, .04f, .707f).normalize().mul(-3));
    }

    private static void roundTrip(Quaternionf rotation) {
        var saved = new Quaternionf(rotation);
        var value = EditableTransform.from(.1f, .2f, .3f, rotation, .4f);
        assertTrue(Math.abs(new Quaternionf(rotation).normalize().dot(value.quaternion())) > 1 - 1e-6,
                () -> "XYZ round-trip lost mount orientation: " + rotation + " -> " + value);
        assertEquals(saved, rotation);
        assertEquals(.1f, value.x()); assertEquals(.2f, value.y()); assertEquals(.3f, value.z()); assertEquals(.4f, value.scale());
    }

    @Test void rejectsInvalidOffsetsAnglesScaleAndQuaternions() {
        assertThrows(IllegalArgumentException.class, () -> new EditableTransform(Float.NaN, 0, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EditableTransform(10.01f, 0, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EditableTransform(0, 0, 0, Float.POSITIVE_INFINITY, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EditableTransform(0, 0, 0, 0, -3601, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new EditableTransform(0, 0, 0, 0, 0, 0, .0009f));
        assertThrows(IllegalArgumentException.class, () -> new EditableTransform(0, 0, 0, 0, 0, 0, 10.01f));
        assertThrows(IllegalArgumentException.class, () -> EditableTransform.from(0, 0, 0, new Quaternionf(0, 0, 0, 0), 1));
        assertThrows(IllegalArgumentException.class, () -> EditableTransform.from(0, 0, 0, new Quaternionf(Float.NaN, 0, 0, 1), 1));
    }
}
