package cn.minerealms.assimpshowcase.showcase;

import cn.minerealms.assimploader.render.ModelVisibility;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ModelVisibilityTest {
    @Test
    void largeBoundsUseTheClosestSurfaceInsteadOfTheAnchorDistance() {
        AABB bounds = ModelVisibility.worldBounds(new AABB(-200, 0, -2, 200, 12, 2), BlockPos.ZERO);
        Vec3 camera = new Vec3(205, 6, 0);
        assertTrue(camera.distanceTo(Vec3.atCenterOf(BlockPos.ZERO)) > 128);
        assertTrue(ModelVisibility.withinDistance(bounds, camera, 16));
        assertFalse(ModelVisibility.withinDistance(bounds, new Vec3(218, 6, 0), 16));
        assertTrue(ModelVisibility.withinDistance(bounds, new Vec3(0, 10, 0), 0));
        assertFalse(ModelVisibility.withinDistance(bounds, new Vec3(0, 29, 0), 16));
    }

    @Test
    void giantOverrideIsOptInAndUsesTheLargestActualDimension() {
        AABB giant = new AABB(-10, 0, -1, 10, 8, 1);
        assertFalse(ModelVisibility.bypassCulling(giant, false, 16));
        assertTrue(ModelVisibility.bypassCulling(giant, true, 16));
        assertFalse(ModelVisibility.bypassCulling(giant, true, 21));
        assertTrue(ModelVisibility.bypassCulling(new AABB(0, 0, 0, 1, 16, 1), true, 16));
        assertFalse(ModelVisibility.bypassCulling(new AABB(0, 0, 0, 1, 15.9, 1), true, 16));
    }

    @Test
    void localBoundsBecomeTheSameWorldCoordinatesUsedByTheRenderer() {
        AABB bounds = ModelVisibility.worldBounds(new AABB(-10, 0, -2, 10, 9, 2), new BlockPos(30, 64, -50));
        assertEquals(new AABB(20.5, 64, -51.5, 40.5, 73, -47.5), bounds);
        assertTrue(bounds.inflate(0.25).contains(new Vec3(40.6, 72, -48)));
    }

    @Test
    void forcedFrustumPolicyKeepsExactlyTheSameFiniteGeometryBounds() {
        AABB model = new AABB(-10, 0, -5, 10, 8, 5);
        BlockPos anchor = new BlockPos(30, 64, -50);
        AABB expected = ModelVisibility.worldBounds(model, anchor).inflate(0.25);
        AABB normal = ModelVisibility.renderBounds(model, anchor, true, false, 16);
        AABB forced = ModelVisibility.renderBounds(model, anchor, true, true, 16);
        assertEquals(expected, normal);
        assertEquals(expected, forced);
        assertTrue(Double.isFinite(forced.getSize()));
        assertFalse(ModelVisibility.bypassFrustum(normal));
        assertTrue(ModelVisibility.bypassFrustum(forced));
        assertFalse(ModelVisibility.bypassFrustum(expected));
        assertFalse(ModelVisibility.bypassFrustum(new AABB(-1e8, -1e8, -1e8, 1e8, 1e8, 1e8)));
    }

    @Test
    void frustumDecisionsFollowCurrentSettingsAndReloadedModelSize() {
        AABB small = new AABB(0, 0, 0, 1, 1, 1);
        AABB giant = new AABB(0, 0, 0, 20, 8, 10);
        assertFalse(ModelVisibility.bypassFrustum(ModelVisibility.renderBounds(small, BlockPos.ZERO, true, true, 16)));
        assertTrue(ModelVisibility.bypassFrustum(ModelVisibility.renderBounds(giant, BlockPos.ZERO, true, true, 16)));
        assertFalse(ModelVisibility.bypassFrustum(ModelVisibility.renderBounds(giant, BlockPos.ZERO, true, false, 16)));
        assertFalse(ModelVisibility.bypassFrustum(ModelVisibility.renderBounds(giant, BlockPos.ZERO, true, true, 21)));
        assertTrue(ModelVisibility.bypassFrustum(ModelVisibility.renderBounds(small, BlockPos.ZERO, false, false, 16)));
    }

    @Test
    void dynamicParentTransformsAndInstancingMatchStaticImportBounds() {
        byte[] sceneData = instancedScene();
        var loader = new AssimpModelLoader();
        var dynamic = loader.loadModelFromMemory("bounds.gltf", sceneData, ModelImportMode.DYNAMIC);
        var baked = loader.loadModelFromMemory("bounds.gltf", sceneData, ModelImportMode.STATIC);
        assertNotNull(dynamic);
        assertNotNull(baked);
        AABB actual = ModelVisibility.modelBounds(dynamic);
        AABB expected = ModelVisibility.modelBounds(baked);
        assertEquals(86, actual.minX, 0.001);
        assertEquals(201, actual.maxX, 0.001);
        assertEquals(expected.minX, actual.minX, 0.001);
        assertEquals(expected.minY, actual.minY, 0.001);
        assertEquals(expected.minZ, actual.minZ, 0.001);
        assertEquals(expected.maxX, actual.maxX, 0.001);
        assertEquals(expected.maxY, actual.maxY, 0.001);
        assertEquals(expected.maxZ, actual.maxZ, 0.001);
        assertSame(actual, ModelVisibility.modelBounds(dynamic));
        assertTrue(ModelVisibility.bypassCulling(actual, true, 16));
    }

    private static byte[] instancedScene() {
        ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) vertices.putFloat(value);
        String encoded = Base64.getEncoder().encodeToString(vertices.array());
        return ("""
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"translation":[80,0,0],"scale":[2,3,4],"children":[1]},
                          {"translation":[3,4,5],"mesh":0},{"translation":[200,0,0],"mesh":0}],
                 "scenes":[{"nodes":[0,2]}],"scene":0}
                """).formatted(encoded).getBytes(StandardCharsets.UTF_8);
    }
}
