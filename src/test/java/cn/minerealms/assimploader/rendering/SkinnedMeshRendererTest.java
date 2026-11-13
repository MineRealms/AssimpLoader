package cn.minerealms.assimploader.rendering;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkinnedMeshRendererTest {
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
