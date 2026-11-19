package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.client.WorkbenchRuntime;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.mojang.blaze3d.systems.RenderSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OculusOwnerCacheLifecycleTest {
    @BeforeEach void prepare() {
        if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
        OculusEntityBufferCache.clear();
    }
    @AfterEach void cleanup() { OculusEntityBufferCache.clear(); }

    @Test void clearingOneOwnersOverridesDropsOnlyItsResidentEntryAndReservation() throws Exception {
        var scene = scene();
        var a = new AnimationState("a", scene); var b = new AnimationState("b", scene);
        var document = WorkbenchDocument.defaults(scene, "test:resident", "model.obj", "");
        try (var ignored = WorkbenchRuntime.instance(a, scene, document)) {}
        try (var ignored = WorkbenchRuntime.instance(b, scene, document)) {}
        resident(a, 64); resident(b, 128);
        try {
            assertEquals(192, OculusEntityBufferCache.poseStatistics().residentBytes());
            try (var ignored = WorkbenchRuntime.instance(a, scene, null)) {
                assertNull(WorkbenchRuntime.document(scene));
                assertFalse(OculusEntityBufferCache.isPoseCached(a));
                assertTrue(OculusEntityBufferCache.isPoseCached(b));
                assertEquals(128, OculusEntityBufferCache.poseStatistics().residentBytes());
            }
            WorkbenchRuntime.release(a, scene);
            assertEquals(128, OculusEntityBufferCache.poseStatistics().residentBytes(), "Repeated release is idempotent");
            WorkbenchRuntime.release(b, scene);
            assertEquals(0, OculusEntityBufferCache.poseStatistics().residentBytes());
            assertEquals(0, OculusEntityBufferCache.poseStatistics().owners());
        } finally {
            WorkbenchRuntime.release(a, scene); WorkbenchRuntime.release(b, scene);
            a.close(); b.close();
        }
    }

    @Test void sourceInvalidationLeavesOtherScenesAndDoesNotSubtractReservationsTwice() throws Exception {
        var first = scene(); var second = scene();
        var a = new AnimationState("a", first); var b = new AnimationState("b", second);
        resident(a, 320); resident(b, 640);
        OculusEntityBufferCache.invalidateScene(first);
        assertFalse(OculusEntityBufferCache.isPoseCached(a));
        assertTrue(OculusEntityBufferCache.isPoseCached(b));
        assertEquals(640, OculusEntityBufferCache.poseStatistics().residentBytes());
        OculusEntityBufferCache.invalidateOwner(b);
        OculusEntityBufferCache.invalidateOwner(b);
        assertEquals(0, OculusEntityBufferCache.poseStatistics().residentBytes());
        assertEquals(0, OculusEntityBufferCache.poseStatistics().owners());
        a.close(); b.close();
    }

    // No GL buffers are allocated here: exercise owner eviction and budget accounting independently of a driver.
    @SuppressWarnings("unchecked")
    private static void resident(AnimationState owner, long bytes) throws Exception {
        Class<?> type = Class.forName(OculusEntityBufferCache.class.getName() + "$PoseEntry");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object entry = constructor.newInstance();
        var reservation = type.getDeclaredField("reservation"); reservation.setAccessible(true); reservation.setLong(entry, bytes);
        var entries = OculusEntityBufferCache.class.getDeclaredField("POSES"); entries.setAccessible(true);
        ((Map<AnimationState, Object>)entries.get(null)).put(owner, entry);
        var resident = OculusEntityBufferCache.class.getDeclaredField("poseResident"); resident.setAccessible(true);
        resident.setLong(null, resident.getLong(null) + bytes);
    }

    private static AssimpScene scene() {
        return new AssimpModelLoader().loadModelFromMemory("resident-" + UUID.randomUUID() + ".obj",
                "o triangle\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
    }
}
