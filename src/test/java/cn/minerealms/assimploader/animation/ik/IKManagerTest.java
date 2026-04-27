package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.Bone;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IKManager class.
 * Tests chain creation, removal, and management.
 */
class IKManagerTest {

    private Skeleton skeleton;
    private IKManager manager;

    @BeforeEach
    void setUp() {
        // Create simple skeleton
        var root = new Bone("Root", new Matrix4f().identity());
        var bone1 = new Bone("Bone1", new Matrix4f().identity());
        var bone2 = new Bone("Bone2", new Matrix4f().identity());
        var bone3 = new Bone("Bone3", new Matrix4f().identity());

        root.addChild(bone1);
        bone1.addChild(bone2);
        bone2.addChild(bone3);

        skeleton = new Skeleton(root);
        manager = new IKManager(skeleton);
    }

    @Test
    void testCreateChain() {
        IKChain chain = manager.createChain("testChain",
            Arrays.asList("Bone1", "Bone2", "Bone3"),
            IKSolver.Type.FABRIK);

        assertNotNull(chain);
        assertEquals("testChain", chain.getName());
        assertEquals(3, chain.getLength());
        assertEquals(IKSolver.Type.FABRIK, chain.getSolver().getType());
    }

    @Test
    void testCreateChainFromRange() {
        IKChain chain = manager.createChainFromRange("testChain",
            "Bone1", "Bone3",
            IKSolver.Type.TWO_BONE);

        assertNotNull(chain);
        assertEquals(3, chain.getLength());
    }

    @Test
    void testCreateDuplicateChain() {
        manager.createChain("testChain",
            Arrays.asList("Bone1", "Bone2"),
            IKSolver.Type.CCD);

        assertThrows(IllegalArgumentException.class, () -> {
            manager.createChain("testChain",
                Arrays.asList("Bone2", "Bone3"),
                IKSolver.Type.CCD);
        });
    }

    @Test
    void testCreateChainWithInvalidBone() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.createChain("testChain",
                Arrays.asList("Bone1", "NonExistent"),
                IKSolver.Type.FABRIK);
        });
    }

    @Test
    void testRemoveChain() {
        manager.createChain("testChain",
            Arrays.asList("Bone1", "Bone2"),
            IKSolver.Type.CCD);

        assertTrue(manager.removeChain("testChain"));
        assertNull(manager.getChain("testChain"));
        assertFalse(manager.removeChain("testChain"));
    }

    @Test
    void testGetChain() {
        IKChain chain = manager.createChain("testChain",
            Arrays.asList("Bone1", "Bone2"),
            IKSolver.Type.LOOK_AT);

        assertEquals(chain, manager.getChain("testChain"));
        assertNull(manager.getChain("nonExistent"));
    }

    @Test
    void testEnableDisable() {
        assertTrue(manager.isEnabled());

        manager.setEnabled(false);
        assertFalse(manager.isEnabled());

        manager.setEnabled(true);
        assertTrue(manager.isEnabled());
    }

    @Test
    void testGetChainNames() {
        manager.createChain("chain1", Arrays.asList("Bone1", "Bone2"), IKSolver.Type.CCD);
        manager.createChain("chain2", Arrays.asList("Bone2", "Bone3"), IKSolver.Type.FABRIK);

        var names = manager.getChainNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("chain1"));
        assertTrue(names.contains("chain2"));
    }

    @Test
    void testGetAllChains() {
        manager.createChain("chain1", Arrays.asList("Bone1", "Bone2"), IKSolver.Type.CCD);
        manager.createChain("chain2", Arrays.asList("Bone2", "Bone3"), IKSolver.Type.FABRIK);

        var chains = manager.getAllChains();
        assertEquals(2, chains.size());
    }

    @Test
    void testClear() {
        manager.createChain("chain1", Arrays.asList("Bone1", "Bone2"), IKSolver.Type.CCD);
        manager.createChain("chain2", Arrays.asList("Bone2", "Bone3"), IKSolver.Type.FABRIK);

        manager.clear();
        assertEquals(0, manager.getChainCount());
    }

    @Test
    void testGetSkeleton() {
        assertEquals(skeleton, manager.getSkeleton());
    }
}
