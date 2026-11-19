package cn.minerealms.assimploader.model;

import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIAnimMesh;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryStack;

import cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportedMorphNamesTest {
    @Test void blenderFbxRepeatedChannelNamesHaveLegacyAliases() {
        assertEquals(Map.of("Blink.Blink", "Blink", "Smile.Smile", "Smile"),
                AssimpMesh.morphAliases(List.of("Blink.Blink", "Smile.Smile"), true));
    }

    @Test void doesNotStripDottedUserNamesOrRenameAnExistingTarget() {
        var names = List.of("Eye.Left", "Eye.Right", "Blink.Blink", "Blink", "Eye.Left.Eye.Left", ".Blink", "Blink.");
        assertTrue(AssimpMesh.morphAliases(names, true).isEmpty());
        assertTrue(AssimpMesh.morphAliases(List.of("Blink.Blink"), false).isEmpty());
    }

    @Test void importedNamesAndAliasesSurviveNativeMemoryReleaseWithoutChangingTargetIndices() {
        AssimpMesh copied;
        try (var stack = MemoryStack.stackPush()) {
            var mesh = AIMesh.calloc(stack).mVertices(AIVector3D.calloc(1, stack));
            AIMesh.nmNumVertices(mesh.address(), 1);
            var first = AIAnimMesh.calloc(stack).mNumVertices(1);
            var second = AIAnimMesh.calloc(stack).mNumVertices(1);
            first.mName().data(stack.UTF8("Blink.Blink"));
            second.mName().data(stack.UTF8("Eye.Left"));
            mesh.mAnimMeshes(stack.pointers(first.address(), second.address()));
            copied = new AssimpMesh(mesh, 0, CoordinateSystem.MINECRAFT_LEFT_HANDED, true);
        }
        assertEquals(List.of("Blink.Blink", "Eye.Left"), copied.getMorphTargets().stream().map(t -> t.getName()).toList());
        assertEquals(Map.of("Blink.Blink", "Blink"), copied.getMorphTargetAliases());
        assertThrows(UnsupportedOperationException.class, () -> copied.getMorphTargetAliases().put("other", "name"));
    }
}
