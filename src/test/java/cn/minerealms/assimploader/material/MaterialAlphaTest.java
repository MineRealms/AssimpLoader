package cn.minerealms.assimploader.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialAlphaTest {
    @Test void opaqueIgnoresBothTextureAndMaterialAlphaWithoutChangingSourceRgb() {
        var source=new LabPbrPacker.Image(3,1,new int[]{0x00123456,0x80654321,0xff102030});
        int[] original=source.pixels();
        var opaque=new MaterialAlpha(MaterialAlpha.Mode.OPAQUE,0,1).apply(source);
        assertArrayEquals(new int[]{0xff123456,0xff654321,0xff102030},opaque.pixels());
        assertArrayEquals(original,source.pixels());
        assertNotSame(source,opaque);
    }

    @Test void maskUsesTextureTimesFactorAndIncludesTheExactCutoffBoundary() {
        var source=new LabPbrPacker.Image(4,1,new int[]{0x00123456,0x7f123456,0x80123456,0xff123456});
        var mask=new MaterialAlpha(MaterialAlpha.Mode.MASK,.5f,64f/255).apply(source);
        assertArrayEquals(new int[]{0x00123456,0x00123456,0xff123456,0xff123456},mask.pixels());
        assertArrayEquals(new int[]{0x00123456,0x7f123456,0x80123456,0xff123456},source.pixels());
        var all=new MaterialAlpha(MaterialAlpha.Mode.MASK,0,0).apply(source);
        for(int pixel:all.pixels())assertEquals(255,pixel>>>24);
        var none=new MaterialAlpha(MaterialAlpha.Mode.MASK,0,1).apply(source);
        for(int pixel:none.pixels())assertEquals(0,pixel>>>24);
    }

    @Test void sourceAndBlendRetainContinuousCoverageWithoutAllocatingAnImageCopy() {
        var source=new LabPbrPacker.Image(1,1,new int[]{0x40123456});
        assertSame(source,MaterialAlpha.SOURCE.apply(source));
        assertSame(source,new MaterialAlpha(MaterialAlpha.Mode.BLEND,.25f,.9f).apply(source));
        var opaque=new LabPbrPacker.Image(1,1,new int[]{0xff123456});
        assertSame(opaque,new MaterialAlpha(MaterialAlpha.Mode.OPAQUE,0,1).apply(opaque));
        assertSame(opaque,new MaterialAlpha(MaterialAlpha.Mode.MASK,1,.5f).apply(opaque));
    }

    @Test void malformedAlphaCannotReachAnImageWorker() {
        assertThrows(IllegalArgumentException.class,()->new MaterialAlpha(MaterialAlpha.Mode.MASK,Float.NaN,.5f));
        assertThrows(IllegalArgumentException.class,()->new MaterialAlpha(MaterialAlpha.Mode.MASK,1,Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->new MaterialAlpha(MaterialAlpha.Mode.OPAQUE,-.1f,.5f));
        assertThrows(IllegalArgumentException.class,()->new MaterialAlpha(MaterialAlpha.Mode.BLEND,1,1.1f));
    }
}
