package cn.minerealms.assimploader.render;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

class BoneMatrixTBOTest {
    @Test
    void packsEachBoneIntoItsOwnColumnMajorBlock() {
        Matrix4f[] matrices = {
                new Matrix4f().translation(1, 2, 3),
                new Matrix4f().translation(4, 5, 6).scale(2)
        };
        FloatBuffer target = BufferUtils.createFloatBuffer(48);

        BoneMatrixTBO.packMatrices(matrices, target);

        assertEquals(0, target.position());
        assertEquals(32, target.remaining());
        assertEquals(1, target.get(0));
        assertEquals(1, target.get(12));
        assertEquals(2, target.get(13));
        assertEquals(3, target.get(14));
        assertEquals(2, target.get(16));
        assertEquals(4, target.get(28));
        assertEquals(5, target.get(29));
        assertEquals(6, target.get(30));
        assertEquals(1, target.get(31));
    }

    @Test
    void reusingCapacityForFewerBonesLimitsTheUpload() {
        FloatBuffer target = BufferUtils.createFloatBuffer(32);
        BoneMatrixTBO.packMatrices(new Matrix4f[]{new Matrix4f(), new Matrix4f()}, target);
        target.position(8);

        BoneMatrixTBO.packMatrices(new Matrix4f[]{new Matrix4f().translation(7, 8, 9)}, target);

        assertEquals(0, target.position());
        assertEquals(16, target.remaining());
        assertEquals(7, target.get(12));
        assertEquals(8, target.get(13));
        assertEquals(9, target.get(14));
    }
}
