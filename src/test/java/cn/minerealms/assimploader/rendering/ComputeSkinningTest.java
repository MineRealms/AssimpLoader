package cn.minerealms.assimploader.rendering;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class ComputeSkinningTest {
    @Test void eightInfluencesRetainBothStd430GroupsWithoutTruncation() {
        var packed=ComputeSkinning.pack(new float[]{1,2,3,4,5,6},new float[]{0,0,1,1,0,0},new float[]{.25f,.5f,.75f,1},
                new int[]{0,1,2,3,4,5,6,7,10,11,12,13,14,15,16,17},
                new float[]{1,2,3,4,5,6,7,8,8,7,6,5,4,3,2,1});
        try {
            assertEquals(224,packed.remaining());
            assertEquals(1,packed.getFloat(0));assertEquals(4,packed.getFloat(112));
            assertEquals(.25f,packed.getFloat(32));assertEquals(.75f,packed.getFloat(144));
            for(int slot=0;slot<8;slot++) {
                int offset=48+(slot/4)*32+(slot%4)*4;
                assertEquals(slot,packed.getInt(offset));
                assertEquals(slot+1,packed.getFloat(offset+16));
                assertEquals(slot+10,packed.getInt(offset+112));
                assertEquals(8-slot,packed.getFloat(offset+128));
            }
        } finally {MemoryUtil.memFree(packed);}
    }

    @Test void missingChannelsPadAndInvalidWeightsDoNotReachTheShader() {
        var packed=ComputeSkinning.pack(new float[]{1,2,3},null,null,new int[]{255,-1,4,3},new float[]{.25f,1,Float.NaN,-1});
        try {
            assertEquals(1,packed.getFloat(20));assertEquals(0,packed.getFloat(32));
            assertEquals(255,packed.getInt(48));assertEquals(.25f,packed.getFloat(64));
            assertEquals(0,packed.getFloat(72));assertEquals(0,packed.getFloat(76));
            for(int slot=0;slot<4;slot++) {assertEquals(-1,packed.getInt(80+slot*4));assertEquals(0,packed.getFloat(96+slot*4));}
        } finally {MemoryUtil.memFree(packed);}
    }

    @Test void malformedLayoutsAreRejectedBeforeNativeAllocation() {
        assertThrows(IllegalArgumentException.class,()->ComputeSkinning.pack(new float[0],null,null,new int[0],new float[0]));
        assertThrows(IllegalArgumentException.class,()->ComputeSkinning.pack(new float[4],null,null,new int[1],new float[1]));
        assertThrows(IllegalArgumentException.class,()->ComputeSkinning.pack(new float[3],new float[1],null,new int[1],new float[1]));
        assertThrows(IllegalArgumentException.class,()->ComputeSkinning.pack(new float[3],null,new float[1],new int[1],new float[1]));
        assertThrows(IllegalArgumentException.class,()->ComputeSkinning.pack(new float[3],null,null,new int[9],new float[9]));
    }
}
