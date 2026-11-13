package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GaussianSplatLODTest {
    private final GaussianSplatData data = new GaussianSplatData(IntStream.range(0, 10)
            .mapToObj(i -> GaussianSplatBufferTest.splat(i, new float[3])).toList(), 0);

    @Test
    void lodStreamsOnlyChosenOriginalIndicesAndSortCountMatchesSelection() {
        int[] target = new int[10];
        var settings = new GaussianSplatLOD.Settings(true, 16, 32, 64, 128, 1, 2, 4);
        int count = GaussianSplatLOD.selectIndices(data, new Vector3f(4.5f, 0, 24), target, settings);
        assertEquals(5, count);
        assertArrayEquals(new int[]{0, 2, 4, 6, 8}, java.util.Arrays.copyOf(target, count));
        var sorter = new GaussianSplatSorter();
        sorter.sort(data.getSplats(), new Matrix4f(), target, count);
        assertEquals(count, sorter.getSortedCount());
        assertArrayEquals(new int[]{0, 2, 4, 6, 8}, sorter.getSortedIndices());
        assertEquals(2, GaussianSplatLOD.selectIndices(data, new Vector3f(4.5f, 0, 80), target, settings));
        assertEquals(0, GaussianSplatLOD.selectIndices(data, new Vector3f(4.5f, 0, 128), target, settings));
        assertEquals(10, data.getSplatCount(), "LOD must not repack or mutate the original cloud");
    }

    @Test
    void disabledLodIgnoresDistanceAndUnloadedConfigurationHasUsableDefaults() {
        var disabled = new GaussianSplatLOD.Settings(false, 16, 32, 64, 128, 1, 2, 4);
        assertEquals(10, GaussianSplatLOD.selectIndices(data, new Vector3f(0, 0, 10000), new int[10], disabled));
        assertNotNull(GaussianSplatLOD.Settings.configured());
        assertEquals(10, GaussianSplatLOD.applyLOD(data, new Vector3f(4.5f, 0, 0)).getSplatCount());
    }
}
