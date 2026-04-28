package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Depth-based sorting for Gaussian splats.
 *
 * Sorts splats back-to-front based on view matrix for correct alpha blending.
 */
public class GaussianSplatSorter {

    private int[] sortedIndices;
    private float[] depths;

    public GaussianSplatSorter() {
    }

    /**
     * Sort splats by depth (back-to-front)
     *
     * @param splats List of splats to sort
     * @param viewMatrix View matrix for depth computation
     */
    public void sort(List<GaussianSplat> splats, Matrix4f viewMatrix) {
        int count = splats.size();

        // Allocate or resize arrays
        if (sortedIndices == null || sortedIndices.length != count) {
            sortedIndices = new int[count];
            depths = new float[count];
        }

        // Compute depths
        for (int i = 0; i < count; i++) {
            GaussianSplat splat = splats.get(i);
            Vector3f pos = splat.getPosition();

            // Depth = -view_matrix[2] * pos (third row of view matrix)
            float depth = -(viewMatrix.m20() * pos.x + viewMatrix.m21() * pos.y + viewMatrix.m22() * pos.z + viewMatrix.m23());
            depths[i] = depth;
            sortedIndices[i] = i;
        }

        // Sort indices by depth (back-to-front = descending depth)
        quickSort(0, count - 1);
    }

    /**
     * Get sorted indices array
     */
    public int[] getSortedIndices() {
        return sortedIndices;
    }

    /**
     * Quick sort implementation for depth sorting
     */
    private void quickSort(int low, int high) {
        if (low < high) {
            int pi = partition(low, high);
            quickSort(low, pi - 1);
            quickSort(pi + 1, high);
        }
    }

    private int partition(int low, int high) {
        float pivot = depths[sortedIndices[high]];
        int i = low - 1;

        for (int j = low; j < high; j++) {
            // Sort descending (back-to-front)
            if (depths[sortedIndices[j]] > pivot) {
                i++;
                // Swap indices
                int temp = sortedIndices[i];
                sortedIndices[i] = sortedIndices[j];
                sortedIndices[j] = temp;
            }
        }

        // Swap pivot
        int temp = sortedIndices[i + 1];
        sortedIndices[i + 1] = sortedIndices[high];
        sortedIndices[high] = temp;

        return i + 1;
    }
}
