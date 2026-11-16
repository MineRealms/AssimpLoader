package cn.minerealms.assimploader.rendering;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SkinnedMeshRendererTest {
    @Test
    void packsSnapshotPaletteInConsecutiveColumnMajorMatricesWithoutChangingIt() {
        Matrix4f first = new Matrix4f().translation(1, 2, 3).rotateY(0.5f);
        Matrix4f second = new Matrix4f().translation(4, 5, 6).scale(2, 3, 4);
        Matrix4f originalFirst = new Matrix4f(first);
        Matrix4f originalSecond = new Matrix4f(second);

        float[] packed = SkinnedMeshRenderer.packPalette(new Matrix4f[]{first, second});

        assertEquals(32, packed.length);
        assertArrayEquals(first.get(new float[16]), Arrays.copyOfRange(packed, 0, 16), 1e-6f);
        assertArrayEquals(second.get(new float[16]), Arrays.copyOfRange(packed, 16, 32), 1e-6f);
        assertEquals(originalFirst, first);
        assertEquals(originalSecond, second);
    }

    @Test
    void composesPoseThenGlobalViewThenProjectionWithoutMutatingInputs() {
        Matrix4f pose = new Matrix4f().translation(1, 2, 3).rotateZ((float) (Math.PI / 2));
        Matrix4f view = new Matrix4f().translation(10, 20, 30);
        Matrix4f projection = new Matrix4f().scaling(2, 3, 4);
        Matrix4f originalPose = new Matrix4f(pose);
        Matrix4f originalView = new Matrix4f(view);
        Matrix4f originalProjection = new Matrix4f(projection);

        Matrix4f modelView = SkinnedMeshRenderer.createModelView(view, pose);
        Matrix4f mvp = SkinnedMeshRenderer.createModelViewProjection(projection, modelView);
        Vector4f position = mvp.transform(new Vector4f(1, 0, 0, 1));

        assertEquals(22, position.x, 1.0e-5f);
        assertEquals(69, position.y, 1.0e-5f);
        assertEquals(132, position.z, 1.0e-5f);
        assertEquals(1, position.w, 1.0e-5f);
        assertEquals(originalPose, pose);
        assertEquals(originalView, view);
        assertEquals(originalProjection, projection);
    }
}
