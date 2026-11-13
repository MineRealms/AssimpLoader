package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GaussianSplatSorterTest {
    @Test
    void transformedDepthUsesJomlRowTwoAndKeepsOriginalSelectedIndices() {
        List<GaussianSplat> points = List.of(point(1, 0), point(2, -100), point(4, 0), point(3, -5));
        Matrix4f view = new Matrix4f().rotateY((float) Math.PI / 2).translate(0, 0, -3);
        var sorter = new GaussianSplatSorter();
        sorter.sort(points, view, new int[]{0, 2}, 2);
        assertArrayEquals(new int[]{2, 0}, sorter.getSortedIndices());
        assertEquals(2, sorter.getSortedCount());
        assertThrows(IllegalArgumentException.class, () -> sorter.sort(points, view, new int[]{7}, 1));
    }

    @Test
    void equalDepthOrderingIsDeterministicAndLargeCloudDoesNotUseLinearRecursion() {
        var sorter = new GaussianSplatSorter();
        var equal = Collections.nCopies(100_000, point(0, -3));
        sorter.sort(equal, new Matrix4f());
        int[] order = sorter.getSortedIndices();
        assertEquals(equal.size(), order.length);
        for (int i = 0; i < order.length; i++) assertEquals(i, order[i]);
        sorter.sort(equal, new Matrix4f(), new int[]{8, 2, 6}, 3);
        assertArrayEquals(new int[]{2, 6, 8}, sorter.getSortedIndices());
        sorter.sort(List.of(), new Matrix4f());
        assertEquals(0, sorter.getSortedCount());
    }

    private static GaussianSplat point(float x, float z) {
        return new GaussianSplat(new Vector3f(x, 0, z), new Quaternionf(), new Vector3f(), 0, new float[3]);
    }
}
