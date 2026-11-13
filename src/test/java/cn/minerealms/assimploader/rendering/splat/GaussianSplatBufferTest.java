package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GaussianSplatBufferTest {
    @Test
    void oversizedShIsTruncatedWithoutChangingTheNextPointStride() {
        float[] longSh = new float[48];
        for (int i = 0; i < longSh.length; i++) longSh[i] = i + 1;
        var packer = new GaussianSplatBuffer(0);
        var data = new GaussianSplatData(List.of(splat(1, longSh), splat(9, new float[]{4, 5, 6})), 0);
        var buffer = packer.pack(data);
        assertEquals(14, packer.getStride());
        assertEquals(28, buffer.remaining());
        assertEquals(1, buffer.get(11));
        assertEquals(3, buffer.get(13));
        assertEquals(9, buffer.get(14));
        assertEquals(4, buffer.get(25));
    }

    @Test
    void shortShIsZeroPaddedAndEveryPackedAttributeRemainsFinite() {
        var data = new GaussianSplatData(List.of(new GaussianSplat(new Vector3f(),
                new Quaternionf(0, 0, 0, 0), new Vector3f(1000, -1000, 0), Float.POSITIVE_INFINITY,
                new float[]{Float.NaN, 2})), 3);
        var buffer = new GaussianSplatBuffer(3).pack(data);
        assertEquals(59, buffer.remaining());
        for (int i = 0; i < buffer.remaining(); i++) assertTrue(Float.isFinite(buffer.get(i)));
        assertEquals(1, buffer.get(3), "zero quaternion must become identity wxyz");
        assertEquals(0, buffer.get(11));
        assertEquals(2, buffer.get(12));
        assertEquals(0, buffer.get(58));
        assertThrows(IllegalArgumentException.class, () -> new GaussianSplatBuffer(4));
        assertTrue(data.getBounds().getCenter().isFinite());
    }

    static GaussianSplat splat(float x, float[] sh) {
        return new GaussianSplat(new Vector3f(x, 0, 0), new Quaternionf(), new Vector3f(), 0, sh);
    }
}
