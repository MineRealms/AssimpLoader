package cn.minerealms.assimploader.client.transform;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldEditCameraOrbitTest {
    @Test void startsInFrontOfTheBodyAndLooksBackWithoutChangingItsHeading() {
        for (double heading : new double[]{0, 90, -175, 270}) {
            var camera = new WorldEditCameraOrbit();
            camera.reset(heading);
            Vector3d eye = camera.position(0, 1, 0, 1, new Vector3d());
            Vector3d front = new Vector3d(-Math.sin(Math.toRadians(heading)), 0, Math.cos(Math.toRadians(heading)));
            assertTrue(front.dot(new Vector3d(eye).sub(0, 1, 0)) > 0);
            Vector3d towardTarget = new Vector3d(0, 1, 0).sub(eye).normalize();
            assertEquals(0, towardTarget.distance(camera.forward(new Vector3d())), 1e-12);
        }
    }

    @Test void basisMatchesMinecraftAndStaysOrthonormalNearThePitchLimits() {
        var camera = new WorldEditCameraOrbit();
        for (double dy : new double[]{0, 1e100, -1e100}) {
            camera.orbit(731, dy);
            Vector3d forward = camera.forward(new Vector3d()), right = camera.right(new Vector3d()), up = camera.up(new Vector3d());
            assertEquals(1, forward.length(), 1e-12);
            assertEquals(1, right.length(), 1e-12);
            assertEquals(1, up.length(), 1e-12);
            assertEquals(0, forward.dot(right), 1e-12);
            assertEquals(0, forward.dot(up), 1e-12);
            assertEquals(0, right.dot(up), 1e-12);
            assertEquals(0, new Vector3d(right).cross(forward).distance(up), 1e-12);
            var vanillaRotation = new Quaterniond().rotationYXZ(-Math.toRadians(camera.yaw()), Math.toRadians(camera.pitch()), 0);
            assertEquals(0, vanillaRotation.transform(new Vector3d(0, 0, 1)).distance(forward), 1e-7);
            assertEquals(0, vanillaRotation.transform(new Vector3d(-1, 0, 0)).distance(right), 1e-7);
            assertEquals(0, vanillaRotation.transform(new Vector3d(0, 1, 0)).distance(up), 1e-7);
            assertTrue(Math.abs(camera.pitch()) <= 85);
            assertTrue(camera.yaw() >= -180 && camera.yaw() < 180);
        }
    }

    @Test void framingPlacesTheDynamicTargetAtRequestedScreenXAcrossAspectRatiosAndOrbits() {
        for (double tangent : new double[]{.15, .7, 2.8}) {
            for (double desired : new double[]{0, .28, .5, .9, 1}) {
                var camera = new WorldEditCameraOrbit();
                camera.reset(73);
                camera.orbit(81, -131);
                camera.frame(desired);
                Vector3d focus = new Vector3d(1000, 75, -380);
                Vector3d relative = new Vector3d(focus).sub(camera.position(focus.x, focus.y, focus.z, tangent, new Vector3d()));
                double depth = relative.dot(camera.forward(new Vector3d()));
                double ndcX = relative.dot(camera.right(new Vector3d())) / (depth * tangent);
                assertEquals(desired, (ndcX + 1) / 2, 1e-12);
                assertEquals(0, relative.dot(camera.up(new Vector3d())), 1e-12);
                assertEquals(camera.distance(), depth, 1e-12);
            }
        }
    }

    @Test void panFollowsTheScreenPlaneAndFocusClearsPanWithoutResettingOrbitOrZoom() {
        var camera = new WorldEditCameraOrbit();
        camera.reset(95);
        camera.orbit(78, 28);
        camera.zoom(3);
        double yaw = camera.yaw(), pitch = camera.pitch(), distance = camera.distance();
        camera.pan(100, 50, .01);
        Vector3d pan = camera.target(0, 0, 0, new Vector3d());
        assertEquals(-1, pan.dot(camera.right(new Vector3d())), 1e-12);
        assertEquals(.5, pan.dot(camera.up(new Vector3d())), 1e-12);
        assertEquals(0, pan.dot(camera.forward(new Vector3d())), 1e-12);
        camera.focus();
        assertEquals(new Vector3d(), camera.target(0, 0, 0, new Vector3d()));
        assertEquals(yaw, camera.yaw());
        assertEquals(pitch, camera.pitch());
        assertEquals(distance, camera.distance());
    }

    @Test void invalidAndExtremeInputsCannotCreateInvalidOrUnboundedCameraState() {
        var camera = new WorldEditCameraOrbit();
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            Vector3d initial = camera.position(1, 2, 3, 1, new Vector3d());
            camera.orbit(invalid, 1);
            camera.pan(invalid, 1, .01);
            camera.zoom(invalid);
            camera.lift(invalid);
            camera.frame(invalid);
            assertEquals(initial, camera.position(1, 2, 3, 1, new Vector3d()));
        }
        camera.zoom(1e100);
        assertEquals(WorldEditCameraOrbit.MIN_DISTANCE, camera.distance());
        camera.zoom(-1e100);
        assertEquals(WorldEditCameraOrbit.MAX_DISTANCE, camera.distance());
        camera.orbit(1e100, -1e100);
        camera.pan(1e100, -1e100, 1e100);
        camera.lift(-1e100);
        camera.frame(1e100);
        Vector3d pan = camera.target(0, 0, 0, new Vector3d());
        assertTrue(Math.abs(pan.x) <= WorldEditCameraOrbit.MAX_PAN);
        assertTrue(Math.abs(pan.y) <= WorldEditCameraOrbit.MAX_PAN);
        assertTrue(Math.abs(pan.z) <= WorldEditCameraOrbit.MAX_PAN);
        Vector3d position = camera.position(0, 0, 0, Double.POSITIVE_INFINITY, new Vector3d());
        assertTrue(position.isFinite());
        assertTrue(position.length() <= WorldEditCameraOrbit.MAX_DISTANCE * 2 + WorldEditCameraOrbit.MAX_PAN * Math.sqrt(3));
    }

    @Test void liftIsWorldVerticalAndMovingTargetDoesNotAccumulateDrift() {
        var camera = new WorldEditCameraOrbit();
        camera.orbit(144, 72);
        camera.lift(1);
        Vector3d pan = camera.target(0, 0, 0, new Vector3d());
        assertEquals(0, pan.x);
        assertEquals(0, pan.z);
        assertTrue(pan.y > 0);
        Vector3d first = camera.position(1, 2, 3, 1, new Vector3d());
        Vector3d second = camera.position(4, 8, -3, 1, new Vector3d());
        assertEquals(0, new Vector3d(second).sub(first).distance(new Vector3d(3, 6, -6)), 1e-12);
    }
}
