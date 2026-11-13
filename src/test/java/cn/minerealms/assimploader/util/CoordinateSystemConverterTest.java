package cn.minerealms.assimploader.util;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.system.MemoryStack;

import static cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateSystemConverterTest {
    private static final float EPSILON = 1e-5f;

    @Test
    void preservesExistingPositionConventions() {
        Vector3f point = new Vector3f(2, 3, 4);
        assertVector(new Vector3f(-2, 3, 4), CoordinateSystemConverter.convertPosition(
                point, FBX_RIGHT_HANDED, MINECRAFT_LEFT_HANDED));
        assertVector(new Vector3f(2, 3, -4), CoordinateSystemConverter.convertPosition(
                point, OPENGL_RIGHT_HANDED, MINECRAFT_LEFT_HANDED));
        assertVector(new Vector3f(2, 4, 3), CoordinateSystemConverter.convertPosition(
                point, Z_UP_RIGHT_HANDED, MINECRAFT_LEFT_HANDED));
        assertVector(new Vector3f(2, 3, 4), point);
    }

    @Test
    void quarterTurnRemainsAQuarterTurnAfterHandednessChange() {
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.PI / 2);
        Quaternionf converted = CoordinateSystemConverter.convertRotation(
                rotation, OPENGL_RIGHT_HANDED, MINECRAFT_LEFT_HANDED);
        assertVector(new Vector3f(0, 0, 1), converted.transform(new Vector3f(1, 0, 0)));
    }

    @Test
    void convertedRotationsActOnConvertedVectorsInEveryBasis() {
        Quaternionf rotation = new Quaternionf().rotationXYZ(.31f, -.68f, 1.12f);
        Vector3f vector = new Vector3f(2, -3, 4);
        for (var from : CoordinateSystemConverter.CoordinateSystem.values()) {
            for (var to : CoordinateSystemConverter.CoordinateSystem.values()) {
                Vector3f expected = CoordinateSystemConverter.convertDirection(
                        rotation.transform(new Vector3f(vector)), from, to);
                Vector3f actual = CoordinateSystemConverter.convertRotation(rotation, from, to)
                        .transform(CoordinateSystemConverter.convertDirection(vector, from, to));
                assertVector(expected, actual);
            }
        }
    }

    @Test
    void convertedTransformsAndPositionsUseTheSameBasis() {
        Matrix4f transform = new Matrix4f().translation(2, -3, 4)
                .rotateXYZ(.3f, -.7f, .2f).scale(2, 3, 4);
        Vector3f point = new Vector3f(-1, 2, 3);
        for (var from : CoordinateSystemConverter.CoordinateSystem.values()) {
            for (var to : CoordinateSystemConverter.CoordinateSystem.values()) {
                Vector3f expected = CoordinateSystemConverter.convertPosition(
                        transform.transformPosition(new Vector3f(point)), from, to);
                Vector3f actual = CoordinateSystemConverter.convertMatrix(transform, from, to)
                        .transformPosition(CoordinateSystemConverter.convertPosition(point, from, to));
                assertVector(expected, actual);
            }
        }
    }

    @Test
    void copiesAssimpTranslationIntoJomlTranslationColumn() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIMatrix4x4 source = AIMatrix4x4.calloc(stack);
            source.a1(1).b2(1).c3(1).d4(1).a4(2).b4(3).c4(4);
            Matrix4f copied = CoordinateSystemConverter.convertMatrix(
                    source, OPENGL_RIGHT_HANDED, MINECRAFT_LEFT_HANDED);
            assertVector(new Vector3f(2, 3, -4), copied.transformPosition(new Vector3f()));
            assertEquals(0, copied.m03(), EPSILON);
            assertEquals(0, copied.m13(), EPSILON);
            assertEquals(0, copied.m23(), EPSILON);
        }
    }

    @Test
    void conversionRoundTripsWithoutMutatingCachedMatrices() {
        Vector3f point = new Vector3f(2, 3, 4);
        for (var from : CoordinateSystemConverter.CoordinateSystem.values()) {
            for (var to : CoordinateSystemConverter.CoordinateSystem.values()) {
                CoordinateSystemConverter.getConversionMatrix(from, to).zero();
                Vector3f converted = CoordinateSystemConverter.convertPosition(point, from, to);
                assertVector(point, CoordinateSystemConverter.convertPosition(converted, to, from));
            }
        }
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
