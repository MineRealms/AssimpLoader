package cn.minerealms.assimploader.animation.ik.controllers;

import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.Skeleton;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for high-level IK controllers (Phase 4).
 */
class IKControllerTest {

    private Skeleton skeleton;
    private IKManager ikManager;
    private Bone root;
    private Bone neck;
    private Bone head;
    private Bone leftShoulder;
    private Bone leftElbow;
    private Bone leftHand;
    private Bone rightShoulder;
    private Bone rightElbow;
    private Bone rightHand;
    private Bone leftHip;
    private Bone leftKnee;
    private Bone leftAnkle;
    private Bone rightHip;
    private Bone rightKnee;
    private Bone rightAnkle;

    @BeforeEach
    void setUp() {
        // Create a humanoid skeleton
        root = new Bone("Root", new Matrix4f().identity());

        // Head
        neck = new Bone("Neck", new Matrix4f().identity());
        head = new Bone("Head", new Matrix4f().identity());

        // Left arm
        leftShoulder = new Bone("LeftShoulder", new Matrix4f().identity());
        leftElbow = new Bone("LeftElbow", new Matrix4f().identity());
        leftHand = new Bone("LeftHand", new Matrix4f().identity());

        // Right arm
        rightShoulder = new Bone("RightShoulder", new Matrix4f().identity());
        rightElbow = new Bone("RightElbow", new Matrix4f().identity());
        rightHand = new Bone("RightHand", new Matrix4f().identity());

        // Left leg
        leftHip = new Bone("LeftHip", new Matrix4f().identity());
        leftKnee = new Bone("LeftKnee", new Matrix4f().identity());
        leftAnkle = new Bone("LeftAnkle", new Matrix4f().identity());

        // Right leg
        rightHip = new Bone("RightHip", new Matrix4f().identity());
        rightKnee = new Bone("RightKnee", new Matrix4f().identity());
        rightAnkle = new Bone("RightAnkle", new Matrix4f().identity());

        // Build hierarchy
        root.addChild(neck);
        neck.addChild(head);

        root.addChild(leftShoulder);
        leftShoulder.addChild(leftElbow);
        leftElbow.addChild(leftHand);

        root.addChild(rightShoulder);
        rightShoulder.addChild(rightElbow);
        rightElbow.addChild(rightHand);

        root.addChild(leftHip);
        leftHip.addChild(leftKnee);
        leftKnee.addChild(leftAnkle);

        root.addChild(rightHip);
        rightHip.addChild(rightKnee);
        rightKnee.addChild(rightAnkle);

        // Set transforms
        root.setLocalTransform(new Matrix4f().identity());
        neck.setLocalTransform(new Matrix4f().translation(0, 1.5f, 0));
        head.setLocalTransform(new Matrix4f().translation(0, 0.3f, 0));

        leftShoulder.setLocalTransform(new Matrix4f().translation(-0.5f, 1.4f, 0));
        leftElbow.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));
        leftHand.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));

        rightShoulder.setLocalTransform(new Matrix4f().translation(0.5f, 1.4f, 0));
        rightElbow.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));
        rightHand.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));

        leftHip.setLocalTransform(new Matrix4f().translation(-0.3f, 0, 0));
        leftKnee.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));
        leftAnkle.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));

        rightHip.setLocalTransform(new Matrix4f().translation(0.3f, 0, 0));
        rightKnee.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));
        rightAnkle.setLocalTransform(new Matrix4f().translation(0, -0.5f, 0));

        // Update transforms
        root.updateTransforms();

        // Create skeleton and IK manager
        skeleton = new Skeleton(root);
        ikManager = new IKManager(skeleton);
    }

    @Test
    void testHeadLookAtController_Creation() {
        HeadLookAtController controller = new HeadLookAtController(ikManager, "Head");
        assertNotNull(controller);
        assertTrue(controller.isEnabled());
        assertEquals(1.0f, controller.getWeight(), 0.001f);
    }

    @Test
    void testHeadLookAtController_WithNeck() {
        HeadLookAtController controller = new HeadLookAtController(ikManager, "Neck", "Head");
        assertNotNull(controller);
        assertNotNull(controller.getChain());
        assertEquals(2, controller.getChain().getBones().size());
    }

    @Test
    void testHeadLookAtController_SetTarget() {
        HeadLookAtController controller = new HeadLookAtController(ikManager, "Head");
        Vector3f target = new Vector3f(1, 2, 3);
        controller.setTarget(target);
        controller.update(0.016f);

        // The controller only sets the chain target; solving happens in the manager.
        ikManager.setEnabled(true);
        ikManager.updateAll();

        // Chain should be updated
        assertFalse(controller.getChain().isDirty());
    }

    @Test
    void testHeadLookAtController_EnableDisable() {
        HeadLookAtController controller = new HeadLookAtController(ikManager, "Head");

        assertTrue(controller.isEnabled());

        controller.setEnabled(false);
        assertFalse(controller.isEnabled());

        controller.setEnabled(true);
        assertTrue(controller.isEnabled());
    }

    @Test
    void testHandIKController_Creation() {
        HandIKController controller = new HandIKController(
            ikManager,
            "LeftShoulder", "LeftElbow", "LeftHand",
            "RightShoulder", "RightElbow", "RightHand"
        );

        assertNotNull(controller);
        assertFalse(controller.isLeftHandEnabled());
        assertFalse(controller.isRightHandEnabled());
    }

    @Test
    void testHandIKController_EnableDisable() {
        HandIKController controller = new HandIKController(
            ikManager,
            "LeftShoulder", "LeftElbow", "LeftHand",
            "RightShoulder", "RightElbow", "RightHand"
        );

        controller.enableLeftHand();
        assertTrue(controller.isLeftHandEnabled());

        controller.enableRightHand();
        assertTrue(controller.isRightHandEnabled());

        controller.disableLeftHand();
        assertFalse(controller.isLeftHandEnabled());

        controller.disableRightHand();
        assertFalse(controller.isRightHandEnabled());
    }

    @Test
    void testHandIKController_SetTargets() {
        HandIKController controller = new HandIKController(
            ikManager,
            "LeftShoulder", "LeftElbow", "LeftHand",
            "RightShoulder", "RightElbow", "RightHand"
        );

        controller.enableLeftHand();
        controller.enableRightHand();

        Vector3f leftTarget = new Vector3f(1, 1, 1);
        Vector3f rightTarget = new Vector3f(-1, 1, 1);

        controller.setLeftHandTarget(leftTarget, null);
        controller.setRightHandTarget(rightTarget, null);

        ikManager.updateAll();

        assertFalse(controller.getLeftHandChain().isDirty());
        assertFalse(controller.getRightHandChain().isDirty());
    }

    @Test
    void testHandIKController_BlendWeights() {
        HandIKController controller = new HandIKController(
            ikManager,
            "LeftShoulder", "LeftElbow", "LeftHand",
            "RightShoulder", "RightElbow", "RightHand"
        );

        controller.setLeftHandWeight(0.5f);
        controller.setRightHandWeight(0.8f);

        assertEquals(0.5f, controller.getLeftHandWeight(), 0.001f);
        assertEquals(0.8f, controller.getRightHandWeight(), 0.001f);
    }

    @Test
    void testFootIKController_Creation() {
        FootIKController controller = new FootIKController(
            ikManager,
            "LeftHip", "LeftKnee", "LeftAnkle",
            "RightHip", "RightKnee", "RightAnkle"
        );

        assertNotNull(controller);
        assertFalse(controller.isLeftFootEnabled());
        assertFalse(controller.isRightFootEnabled());
    }

    @Test
    void testFootIKController_EnableDisable() {
        FootIKController controller = new FootIKController(
            ikManager,
            "LeftHip", "LeftKnee", "LeftAnkle",
            "RightHip", "RightKnee", "RightAnkle"
        );

        controller.enableLeftFoot();
        assertTrue(controller.isLeftFootEnabled());

        controller.enableRightFoot();
        assertTrue(controller.isRightFootEnabled());

        controller.disableLeftFoot();
        assertFalse(controller.isLeftFootEnabled());

        controller.disableRightFoot();
        assertFalse(controller.isRightFootEnabled());
    }

    @Test
    void testFootIKController_ManualTargets() {
        FootIKController controller = new FootIKController(
            ikManager,
            "LeftHip", "LeftKnee", "LeftAnkle",
            "RightHip", "RightKnee", "RightAnkle"
        );

        controller.enableLeftFoot();
        controller.enableRightFoot();

        Vector3f leftTarget = new Vector3f(-0.3f, -1.0f, 0);
        Vector3f rightTarget = new Vector3f(0.3f, -1.0f, 0);

        controller.setLeftFootTarget(leftTarget);
        controller.setRightFootTarget(rightTarget);

        ikManager.updateAll();

        assertFalse(controller.getLeftFootChain().isDirty());
        assertFalse(controller.getRightFootChain().isDirty());
    }

    @Test
    void testFootIKController_GroundRaycast() {
        FootIKController controller = new FootIKController(
            ikManager,
            "LeftHip", "LeftKnee", "LeftAnkle",
            "RightHip", "RightKnee", "RightAnkle"
        );

        // Set up simple ground raycast (always returns y = -1.0)
        controller.setGroundRaycast(pos -> new Vector3f(pos.x, -1.0f, pos.z));
        controller.setFootOffset(0.1f);

        controller.enableLeftFoot();
        controller.enableRightFoot();

        controller.update(0.016f);

        // The controller only sets the chain targets; solving happens in the manager.
        ikManager.setEnabled(true);
        ikManager.updateAll();

        // Chains should be updated
        assertFalse(controller.getLeftFootChain().isDirty());
        assertFalse(controller.getRightFootChain().isDirty());
    }

    @Test
    void testFootIKController_BlendWeights() {
        FootIKController controller = new FootIKController(
            ikManager,
            "LeftHip", "LeftKnee", "LeftAnkle",
            "RightHip", "RightKnee", "RightAnkle"
        );

        controller.setLeftFootWeight(0.7f);
        controller.setRightFootWeight(0.9f);

        assertEquals(0.7f, controller.getLeftFootWeight(), 0.001f);
        assertEquals(0.9f, controller.getRightFootWeight(), 0.001f);
    }
}
