package cn.minerealms.assimploader.rendering;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CpuSkinningTest {
    @Test
    void weightedPalettePreservesTranslationAndUnweightedVertices() {
        var result = CpuSkinning.deform(new float[]{1, 2, 3, 4, 5, 6}, null,
                new int[]{0, 1, -1, -1}, new float[]{0.25f, 0.75f, 0, 0},
                new Matrix4f[]{new Matrix4f().translation(4, 0, 0), new Matrix4f().translation(0, 8, 0)});
        assertArrayEquals(new float[]{2, 8, 3, 4, 5, 6}, result.positions(), 1.0e-6f);
        for (float value : result.normals()) assertTrue(Float.isFinite(value));
    }
}
