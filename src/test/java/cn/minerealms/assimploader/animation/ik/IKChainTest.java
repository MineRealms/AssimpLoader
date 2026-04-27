package cn.minerealms.assimploader.animation.ik;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IKChain class.
 * Tests target setting, weight blending, enable/disable, and dirty flag.
 */
class IKChainTest {

    private Skeleton skeleton;
    private IKChain chain;

    @BeforeEach
    void setUp() {
        // Create simple 3-bone chain
        var root = new cn.minerealms.assimploader.animation.Bone("Root", new org.joml.Matrix4f().identity());
        var bone1 = new cn.minerealms.assimploader.animation.Bone("Bone1", new org.joml.Matrix4f().identity());
        var bone2 = new cn.minerealms.assimploader.animation.Bone("Bone2", new org.joml.Matrix4f().identity());
        var bone3 = new cn.minerealms.assimploader.animation.Bone("Bone3", new org.joml.Matrix4f().identity());

        root.addChild(bone1);
        bone1.addChild(bone2);
        bone2.addChild(bone3);

        skeleton = new Skeleton(root);

        // Create IK chain
        var manager = new IKManager(skeleton);
        chain = manager.createChain("testChain",
            java.util.Arrays.asList("Bone1", "Bone2", "Bone3"),
            IKSolver.Type.TWO_BONE);
    }

    @Test
    void testInitialState() {
        assertEquals("testChain", chain.getName());
        assertEquals(3, chain.getLength());
        assertEquals(1.0f, chain.getWeight());
        assertTrue(chain.isEnabled());
        assertTrue(chain.isDirty());
    }

    @Test
    void testSetTarget() {
        Vector3f target = new Vector3f(1, 2, 3);
        chain.setTargetPosition(target);

        Vector3f retrieved = chain.getTargetPosition();
        assertEquals(target.x, retrieved.x, 0.001f);
        assertEquals(target.y, retrieved.y, 0.001f);
        assertEquals(target.z, retrieved.z, 0.001f);
        assertTrue(chain.isDirty());
    }

    @Test
    void testSetWeight() {
        chain.setWeight(0.5f);
        assertEquals(0.5f, chain.getWeight(), 0.001f);

        // Test clamping
        chain.setWeight(-0.5f);
        assertEquals(0.0f, chain.getWeight(), 0.001f);

        chain.setWeight(1.5f);
        assertEquals(1.0f, chain.getWeight(), 0.001f);
    }

    @Test
    void testEnableDisable() {
        chain.setEnabled(false);
        assertFalse(chain.isEnabled());

        chain.setEnabled(true);
        assertTrue(chain.isEnabled());
        assertTrue(chain.isDirty());
    }

    @Test
    void testGetBones() {
        assertEquals(3, chain.getBones().size());
        assertEquals("Bone1", chain.getRootBone().getName());
        assertEquals("Bone3", chain.getTipBone().getName());
    }

    @Test
    void testMarkDirty() {
        chain.solve(); // Clear dirty flag
        assertFalse(chain.isDirty());

        chain.markDirty();
        assertTrue(chain.isDirty());
    }
}
