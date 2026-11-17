package cn.minerealms.assimploader.client.transform;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GizmoMathTest {
    @Test void projectedPointAndPickingRayAgreeWithPerspectiveCamera() {
        var matrix = new Matrix4f().perspective((float) Math.toRadians(70), 16f / 9, .05f, 100)
                .lookAt(0, 0, 5, 0, 0, 0, 0, 1, 0);
        var point = new Vector3f(1, .3f, 0);
        var screen = GizmoMath.project(matrix, point, 1280, 720);
        assertNotNull(screen);
        var ray = GizmoMath.ray(new Matrix4f(matrix).invert(), screen.x(), screen.y(), 1280, 720);
        var hit = GizmoMath.plane(ray, new Vector3f(), new Vector3f(0, 0, 1));
        assertNotNull(hit);
        assertEquals(0, point.distance(hit), 1e-4);
        assertNull(GizmoMath.project(matrix, new Vector3f(0, 0, 6), 1280, 720));
        assertNull(GizmoMath.project(matrix, new Vector3f(0, 0, -200), 1280, 720));
    }

    @Test void draggingConstraintsRejectParallelOrBackwardIntersections() {
        var ray = new GizmoMath.Ray(new Vector3f(2, 3, 4), new Vector3f(0, 0, -1));
        assertEquals(2, GizmoMath.axisParameter(ray, new Vector3f(), new Vector3f(1, 0, 0)), 1e-6);
        assertTrue(Float.isNaN(GizmoMath.axisParameter(ray, new Vector3f(), new Vector3f(0, 0, 1))));
        assertNull(GizmoMath.plane(ray, new Vector3f(), new Vector3f(0, 1, 0)));
        assertNull(GizmoMath.plane(ray, new Vector3f(0, 0, 5), new Vector3f(0, 0, 1)));
        assertEquals((float) Math.PI / 2,
                GizmoMath.signedAngle(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)), 1e-6);
    }

    @Test void pickingDistanceIsClampedToHandleSegment() {
        var a = new GizmoMath.Point(10, 10, 0);
        var b = new GizmoMath.Point(20, 10, 0);
        assertEquals(3, GizmoMath.segmentDistance(15, 13, a, b), 1e-6);
        assertEquals(5, GizmoMath.segmentDistance(24, 13, a, b), 1e-6);
        assertEquals(5, GizmoMath.segmentDistance(13, 14, a, a), 1e-6);
    }
}
