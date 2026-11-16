package cn.minerealms.assimploader.animation;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ClientAnimationManagerTest {
    @Test void aliasesForTheSameOwnerSceneReusePlaybackWithoutDoubleUpdates() {
        var manager=new ClientAnimationManager();
        var scene=AnimationTestScenes.create();var owner=new Object();
        var state=manager.getOrCreateAnimationState(owner,"short_name",scene);
        assertSame(state,manager.getOrCreateAnimationState(owner,"example:models/short_name.glb",scene));
        state.playAnimation("A");manager.update(.5f);
        assertEquals(.5,state.getSimulationTime(),1e-6);
        assertNotSame(state,manager.getOrCreateAnimationState(new Object(),"short_name",scene));
        manager.release(owner);assertTrue(state.isClosed());manager.clear();
    }

    @Test
    void invalidResourceAndAssetBindingFallBackOnceUntilReload() {
        var badClip = ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"test:model","states":{"idle":{"clip":"absent"}}}
                """));
        for (boolean parseFailure : new boolean[]{true, false}) {
            AtomicInteger reads = new AtomicInteger();
            var manager = new ClientAnimationManager(resource -> {
                reads.incrementAndGet();
                if (parseFailure) throw new IllegalArgumentException("malformed resource");
                return Optional.of(badClip);
            });
            var scene = AnimationTestScenes.create();
            Object ownerA = new Object(), ownerB = new Object();
            var first = manager.getOrCreateAnimationState(ownerA,"same",scene);
            var second = manager.getOrCreateAnimationState(ownerB,"same",scene);
            assertTrue(first.isInitialized());
            assertTrue(second.isInitialized());
            assertNotSame(first,second);
            assertEquals(1,reads.get());
            first.playAnimation("A");
            manager.update(.5f);
            assertEquals(.5,first.getPoseSnapshot().getNodeTransform("Joint").m30(),1e-4);
            manager.clear();
            assertTrue(first.isClosed());
            manager.getOrCreateAnimationState(ownerA,"same",scene);
            assertEquals(2,reads.get());
            manager.clear();
        }
    }

    @Test
    void equalOwnersKeepIndependentPlaybackAndRelease() {
        var manager = new ClientAnimationManager();
        var scene = AnimationTestScenes.create();
        record Owner(int value) {}
        Owner ownerA = new Owner(1), ownerB = new Owner(1);
        assertEquals(ownerA, ownerB);
        var first = manager.getOrCreateAnimationState(ownerA, "same", scene);
        var second = manager.getOrCreateAnimationState(ownerB, "same", scene);

        assertNotSame(first, second);
        assertSame(first, manager.getOrCreateAnimationState(ownerA, "same", scene));
        assertSame(second, manager.getOrCreateAnimationState(ownerB, "same", scene));
        first.playAnimation("A");
        second.playAnimation("B");
        manager.update(.5f);
        assertEquals(.5, first.getPoseSnapshot().getNodeTransform("Joint").m30(), 1e-4);
        assertEquals(20.5, second.getPoseSnapshot().getNodeTransform("Joint").m30(), 1e-4);
        manager.release(ownerA);
        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
        assertSame(second, manager.getOrCreateAnimationState(ownerB, "same", scene));
        manager.clear();
    }

    @Test
    void collectedOwnerQueueClosesTheAbandonedState() throws ReflectiveOperationException {
        var manager = new ClientAnimationManager();
        Object owner = new Object();
        var state = manager.getOrCreateAnimationState(owner, "same", AnimationTestScenes.create());
        var field = ClientAnimationManager.class.getDeclaredField("instances");
        field.setAccessible(true);
        Map<?, ?> instances = (Map<?, ?>) field.get(manager);
        WeakReference<?> reference = (WeakReference<?>) instances.keySet().iterator().next();
        // Deliver the reference-queue event deterministically without depending on GC timing.
        reference.clear();
        assertTrue(reference.enqueue());

        manager.update(0);

        assertTrue(state.isClosed());
        assertTrue(instances.isEmpty());
    }

    @Test
    void ownersShareAssetsButNotPlaybackAndReleaseClosesOnlyOneOwner() {
        var manager = new ClientAnimationManager();
        var scene = AnimationTestScenes.create();
        Object ownerA = new Object(), ownerB = new Object();
        var first = manager.getOrCreateAnimationState(ownerA, "same", scene);
        var second = manager.getOrCreateAnimationState(ownerB, "same", scene);
        assertNotSame(first, second);
        assertSame(scene, first.getScene());
        assertSame(first, manager.getOrCreateAnimationState(ownerA, "same", scene));
        first.playAnimation("A");
        second.playAnimation("B");
        manager.update(.5f);
        assertEquals(.5, first.getPoseSnapshot().getNodeTransform("Joint").m30(), 1e-4);
        assertEquals(20.5, second.getPoseSnapshot().getNodeTransform("Joint").m30(), 1e-4);
        manager.release(ownerA);
        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
        manager.update(.5f);
        assertEquals(21, second.getPoseSnapshot().getNodeTransform("Joint").m30(), 1e-4);
        manager.clear();
        assertTrue(second.isClosed());
    }

    @Test
    void replacingSceneClosesOldInstanceAndNullSceneRemovesIt() {
        var manager = new ClientAnimationManager();
        Object owner = new Object();
        var old = manager.getOrCreateAnimationState(owner, "same", AnimationTestScenes.create());
        var replacement = manager.getOrCreateAnimationState(owner, "same", AnimationTestScenes.create());
        assertTrue(old.isClosed());
        assertNotSame(old, replacement);
        assertNull(manager.getOrCreateAnimationState(owner, "same", null));
        assertTrue(replacement.isClosed());
        manager.clear();
    }

    @Test
    void closedInstancesAreRecreatedAndInvalidDeltaDoesNotAdvance() {
        var manager = new ClientAnimationManager();
        var scene = AnimationTestScenes.create();
        Object owner = new Object();
        var old = manager.getOrCreateAnimationState(owner, "same", scene);
        old.close();
        var current = manager.getOrCreateAnimationState(owner, "same", scene);
        assertNotSame(old, current);
        var pose = current.getPoseSnapshot();
        assertThrows(IllegalArgumentException.class, () -> manager.update(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> manager.update(-1));
        assertSame(pose, current.getPoseSnapshot());
        manager.clear();
    }
}
