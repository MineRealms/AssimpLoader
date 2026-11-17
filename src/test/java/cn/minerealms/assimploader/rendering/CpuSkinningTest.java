package cn.minerealms.assimploader.rendering;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CpuSkinningTest {
    @Test
    void weightedPalettePreservesTranslationAndUnweightedVertices() {
        var result = CpuSkinning.deform(new float[]{1, 2, 3, 4, 5, 6}, null,
                new int[]{0, 1, -1, -1}, new float[]{0.25f, 0.75f, 0, 0},
                new Matrix4f[]{new Matrix4f().translation(4, 0, 0), new Matrix4f().translation(0, 8, 0)});
        assertArrayEquals(new float[]{2, 8, 3, 4, 5, 6}, result.positions(), 1.0e-6f);
        for (float value : result.normals()) assertTrue(Float.isFinite(value));
    }

    @Test
    void repeatedInfluencesReuseMatricesWithoutMergingDistinctVertexOrMorphData() {
        int[] indices = {0, 1, 0, 1, 1, -1, 0, 1};
        float[] weights = {.25f, .75f, .25f, .75f, 1, 0, .25f, .75f};
        var layout = CpuSkinning.InfluenceLayout.create(4, indices, weights);
        assertEquals(2, layout.representatives.length);
        int[] visits = new int[4];
        for (int first : layout.firstVertices)
            for (int vertex = first; vertex >= 0; vertex = layout.nextVertices[vertex]) visits[vertex]++;
        assertArrayEquals(new int[]{1, 1, 1, 1}, visits);

        float[] positions = {1, 0, 0, 0, 2, 0, 0, 0, 3, -1, 2, -3};
        float[] normals = {1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 1};
        Matrix4f[] palette = {new Matrix4f().translation(2, 3, 4).rotateY(.7f).scale(2, 3, .5f),
                new Matrix4f().translation(-3, 2, 1).rotateZ(-.3f).scale(-1, 2, 4)};
        assertMatchesReference(positions, normals, indices, weights, palette);
        float[] morphed = positions.clone();
        morphed[0] += 3;
        morphed[4] -= 2;
        assertMatchesReference(morphed, normals, indices, weights, palette);
        assertArrayEquals(new float[]{1, 0, 0, 0, 2, 0, 0, 0, 3, -1, 2, -3}, positions);
    }

    @Test
    void groupedSkinningPreservesInvalidWeightsAndSingularNormalFallback() {
        float[] positions = {1, 2, 3, 4, 5, 6, -1, -2, -3, -4, -5, -6};
        int[] indices = {0, 1, 0, 1, -1, 2, 0, 1};
        float[] weights = {1, 1, 1, 1, Float.NaN, Float.POSITIVE_INFINITY, 1e-10f, 0};
        Matrix4f[] palette = {new Matrix4f(), new Matrix4f().scale(-1, 1, 1)};
        var result = CpuSkinning.deform(positions, null, indices, weights, palette);
        assertMatchesReference(positions, null, indices, weights, palette);
        assertArrayEquals(new float[]{0, 2, 3, 0, 5, 6, -1, -2, -3, -4, -5, -6}, result.positions());
        assertArrayEquals(new float[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0}, result.normals());
        assertEquals(0, CpuSkinning.deform(new float[0], null, new int[0], new float[0], palette).positions().length);
    }

    @Test
    void expandedVerticesMatchIndependentSkinningWithScaleReflectionAndNonNormalizedWeights() {
        Random random = new Random(38731);
        int vertices = 256, stride = 4;
        float[] positions = new float[vertices * 3], normals = new float[vertices * 3];
        int[] indices = new int[vertices * stride];
        float[] weights = new float[vertices * stride];
        Matrix4f[] palette = new Matrix4f[5];
        for (int i = 0; i < palette.length; i++)
            palette[i] = new Matrix4f().translation(random.nextFloat(), random.nextFloat(), random.nextFloat())
                    .rotateXYZ(random.nextFloat(), random.nextFloat(), random.nextFloat())
                    .scale(i == 0 ? -2 : .5f + random.nextFloat(), 1 + random.nextFloat(), .5f + random.nextFloat());
        for (int i = 0; i < positions.length; i++) {
            positions[i] = random.nextFloat() * 8 - 4;
            normals[i] = random.nextFloat() * 2 - 1;
        }
        for (int vertex = 0; vertex < vertices; vertex++) {
            int group = vertex % 16;
            for (int slot = 0; slot < stride; slot++) {
                indices[vertex * stride + slot] = (group + slot) % palette.length;
                weights[vertex * stride + slot] = (group + slot + 1) * .13f;
            }
        }
        assertEquals(16, CpuSkinning.InfluenceLayout.create(vertices, indices, weights).representatives.length);
        assertMatchesReference(positions, normals, indices, weights, palette);
    }

    private static void assertMatchesReference(float[] positions, float[] normals, int[] indices, float[] weights,
                                               Matrix4f[] palette) {
        var actual = CpuSkinning.deform(positions, normals, indices, weights, palette);
        int vertices = positions.length / 3, stride = vertices == 0 ? 0 : weights.length / vertices;
        for (int vertex = 0; vertex < vertices; vertex++) {
            float[] coefficients = new float[16];
            float total = 0;
            for (int slot = 0; slot < stride; slot++) {
                int index = indices[vertex * stride + slot];
                float weight = weights[vertex * stride + slot];
                if (index < 0 || index >= palette.length || !Float.isFinite(weight) || weight <= 0) continue;
                float[] bone = palette[index].get(new float[16]);
                for (int i = 0; i < 16; i++) coefficients[i] += bone[i] * weight;
                total += weight;
            }
            if (total <= 1e-8f) coefficients = new Matrix4f().get(new float[16]);
            else for (int i = 0; i < 16; i++) coefficients[i] /= total;
            Matrix4f transform = new Matrix4f().set(coefficients);
            Matrix3f normal = new Matrix3f(transform);
            if (Math.abs(normal.determinant()) > 1e-12f) normal.invert().transpose();
            else normal.identity();
            int p = vertex * 3;
            Vector3f position = transform.transformPosition(new Vector3f(positions[p], positions[p + 1], positions[p + 2]));
            Vector3f direction = normal.transform(normals == null ? new Vector3f(0, 1, 0)
                    : new Vector3f(normals[p], normals[p + 1], normals[p + 2]));
            for (int component = 0; component < 3; component++) {
                assertEquals(position.get(component), actual.positions()[p + component], 2e-5f);
                assertEquals(direction.get(component), actual.normals()[p + component], 2e-4f);
            }
        }
    }
}
