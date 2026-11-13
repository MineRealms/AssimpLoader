package cn.minerealms.assimploader.rendering;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class SkinnedMeshVBOTest {
    @Test
    void missingNormalsAndUvsUseDefaultsAndOneInfluenceIsPadded() {
        ByteBuffer buffer = SkinnedMeshVBO.createInterleavedBuffer(
                new float[]{1, 2, 3, 4, 5, 6}, null, null,
                new int[]{7, 9}, new float[]{0.25f, 0.5f});

        assertEquals(2 * SkinnedMeshVBO.VERTEX_STRIDE, buffer.remaining());
        assertEquals(1, buffer.getFloat(0));
        assertEquals(4, buffer.getFloat(SkinnedMeshVBO.VERTEX_STRIDE));
        for (int vertex = 0; vertex < 2; vertex++) {
            int offset = vertex * SkinnedMeshVBO.VERTEX_STRIDE;
            assertEquals(0, buffer.getFloat(offset + 12));
            assertEquals(1, buffer.getFloat(offset + 16));
            assertEquals(0, buffer.getFloat(offset + 20));
            assertEquals(0, buffer.getFloat(offset + 24));
            assertEquals(0, buffer.getFloat(offset + 28));
            assertInfluences(buffer, vertex, new int[]{vertex == 0 ? 7 : 9, -1, -1, -1},
                    new float[]{1, 0, 0, 0});
        }
    }

    @Test
    void eightInfluenceSourceUsesItsOwnStrideAndRenormalizesRetainedFour() {
        ByteBuffer buffer = SkinnedMeshVBO.createInterleavedBuffer(
                new float[]{1, 2, 3, 4, 5, 6}, new float[]{0, 0, 1, 1, 0, 0},
                new float[]{0.25f, 0.5f, 0.75f, 1},
                new int[]{0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 17},
                new float[]{0.05f, 0.10f, 0.15f, 0.20f, 0.1f, 0.1f, 0.1f, 0.2f,
                        0.2f, 0.15f, 0.1f, 0.05f, 0.1f, 0.1f, 0.1f, 0.2f});

        assertInfluences(buffer, 0, new int[]{3, 7, 2, 1}, new float[]{4f/13, 4f/13, 3f/13, 2f/13});
        assertInfluences(buffer, 1, new int[]{10, 17, 11, 12}, new float[]{4f/13, 4f/13, 3f/13, 2f/13});
        assertEquals(1, buffer.getFloat(20));
        assertEquals(0.25f, buffer.getFloat(24));
        assertEquals(0.75f, buffer.getFloat(SkinnedMeshVBO.VERTEX_STRIDE + 24));
    }

    @Test
    void unweightedVerticesKeepTheShaderIdentityFallback() {
        ByteBuffer buffer = SkinnedMeshVBO.createInterleavedBuffer(new float[]{1, 2, 3}, null, null,
                new int[]{0, 1, 2, -1}, new float[]{0, Float.NaN, -1, 1});

        assertInfluences(buffer, 0, new int[]{-1, -1, -1, -1}, new float[]{0, 0, 0, 0});
    }

    @Test
    void mismatchedSourceDataIsRejectedBeforeGpuUpload() {
        assertThrows(IllegalArgumentException.class, () -> SkinnedMeshVBO.createInterleavedBuffer(
                new float[]{1, 2, 3, 4, 5, 6}, null, null, new int[]{0, 1, 2}, new float[]{1, 1, 1}));
        assertThrows(IllegalArgumentException.class, () -> SkinnedMeshVBO.createInterleavedBuffer(
                new float[]{1, 2, 3}, null, new float[]{0}, new int[]{0}, new float[]{1}));
    }

    private static void assertInfluences(ByteBuffer buffer, int vertex, int[] indices, float[] weights) {
        int offset = vertex * SkinnedMeshVBO.VERTEX_STRIDE;
        for (int slot = 0; slot < SkinnedMeshVBO.BONES_PER_VERTEX; slot++) {
            assertEquals(indices[slot], buffer.getInt(offset + 32 + slot * Integer.BYTES));
            assertEquals(weights[slot], buffer.getFloat(offset + 48 + slot * Float.BYTES), 1.0e-6f);
        }
    }
}
