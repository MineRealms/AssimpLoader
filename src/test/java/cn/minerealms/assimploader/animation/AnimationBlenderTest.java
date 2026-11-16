package cn.minerealms.assimploader.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationBlenderTest {
    @Test
    void blendsRotatedReflectionsWithNonUniformScaleWithoutChangingInputs() {
        Quaternionf fromRotation = new Quaternionf().rotateXYZ(.2f, -.4f, .1f);
        Quaternionf toRotation = new Quaternionf().rotateXYZ(.8f, .3f, -.2f);
        Matrix4f from = new Matrix4f().translationRotateScale(new Vector3f(1, 2, 3),
                fromRotation, new Vector3f(-2, 3, 4));
        Matrix4f to = new Matrix4f().translationRotateScale(new Vector3f(9, 6, -1),
                toRotation, new Vector3f(-6, 7, 8));
        Matrix4f originalFrom = new Matrix4f(from), originalTo = new Matrix4f(to);
        Matrix4f expected = new Matrix4f().translationRotateScale(new Vector3f(3, 3, 2),
                new Quaternionf(fromRotation).slerp(toRotation, .25f), new Vector3f(-3, 4, 5));

        Matrix4f blended = AnimationBlender.blendTransforms(from, to, .25f);

        assertMatrix(expected, blended);
        assertTrue(blended.determinant() < 0);
        assertEquals(originalFrom, from);
        assertEquals(originalTo, to);
    }

    @Test
    void canonicalReflectionAlsoPreservesNegativeYScale() {
        Matrix4f from = new Matrix4f().rotateZ(.2f).scale(2, -3, 4);
        Matrix4f to = new Matrix4f().rotateZ(.8f).scale(6, -7, 8);

        Matrix4f blended = AnimationBlender.blendTransforms(from, to, .25f);

        assertMatrix(new Matrix4f().rotateZ(.35f).scale(3, -4, 5), blended);
        assertTrue(blended.determinant() < 0);
    }

    @Test
    void oppositeParitiesInterpolateTheSignedScale() {
        Quaternionf rotation = new Quaternionf().rotateXYZ(.3f, -.6f, .2f);
        Matrix4f from = new Matrix4f().rotation(rotation).scale(-2, 3, 4);
        Matrix4f to = new Matrix4f().rotation(rotation).scale(6, 7, 8);

        assertMatrix(new Matrix4f().rotation(rotation).scale(2, 5, 6),
                AnimationBlender.blendTransforms(from, to, .5f));
    }

    @Test
    void endpointsAndEqualMatricesKeepShearAndReflectionExactly() {
        Matrix4f from = new Matrix4f().translation(1, 2, 3).scale(-2, 3, 4).m10(.35f);
        Matrix4f to = new Matrix4f().translation(5, 6, 7).scale(8, -9, 10).m21(-.4f);

        Matrix4f start = AnimationBlender.blendTransforms(from, to, 0);
        Matrix4f end = AnimationBlender.blendTransforms(from, to, 1);
        Matrix4f equal = AnimationBlender.blendTransforms(from, new Matrix4f(from), .4f);

        assertEquals(from, start);
        assertEquals(to, end);
        assertEquals(from, equal);
        assertNotSame(from, start);
        assertNotSame(to, end);
        assertNotSame(from, equal);
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        assertTrue(expected.equals(actual, 1e-5f), () -> "Expected " + expected + " but was " + actual);
    }
}
