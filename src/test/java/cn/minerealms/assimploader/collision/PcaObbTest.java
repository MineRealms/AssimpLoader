package cn.minerealms.assimploader.collision;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PcaObbTest {
    @Test void rotatedCuboidIsEnclosedAndRayUsesModelUnits() {
        Matrix4f transform = new Matrix4f().translation(7, 3, -8).rotateXYZ(.2f, .73f, -.4f);
        float[] vertices = corners(transform, new Vector3f(4, 1, .25f));
        PcaObb box = PcaObb.fit(vertices);
        assertEnclosed(box, vertices);
        Vector3f span = box.maximum().sub(box.minimum());
        assertEquals(8, span.x, .001);
        assertEquals(2, span.y, .001);
        assertEquals(.5, span.z, .001);
        Vector3f normal = transform.transformDirection(new Vector3f(1, 0, 0));
        Vector3f surface = transform.transformPosition(new Vector3f(4, 0, 0));
        Ray ray = new Ray(new Vector3f(surface).fma(3, normal), new Vector3f(normal).negate());
        RayHit hit = box.raycast(ray, 5).orElseThrow();
        assertEquals(3, hit.distance(), .001);
        assertTrue(hit.normal().distance(normal) < .001);
        assertTrue(box.raycast(ray, 2.9f).isEmpty());
        assertTrue(box.raycast(new Ray(transform.transformPosition(new Vector3f()), normal), 5).isPresent());
        assertEquals(8, box.covarianceSamples());
        box.minimum().zero(); box.maximum().zero(); box.transform().zero();
        assertEquals(3, box.raycast(ray, 5).orElseThrow().distance(), .001);
    }

    @Test void pointLinePlaneAndRepeatedPointsStayFiniteAndDeterministic() {
        for (float[] points : List.of(new float[]{5, 6, 7}, new float[]{2, 2, 2, 2, 2, 2},
                new float[]{-3, -3, -3, 0, 0, 0, 3, 3, 3},
                new float[]{-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0})) {
            PcaObb box = PcaObb.fit(points);
            assertEnclosed(box, points);
            assertTrue(box.transform().isFinite());
            assertEquals(1, box.transform().determinant(), 1e-5);
            assertTrue(box.transform().equals(PcaObb.fit(points).transform(), 0));
            Vector3f first = new Vector3f(points[0], points[1], points[2]);
            assertTrue(box.raycast(new Ray(new Vector3f(first).add(0, 0, 1), new Vector3f(0, 0, -1)), 2).isPresent());
        }
    }

    @Test void sampleBudgetStillEnclosesUnsampledOutliersAndLargeTranslatedGeometry() {
        float[] positions = new float[300];
        for (int i = 0; i < 100; i++) {
            positions[i * 3] = i;
            positions[i * 3 + 1] = i * .3f;
            positions[i * 3 + 2] = i * .7f;
        }
        positions[3] = 300;
        positions[4] = -800;
        positions[5] = 500;
        PcaObb sampled = PcaObb.fit(positions, new PcaObb.Limits(100, 3));
        assertEquals(3, sampled.covarianceSamples());
        assertEnclosed(sampled, positions);
        float[] translated = corners(new Matrix4f().translation(1_000_000, -2_000_000, 3_000_000)
                .rotateY(.62f), new Vector3f(4, 1, 2));
        assertEnclosed(PcaObb.fit(translated), translated);
    }

    @Test void rejectsInvalidLayoutBudgetsAndNonfiniteUnsampledVertices() {
        assertThrows(IllegalArgumentException.class, () -> PcaObb.fit(new float[0]));
        assertThrows(IllegalArgumentException.class, () -> PcaObb.fit(new float[]{1, 2}));
        assertThrows(IllegalArgumentException.class, () -> new PcaObb.Limits(0, 4096));
        assertThrows(IllegalArgumentException.class, () -> new PcaObb.Limits(10, 2));
        assertThrows(IllegalArgumentException.class, () -> PcaObb.fit(new float[9], new PcaObb.Limits(2, 3)));
        float[] vertices = new float[300];
        vertices[4] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> PcaObb.fit(vertices, new PcaObb.Limits(100, 3)));
    }

    @Test void staticMeshIsFittedOnlyOnQueryAndClearDropsItsCache() throws Exception {
        String obj = "o box\nv -2 -1 0\nv 2 -1 0\nv 0 1 0\nf 1 2 3\n";
        var scene = new AssimpModelLoader().loadModelFromMemory("test.obj", obj.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        var pose = PoseSnapshot.capture(1, scene, Map.of());
        ModelCollision collision = new ModelCollision(List.of());
        Ray ray = new Ray(new Vector3f(0, 0, -2), new Vector3f(0, 0, 1));
        assertTrue(cache(collision, "staticObbs").isEmpty());
        assertTrue(collision.raycast(pose, ray, 5, ModelCollision.Mode.BOUNDS).isPresent());
        assertTrue(cache(collision, "staticObbs").isEmpty());
        assertTrue(collision.raycast(pose, ray, 5, ModelCollision.Mode.OBB).isPresent());
        Object fitted = cache(collision, "staticObbs").values().iterator().next();
        assertTrue(collision.raycast(pose, ray, 5, ModelCollision.Mode.OBB).isPresent());
        assertSame(fitted, cache(collision, "staticObbs").values().iterator().next());
        collision.clear();
        assertTrue(cache(collision, "staticObbs").isEmpty());
        assertTrue(cache(collision, "deformedObbs").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ModelCollision(List.of(), new PcaObb.Limits(2, 3))
                .raycast(pose, ray, 5, ModelCollision.Mode.OBB));
    }

    @Test void currentPoseSkinningUsesSnapshotSpecificObbsAndBatchBudgetPrecedesSkinning() throws Exception {
        String path = "/assets/assimploader/models/validation/deer_showcase.glb";
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            var scene = new AssimpModelLoader().loadModelFromMemory(path, input.readAllBytes(), ModelImportMode.DYNAMIC);
            var first = PoseSnapshot.capture(1, scene, Map.of());
            var second = PoseSnapshot.capture(2, scene, Map.of("hips", new Matrix4f(scene.findNodeByName("hips").getTransformation()).translate(0, .1f, 0)));
            ModelCollision collision = new ModelCollision(List.of());
            Ray ray = new Ray(new Vector3f(4, 1.1f, 0), new Vector3f(-1, 0, 0));
            assertTrue(collision.raycast(first, ray, 10, ModelCollision.Mode.OBB).isPresent());
            assertTrue(collision.raycast(second, ray, 10, ModelCollision.Mode.OBB).isPresent());
            assertEquals(2, cache(collision, "deformedObbs").size());
            assertTrue(cache(collision, "staticObbs").isEmpty());
            var firstCache = (Map<?, ?>) cache(collision, "deformedObbs").get(first);
            var secondCache = (Map<?, ?>) cache(collision, "deformedObbs").get(second);
            assertNotSame(firstCache.values().iterator().next(), secondCache.values().iterator().next());
            collision.clear();
            assertThrows(IllegalArgumentException.class, () -> new ModelCollision(List.of(), new PcaObb.Limits(8, 3))
                    .raycast(first, ray, 10, ModelCollision.Mode.OBB));
        }
    }

    @Test void uncachedMultiMeshBatchRejectsBeforeProducingPartialCaches() throws Exception {
        String path = "/assets/assimploader/models/validation/animation_probe.fbx";
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            var scene = new AssimpModelLoader().loadModelFromMemory(path, input.readAllBytes(), ModelImportMode.DYNAMIC);
            int largest = scene.getMeshes().stream().mapToInt(mesh -> mesh.getVertexCount()).max().orElseThrow();
            int total = scene.getMeshes().stream().mapToInt(mesh -> mesh.getVertexCount()).sum();
            assertTrue(total > largest);
            var pose = PoseSnapshot.capture(1, scene, Map.of());
            var bounds = pose.getBounds();
            Ray ray = new Ray(new Vector3f((float)bounds.minX - 1, (float)(bounds.minY + bounds.maxY) / 2,
                    (float)(bounds.minZ + bounds.maxZ) / 2), new Vector3f(1, 0, 0));
            ModelCollision collision = new ModelCollision(List.of(), new PcaObb.Limits(largest, 3));
            assertThrows(IllegalArgumentException.class, () -> collision.raycast(pose, ray, 100, ModelCollision.Mode.OBB));
            assertTrue(cache(collision, "staticObbs").isEmpty());
            assertTrue(((Map<?, ?>)cache(collision, "deformedObbs").get(pose)).isEmpty());
        }
    }

    private static Map<?, ?> cache(ModelCollision collision, String name) throws Exception {
        Field field = ModelCollision.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(collision);
    }

    private static float[] corners(Matrix4f transform, Vector3f half) {
        float[] vertices = new float[24];
        int offset = 0;
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            Vector3f point = transform.transformPosition(new Vector3f(x * half.x, y * half.y, z * half.z));
            vertices[offset++] = point.x; vertices[offset++] = point.y; vertices[offset++] = point.z;
        }
        return vertices;
    }

    private static void assertEnclosed(PcaObb box, float[] vertices) {
        Matrix4f inverse = box.transform().invert();
        Vector3f low = box.minimum(), high = box.maximum();
        for (int i = 0; i < vertices.length; i += 3) {
            Vector3f point = inverse.transformPosition(new Vector3f(vertices[i], vertices[i + 1], vertices[i + 2]));
            for (int axis = 0; axis < 3; axis++) assertTrue(point.get(axis) >= low.get(axis) && point.get(axis) <= high.get(axis),
                    () -> point + " outside " + low + " / " + high);
        }
    }
}
