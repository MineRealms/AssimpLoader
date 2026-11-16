package cn.minerealms.assimploader.collision;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollisionShapeTest {
    @Test void boxParallelInsideAndDistanceLimits() {
        AabbCollider box = new AabbCollider(new Vector3f(-1), new Vector3f(1));
        Ray ray = new Ray(new Vector3f(0, 0, -3), new Vector3f(0, 0, 7));
        RayHit hit = box.raycast(ray, 10).orElseThrow();
        assertEquals(2, hit.distance(), 1e-6);
        assertTrue(hit.normal().distance(new Vector3f(0, 0, -1)) < 1e-6);
        assertTrue(box.raycast(ray, 1.99f).isEmpty());
        assertTrue(box.raycast(new Ray(new Vector3f(2, 0, -3), new Vector3f(0, 0, 1)), 10).isEmpty());
        assertEquals(1, box.raycast(new Ray(new Vector3f(), new Vector3f(1, 0, 0)), 10).orElseThrow().distance(), 1e-6);
    }

    @Test void capsuleSidesEndCapsInsideAndDegenerateSphere() {
        CapsuleCollider capsule = new CapsuleCollider(new Vector3f(0, -1, 0), new Vector3f(0, 1, 0), 0.5f);
        assertEquals(1.5f, capsule.raycast(new Ray(new Vector3f(-2, 0, 0), new Vector3f(1, 0, 0)), 10).orElseThrow().distance(), 1e-6);
        assertEquals(1.5f, capsule.raycast(new Ray(new Vector3f(0, -3, 0), new Vector3f(0, 1, 0)), 10).orElseThrow().distance(), 1e-6);
        assertEquals(0.5f, capsule.raycast(new Ray(new Vector3f(), new Vector3f(1, 0, 0)), 10).orElseThrow().distance(), 1e-6);
        assertEquals(1.5f, capsule.raycast(new Ray(new Vector3f(), new Vector3f(0, 1, 0)), 10).orElseThrow().distance(), 1e-6);
        assertTrue(capsule.raycast(new Ray(new Vector3f(-2, 2, 0), new Vector3f(1, 0, 0)), 10).isEmpty());
        CapsuleCollider sphere = new CapsuleCollider(new Vector3f(), new Vector3f(), 1);
        assertEquals(2, sphere.raycast(new Ray(new Vector3f(0, 0, -3), new Vector3f(0, 0, 1)), 3).orElseThrow().distance(), 1e-6);
        assertEquals(1.2f, sphere.project(new Vector3f(), 0.2f, new Vector3f(0, 1, 0)).y, 1e-6);
    }

    @Test void orientedBoxKeepsModelDistancesAndCorrectNormalUnderScale() {
        Matrix4f transform = new Matrix4f().translation(3, 4, 5).rotateY(0.7f).scale(2, 1, 0.5f);
        Vector3f normal = transform.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f surface = transform.transformPosition(new Vector3f(1, 0, 0));
        Ray ray = new Ray(new Vector3f(surface).fma(3, normal), new Vector3f(normal).negate());
        ObbCollider box = new ObbCollider(new Vector3f(-1), new Vector3f(1), transform);
        RayHit hit = box.raycast(ray, 5).orElseThrow();
        assertEquals(3, hit.distance(), 2e-5);
        assertTrue(hit.position().distance(surface) < 2e-5);
        assertTrue(hit.normal().distance(normal) < 2e-5);
        assertTrue(box.raycast(ray, 2.9f).isEmpty());
    }

    @Test void bvhFindsNearestTriangleAcrossBranchesAndCopiesInput() {
        float[] vertices = new float[60 * 9];
        int[] indices = new int[60 * 3];
        for (int i = 0; i < 60; i++) {
            float z = 60 - i;
            System.arraycopy(new float[]{-1,-1,z, 1,-1,z, 0,1,z}, 0, vertices, i * 9, 9);
            indices[i * 3] = i * 3; indices[i * 3 + 1] = i * 3 + 1; indices[i * 3 + 2] = i * 3 + 2;
        }
        TriangleMeshBvh bvh = new TriangleMeshBvh(vertices, indices);
        java.util.Arrays.fill(vertices, Float.NaN);
        java.util.Arrays.fill(indices, -1);
        RayHit hit = bvh.raycast(new Ray(new Vector3f(), new Vector3f(0, 0, 1)), 100).orElseThrow();
        assertEquals(1, hit.distance(), 1e-6);
        assertEquals(59, hit.triangle());
        assertTrue(hit.normal().distance(new Vector3f(0, 0, -1)) < 1e-6);
        assertTrue(bvh.raycast(new Ray(new Vector3f(5, 0, 0), new Vector3f(0, 0, 1)), 100).isEmpty());
        assertTrue(bvh.raycast(new Ray(new Vector3f(), new Vector3f(0, 0, 1)), 0.5f).isEmpty());
    }

    @Test void rejectsInvalidGeometryAndDoesNotExposeMutableHits() {
        assertThrows(IllegalArgumentException.class, () -> new Ray(new Vector3f(), new Vector3f()));
        assertThrows(IllegalArgumentException.class, () -> new Ray(new Vector3f(Float.NaN), new Vector3f(1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new CapsuleCollider(new Vector3f(), new Vector3f(), Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ObbCollider(new Vector3f(-1), new Vector3f(1), new Matrix4f().scale(0)));
        assertThrows(IllegalArgumentException.class, () -> new TriangleMeshBvh(new float[]{0,0,0}, new int[]{0,1,2}));
        RayHit hit = new RayHit(1, new Vector3f(1), new Vector3f(0, 1, 0), -1);
        hit.position().zero();
        assertEquals(new Vector3f(1), hit.position());
        assertTrue(new TriangleMeshBvh(new float[0], new int[0]).raycast(new Ray(new Vector3f(), new Vector3f(1,0,0)), 10).isEmpty());
    }

    @Test void tangentBoundaryAndTwoSidedTrianglesAreIncluded() {
        CapsuleCollider capsule = new CapsuleCollider(new Vector3f(0,-1,0), new Vector3f(0,1,0), 1);
        RayHit tangent = capsule.raycast(new Ray(new Vector3f(-2,0,1), new Vector3f(1,0,0)), 2).orElseThrow();
        assertEquals(2, tangent.distance(), 1e-6);
        TriangleMeshBvh mesh = new TriangleMeshBvh(new float[]{0,0,0, 1,0,0, 0,1,0}, new int[]{0,1,2});
        assertEquals(1, mesh.raycast(new Ray(new Vector3f(0,0,1), new Vector3f(0,0,-1)), 1).orElseThrow().distance(), 1e-6);
        assertEquals(1, mesh.raycast(new Ray(new Vector3f(.25f,.25f,-1), new Vector3f(0,0,1)), 1).orElseThrow().distance(), 1e-6);
        TriangleMeshBvh degenerate = new TriangleMeshBvh(new float[]{0,0,0}, new int[]{0,0,0});
        assertTrue(degenerate.raycast(new Ray(new Vector3f(0,0,1), new Vector3f(0,0,-1)), 10).isEmpty());
    }
}
