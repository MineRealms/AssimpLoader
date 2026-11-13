package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/** Reusable original-index ordering, back to front in view space. */
public final class GaussianSplatSorter {
    private int[] sortedIndices = new int[0];
    private float[] depths = new float[0];
    private int count;
    private final Vector3f position = new Vector3f();
    private final IntComparator comparator = (left, right) -> {
        int order = Float.compare(depths[right], depths[left]);
        return order != 0 ? order : Integer.compare(left, right);
    };

    public void sort(List<GaussianSplat> splats, Matrix4f viewMatrix) {
        ensureCapacity(splats.size());
        for (int i = 0; i < splats.size(); i++) sortedIndices[i] = i;
        sortPrepared(splats, viewMatrix, splats.size());
    }

    public void sort(List<GaussianSplat> splats, Matrix4f viewMatrix, int[] originalIndices, int selectedCount) {
        if (selectedCount < 0 || selectedCount > originalIndices.length) throw new IllegalArgumentException("Invalid selected count");
        ensureCapacity(Math.max(splats.size(), selectedCount));
        System.arraycopy(originalIndices, 0, sortedIndices, 0, selectedCount);
        sortPrepared(splats, viewMatrix, selectedCount);
    }

    private void sortPrepared(List<GaussianSplat> splats, Matrix4f view, int selectedCount) {
        if (!view.isFinite()) throw new IllegalArgumentException("Non-finite view matrix");
        count = selectedCount;
        for (int i = 0; i < count; i++) {
            int index = sortedIndices[i];
            if (index < 0 || index >= splats.size()) throw new IllegalArgumentException("Splat index out of bounds");
            splats.get(index).getPosition(position);
            // JOML fields use column then row; view-space z is row 2.
            float depth = -(view.m02() * position.x + view.m12() * position.y
                    + view.m22() * position.z + view.m32());
            depths[index] = Float.isFinite(depth) ? depth : Float.NEGATIVE_INFINITY;
        }
        IntArrays.quickSort(sortedIndices, 0, count, comparator);
    }

    private void ensureCapacity(int capacity) {
        if (sortedIndices.length < capacity) {
            sortedIndices = new int[capacity];
            depths = new float[capacity];
        }
    }

    /** Compatibility snapshot; rendering uses the backing buffer plus explicit count. */
    public int[] getSortedIndices() { return Arrays.copyOf(sortedIndices, count); }
    int[] indicesBuffer() { return sortedIndices; }
    public int getSortedCount() { return count; }
    void clear() {
        sortedIndices = new int[0];
        depths = new float[0];
        count = 0;
    }
}
