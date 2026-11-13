package cn.minerealms.assimploader.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkinnedMeshProcessorTest {
    @Test
    void strongestInfluencesWinRegardlessOfImportOrder() {
        var data = new SkinnedMeshProcessor.VertexBoneData(4);
        for (int i = 0; i < 10; i++) data.addBoneData(i, (i + 1) * 0.1f);
        data.addBoneData(20, Float.NaN);
        data.addBoneData(21, -1);
        assertArrayEquals(new int[]{9, 8, 7, 6}, data.boneIndices);
        data.normalize();
        float total = 0;
        for (float weight : data.boneWeights) total += weight;
        assertEquals(1, total, 1e-6f);
        assertEquals(1f / 3.4f, data.boneWeights[0], 1e-6f);
    }
}
