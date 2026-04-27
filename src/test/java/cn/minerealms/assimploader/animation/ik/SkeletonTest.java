package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.Bone;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Skeleton class.
 * Tests bone lookup, chain extraction, and hierarchy traversal.
 */
class SkeletonTest {

    private Skeleton skeleton;
    private Bone root;
    private Bone spine;
    private Bone chest;
    private Bone leftShoulder;
    private Bone leftElbow;
    private Bone leftHand;
    private Bone rightShoulder;
    private Bone rightElbow;
    private Bone rightHand;

    @BeforeEach
    void setUp() {
        // Create a simple humanoid skeleton hierarchy:
        //       Root
        //         |
        //       Spine
        //         |
        //       Chest
        //      /     \
        // LeftShoulder  RightShoulder
        //     |              |
        // LeftElbow      RightElbow
        //     |              |
        // LeftHand       RightHand

        root = new Bone("Root", new Matrix4f().identity());
        spine = new Bone("Spine", new Matrix4f().identity());
        chest = new Bone("Chest", new Matrix4f().identity());
        leftShoulder = new Bone("LeftShoulder", new Matrix4f().identity());
        leftElbow = new Bone("LeftElbow", new Matrix4f().identity());
        leftHand = new Bone("LeftHand", new Matrix4f().identity());
        rightShoulder = new Bone("RightShoulder", new Matrix4f().identity());
        rightElbow = new Bone("RightElbow", new Matrix4f().identity());
        rightHand = new Bone("RightHand", new Matrix4f().identity());

        // Build hierarchy
        root.addChild(spine);
        spine.addChild(chest);
        chest.addChild(leftShoulder);
        leftShoulder.addChild(leftElbow);
        leftElbow.addChild(leftHand);
        chest.addChild(rightShoulder);
        rightShoulder.addChild(rightElbow);
        rightElbow.addChild(rightHand);

        skeleton = new Skeleton(root);
    }

    @Test
    void testGetBone() {
        assertEquals(root, skeleton.getBone("Root"));
        assertEquals(leftHand, skeleton.getBone("LeftHand"));
        assertEquals(rightElbow, skeleton.getBone("RightElbow"));
        assertNull(skeleton.getBone("NonExistent"));
    }

    @Test
    void testGetBoneChain_SimpleChain() {
        // Test left arm chain
        List<Bone> chain = skeleton.getBoneChain("LeftShoulder", "LeftHand");
        assertEquals(3, chain.size());
        assertEquals(leftShoulder, chain.get(0));
        assertEquals(leftElbow, chain.get(1));
        assertEquals(leftHand, chain.get(2));
    }

    @Test
    void testGetBoneChain_SameBone() {
        List<Bone> chain = skeleton.getBoneChain("LeftElbow", "LeftElbow");
        assertEquals(1, chain.size());
        assertEquals(leftElbow, chain.get(0));
    }

    @Test
    void testGetBoneChain_CrossBranch() {
        // Test chain across branches (left hand to right hand)
        List<Bone> chain = skeleton.getBoneChain("LeftHand", "RightHand");

        // Expected: LeftHand -> LeftElbow -> LeftShoulder -> Chest -> RightShoulder -> RightElbow -> RightHand
        assertEquals(7, chain.size());
        assertEquals(leftHand, chain.get(0));
        assertEquals(leftElbow, chain.get(1));
        assertEquals(leftShoulder, chain.get(2));
        assertEquals(chest, chain.get(3));
        assertEquals(rightShoulder, chain.get(4));
        assertEquals(rightElbow, chain.get(5));
        assertEquals(rightHand, chain.get(6));
    }

    @Test
    void testGetBoneChain_InvalidBones() {
        List<Bone> chain = skeleton.getBoneChain("NonExistent", "LeftHand");
        assertTrue(chain.isEmpty());

        chain = skeleton.getBoneChain("LeftHand", "NonExistent");
        assertTrue(chain.isEmpty());
    }

    @Test
    void testGetAllBones() {
        List<Bone> allBones = skeleton.getAllBones();
        assertEquals(9, allBones.size());
        assertTrue(allBones.contains(root));
        assertTrue(allBones.contains(leftHand));
        assertTrue(allBones.contains(rightHand));
    }

    @Test
    void testHasBone() {
        assertTrue(skeleton.hasBone("Root"));
        assertTrue(skeleton.hasBone("LeftHand"));
        assertFalse(skeleton.hasBone("NonExistent"));
    }

    @Test
    void testGetBoneNames() {
        var names = skeleton.getBoneNames();
        assertEquals(9, names.size());
        assertTrue(names.contains("Root"));
        assertTrue(names.contains("LeftHand"));
        assertTrue(names.contains("RightHand"));
    }

    @Test
    void testGetBoneCount() {
        assertEquals(9, skeleton.getBoneCount());
    }

    @Test
    void testGetRootBone() {
        assertEquals(root, skeleton.getRootBone());
    }
}
