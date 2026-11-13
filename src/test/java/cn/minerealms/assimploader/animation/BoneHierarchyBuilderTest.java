package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AINode;
import org.lwjgl.system.MemoryStack;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BoneHierarchyBuilderTest {
    @Test
    void auxiliaryParentsAndMultipleBranchesRetainBindTransformsAndPaletteOrder() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AINode left = node(stack, "Left", 0, 2, 0);
            AINode right = node(stack, "Right", 0, 0, 3);
            AINode helper = node(stack, "Helper", 5, 0, 0, left, right);
            AINode root = node(stack, "Scene", 1, 0, 0, helper);
            Map<String, Matrix4f> offsets = new LinkedHashMap<>();
            offsets.put("Right", new Matrix4f().translation(-6, 0, -3));
            offsets.put("Left", new Matrix4f().translation(-6, -2, 0));
            offsets.put("Detached", new Matrix4f());
            BoneHierarchyBuilder hierarchy = new BoneHierarchyBuilder();
            hierarchy.buildFromOffsets(offsets, new AssimpNode(root, CoordinateSystem.MINECRAFT_LEFT_HANDED));

            assertEquals(3, hierarchy.getBoneCount());
            assertEquals(5, hierarchy.getHierarchyNodes().size());
            assertTrue(hierarchy.getFinalTransforms()[0].equals(new Matrix4f(), 1e-5f));
            assertTrue(hierarchy.getFinalTransforms()[1].equals(new Matrix4f(), 1e-5f));
            hierarchy.updateBoneTransform("Helper", new Matrix4f().translation(10, 0, 0));
            hierarchy.updateBoneTransform("Detached", new Matrix4f().translation(0, 4, 0));
            hierarchy.updateTransforms();
            assertEquals(5, hierarchy.getFinalTransforms()[0].m30(), 1e-5f);
            assertEquals(5, hierarchy.getFinalTransforms()[1].m30(), 1e-5f);
            assertEquals(4, hierarchy.getFinalTransforms()[2].m31(), 1e-5f);
            hierarchy.resetToBindPose();
            for (Matrix4f transform : hierarchy.getFinalTransforms()) {
                assertTrue(transform.equals(new Matrix4f(), 1e-5f));
            }
        }
    }

    @Test
    void rebuildingClearsThePreviousTree() {
        BoneHierarchyBuilder hierarchy = new BoneHierarchyBuilder();
        hierarchy.buildFromOffsets(Map.of("Old", new Matrix4f()), null);
        hierarchy.buildFromOffsets(Map.of("New", new Matrix4f()), null);
        assertNull(hierarchy.getBone("Old"));
        assertEquals(1, hierarchy.getBoneCount());
        assertEquals("New", hierarchy.getRootBone().getName());
    }

    static AINode node(MemoryStack stack, String name, float x, float y, float z, AINode... children) {
        AINode node = AINode.calloc(stack);
        node.mName().data(stack.UTF8(name));
        node.mTransformation().a1(1).b2(1).c3(1).d4(1).a4(x).b4(y).c4(z);
        if (children.length > 0) {
            var pointers = stack.mallocPointer(children.length);
            for (AINode child : children) pointers.put(child.address());
            pointers.flip();
            node.mChildren(pointers);
        }
        return node;
    }
}
