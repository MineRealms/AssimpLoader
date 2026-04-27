package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import cn.minerealms.assimploader.animation.ik.Skeleton;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AnimationPlayer with IK system (Phase 3).
 *
 * Note: These tests focus on IK integration without requiring a full AnimationPlayer.
 * They test the IK system components directly.
 */
class AnimationPlayerIKIntegrationTest {

    private Bone rootBone;
    private Bone shoulder;
    private Bone elbow;
    private Bone hand;

    @BeforeEach
    void setUp() {
        // Create a simple arm hierarchy
        rootBone = new Bone("Root", new Matrix4f().identity());
        shoulder = new Bone("Shoulder", new Matrix4f().identity());
        elbow = new Bone("Elbow", new Matrix4f().identity());
        hand = new Bone("Hand", new Matrix4f().identity());

        rootBone.addChild(shoulder);
        shoulder.addChild(elbow);
        elbow.addChild(hand);

        // Set initial transforms with proper spacing
        rootBone.setLocalTransform(new Matrix4f().identity());
        shoulder.setLocalTransform(new Matrix4f().translation(0, 1, 0));
        elbow.setLocalTransform(new Matrix4f().translation(0, 1, 0));
        hand.setLocalTransform(new Matrix4f().translation(0, 1, 0));

        // Update transforms to calculate global positions
        rootBone.updateTransforms();
    }

    @Test
    void testIKInitialization() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);

        assertNotNull(ikManager);
        assertNotNull(skeleton);
        assertTrue(ikManager.isEnabled());
    }

    @Test
    void testIKEnableDisable() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);

        assertTrue(ikManager.isEnabled());

        ikManager.setEnabled(false);
        assertFalse(ikManager.isEnabled());

        ikManager.setEnabled(true);
        assertTrue(ikManager.isEnabled());
    }

    @Test
    void testIKManagerAccess() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);

        // Create an IK chain
        IKChain chain = ikManager.createChain(
            "arm",
            Arrays.asList("Shoulder", "Elbow", "Hand"),
            IKSolver.Type.FABRIK
        );

        assertNotNull(chain);
        assertEquals("arm", chain.getName());
        assertEquals(3, chain.getBones().size());
    }

    @Test
    void testSkeletonAccess() {
        Skeleton skeleton = new Skeleton(rootBone);

        // Verify bone lookup
        assertEquals(shoulder, skeleton.getBone("Shoulder"));
        assertEquals(elbow, skeleton.getBone("Elbow"));
        assertEquals(hand, skeleton.getBone("Hand"));
    }

    @Test
    void testUpdateWithIKDisabled() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);
        ikManager.setEnabled(false);

        IKChain chain = ikManager.createChain(
            "arm",
            Arrays.asList("Shoulder", "Elbow", "Hand"),
            IKSolver.Type.FABRIK
        );

        chain.setTarget(new Vector3f(1, 1, 1), null);

        // Update should not apply IK
        ikManager.updateAll();

        // IK should not have been applied (chain remains dirty)
        assertTrue(chain.isDirty());
    }

    @Test
    void testUpdateWithIKEnabled() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);
        ikManager.setEnabled(true);

        IKChain chain = ikManager.createChain(
            "arm",
            Arrays.asList("Shoulder", "Elbow", "Hand"),
            IKSolver.Type.FABRIK
        );

        chain.setTarget(new Vector3f(1, 1, 1), null);

        // Update should apply IK
        ikManager.updateAll();

        // IK should have been applied (chain no longer dirty)
        assertFalse(chain.isDirty());
    }

    @Test
    void testMultipleIKChains() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);

        // Create multiple chains
        IKChain armChain = ikManager.createChain(
            "arm",
            Arrays.asList("Shoulder", "Elbow", "Hand"),
            IKSolver.Type.FABRIK
        );

        IKChain lookAtChain = ikManager.createChain(
            "lookAt",
            Arrays.asList("Shoulder"),
            IKSolver.Type.LOOK_AT
        );

        assertNotNull(armChain);
        assertNotNull(lookAtChain);
        assertEquals(2, ikManager.getChainCount());

        armChain.setTarget(new Vector3f(1, 1, 1), null);
        lookAtChain.setTarget(new Vector3f(0, 0, 1), null);

        ikManager.updateAll();

        // Both chains should be solved
        assertFalse(armChain.isDirty());
        assertFalse(lookAtChain.isDirty());
    }

    @Test
    void testIKChainBlending() {
        Skeleton skeleton = new Skeleton(rootBone);
        IKManager ikManager = new IKManager(skeleton);

        IKChain chain = ikManager.createChain(
            "arm",
            Arrays.asList("Shoulder", "Elbow", "Hand"),
            IKSolver.Type.FABRIK
        );

        // Set blend weight
        chain.setWeight(0.5f);
        assertEquals(0.5f, chain.getWeight(), 0.001f);

        chain.setTarget(new Vector3f(1, 1, 1), null);

        ikManager.updateAll();

        // Chain should be solved with blending
        assertFalse(chain.isDirty());
    }
}
