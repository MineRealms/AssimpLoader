package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.MorphDeformation;
import cn.minerealms.assimploader.animation.MorphTargetTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MorphTargetTBOTest {
    @Test void packedTexelsMatchImmutableNativeMorphDeltas() {
        var mesh=MorphTargetTest.fixture().getMeshes().get(0);
        float[] packed=MorphTargetTBO.pack(mesh);
        assertEquals(mesh.getVertexCount()*8,packed.length);
        float[] positions=mesh.getMorphTargets().get(0).copyPositionDeltas();
        for(int vertex=0;vertex<mesh.getVertexCount();vertex++) for(int axis=0;axis<3;axis++)
            assertEquals(positions[vertex*3+axis],packed[vertex*8+axis]);
        packed[0]=900;
        assertEquals(positions[0],MorphTargetTBO.pack(mesh)[0]);
        assertTrue(MorphTargetTBO.fits(mesh,6));
        assertFalse(MorphTargetTBO.fits(mesh,5));
    }

    @Test void effectiveWeightsAreFiniteOwnedAndMatchCpuExtrapolation() {
        var mesh=MorphTargetTest.fixture().getMeshes().get(0);
        float[] weights={-1};
        float[] effective=MorphDeformation.effectiveWeights(mesh,weights);
        assertArrayEquals(weights,effective);
        effective[0]=5;
        assertEquals(-1,weights[0]);
        assertThrows(IllegalArgumentException.class,()->MorphDeformation.effectiveWeights(mesh,new float[]{Float.NaN}));
        assertThrows(IllegalArgumentException.class,()->MorphDeformation.effectiveWeights(mesh,new float[0]));
    }
}
