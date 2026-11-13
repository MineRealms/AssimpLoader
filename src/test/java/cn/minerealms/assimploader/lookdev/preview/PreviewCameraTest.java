package cn.minerealms.assimploader.lookdev.preview;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewCameraTest {
    @Test
    void fitKeepsBoundsInsideWideAndNarrowViewportsAtDifferentScales() {
        for (float aspect : new float[]{0.25f, 1, 4}) {
            for (float radius : new float[]{0.0001f, 1, 10000}) {
                PreviewCamera camera = new PreviewCamera();
                camera.reset(radius, aspect);
                Matrix4f clip = camera.projection(aspect).mul(camera.view());
                float halfExtent = radius / (float) Math.sqrt(3);
                for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
                    Vector4f point = clip.transform(new Vector4f(x * halfExtent, y * halfExtent, z * halfExtent, 1));
                    assertTrue(point.isFinite());
                    assertTrue(point.w > 0);
                    assertTrue(Math.abs(point.x) <= point.w, "horizontal fit, aspect=" + aspect);
                    assertTrue(Math.abs(point.y) <= point.w, "vertical fit, aspect=" + aspect);
                    assertTrue(Math.abs(point.z) <= point.w, "clipping planes, radius=" + radius);
                }
            }
        }
    }

    @Test
    void extremeOrbitZoomAndInvalidInputCannotProduceInvalidMatrices() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(0, 0);
        camera.orbit(1e6, 1e6);
        camera.zoom(1e6);
        camera.pan(100, -100, 0);
        camera.zoom(Double.NaN);
        camera.orbit(Double.POSITIVE_INFINITY, 0);
        assertTrue(camera.distance() > 0 && Float.isFinite(camera.distance()));
        assertTrue(Math.abs(camera.pitch()) <= 89);
        assertTrue(camera.view().isFinite());
        assertTrue(camera.projection(1).isFinite());
        camera.zoom(-1e6);
        assertTrue(camera.projection(1).isFinite());
    }

    @Test
    void panningChangesTargetWithoutChangingDistanceAndFitRecenters() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(2, 1.5f);
        Matrix4f before = camera.view();
        float distance = camera.distance();
        camera.pan(40, 20, 300);
        assertFalse(before.equals(camera.view(), 1e-6f));
        assertEquals(distance, camera.distance());
        camera.fit(2, 1.5f);
        assertTrue(before.equals(camera.view(), 1e-6f));
    }
}
