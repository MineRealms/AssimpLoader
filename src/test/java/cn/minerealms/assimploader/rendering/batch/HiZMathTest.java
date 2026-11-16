package cn.minerealms.assimploader.rendering.batch;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HiZMathTest {
    @Test void nonPowerOfTwoReductionIncludesLastRowAndColumn() {
        float[] input=new float[7*5];Arrays.fill(input,.2f);input[6+4*7]=1;
        var levels=HiZMath.pyramid(new HiZMath.Level(7,5,input));
        assertEquals(3,levels.length);assertEquals(3,levels[1].width());assertEquals(2,levels[1].height());
        assertEquals(1,levels[1].depth()[2+3]);assertEquals(1,levels[2].depth()[0]);
    }

    @Test void fractionalFootprintsOverlapRatherThanDropSourceTexels() {
        var level=HiZMath.reduce(new HiZMath.Level(5,1,new float[]{.1f,.2f,.9f,.4f,.5f}),2,1);
        assertArrayEquals(new float[]{.9f,.9f},level.depth());
        var invalid=HiZMath.reduce(new HiZMath.Level(2,1,new float[]{Float.NaN,.5f}),1,1);
        assertEquals(1,invalid.depth()[0]);
    }

    @Test void occlusionNeedsEveryCoveredDepthTexelAndStrictBias() {
        float[] depths=new float[64*64];Arrays.fill(depths,.2f);
        var levels=HiZMath.pyramid(new HiZMath.Level(64,64,depths));
        assertTrue(HiZMath.occluded(-.1f,-.1f,.2f,.1f,.1f,.4f,new Matrix4f(),levels,.001f));
        assertFalse(HiZMath.occluded(-.1f,-.1f,-.61f,.1f,.1f,-.4f,new Matrix4f(),levels,.01f));
        depths[32*64+32]=1;
        assertFalse(HiZMath.occluded(-.1f,-.1f,.2f,.1f,.1f,.4f,new Matrix4f(),HiZMath.pyramid(new HiZMath.Level(64,64,depths)),.001f));
    }

    @Test void nearFarScreenEdgesAndNonFiniteBoundsRemainVisible() {
        float[] depths=new float[32*32];Arrays.fill(depths,.1f);
        var levels=HiZMath.pyramid(new HiZMath.Level(32,32,depths));
        assertFalse(HiZMath.occluded(-.2f,-.2f,-1,.2f,.2f,.5f,new Matrix4f(),levels,0));
        assertFalse(HiZMath.occluded(-.2f,-.2f,.5f,.2f,.2f,1,new Matrix4f(),levels,0));
        assertFalse(HiZMath.occluded(.95f,-.2f,.5f,.97f,.2f,.7f,new Matrix4f(),levels,0));
        assertFalse(HiZMath.occluded(Float.NaN,-.2f,.5f,.2f,.2f,.7f,new Matrix4f(),levels,0));
        assertFalse(HiZMath.occluded(-.2f,-.2f,.5f,.2f,.2f,.7f,new Matrix4f().zero(),levels,0));
        assertFalse(HiZMath.occluded(-.2f,-.2f,.5f,.2f,.2f,.7f,new Matrix4f().m00(Float.NaN),levels,0));
    }

    @Test void perspectiveNearCrossingCannotBeOccludedAndForegroundIsVisible() {
        float[] depths=new float[128*72];Arrays.fill(depths,.5f);
        var levels=HiZMath.pyramid(new HiZMath.Level(128,72,depths));
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),128f/72,.1f,100);
        assertFalse(HiZMath.occluded(-.01f,-.01f,-.2f,.01f,.01f,.1f,projection,levels,.001f));
        assertFalse(HiZMath.occluded(-.01f,-.01f,-.15f,.01f,.01f,-.12f,projection,levels,.001f));
        assertTrue(HiZMath.occluded(-.1f,-.1f,-10,.1f,.1f,-9,projection,levels,.001f));
    }

    @Test void nonPowerOfTwoHoleAndConservativeDilationPreserveVisibility() {
        float[] depths=new float[63*35];Arrays.fill(depths,.2f);
        depths[31+17*63]=1;
        var levels=HiZMath.pyramid(new HiZMath.Level(63,35,depths));
        assertFalse(HiZMath.occluded(-.005f,-.005f,.5f,.005f,.005f,.6f,new Matrix4f(),levels,0));
        depths[31+17*63]=.2f;depths[30+17*63]=1;
        assertFalse(HiZMath.occluded(-.005f,-.005f,.5f,.005f,.005f,.6f,new Matrix4f(),
                HiZMath.pyramid(new HiZMath.Level(63,35,depths)),0));
    }

    @Test void textureCapacityIsBoundedAndIncludesAllMipLevels() {
        assertEquals(4,HiZPyramid.requiredBytes(1,1));
        assertEquals(32+8+4,HiZPyramid.requiredBytes(4,2));
        assertTrue(HiZPyramid.requiredBytes(1024,1024)<HiZPyramid.MAX_BYTES);
        assertThrows(IllegalArgumentException.class,()->HiZPyramid.requiredBytes(1025,1));
        assertThrows(IllegalArgumentException.class,()->HiZMath.reduce(new HiZMath.Level(1,1,new float[]{1}),2,1));
    }
}
