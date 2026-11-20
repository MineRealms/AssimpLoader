package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpNode;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AINode;
import org.lwjgl.system.MemoryStack;

import static org.junit.jupiter.api.Assertions.*;

class MorphChannelBindingTest {
    private static AssimpNode owner(String name,int... indices) {
        try(var stack=MemoryStack.stackPush()) {
            var node=AINode.calloc(stack);
            node.mName().data(stack.UTF8(name));
            node.mTransformation().a1(1).b2(1).c3(1).d4(1);
            node.mMeshes(stack.ints(indices));
            return new AssimpNode(node);
        }
    }

    @Test void fbxGeometryZeroResolvesAnUnambiguousMeshBearingNode() {
        var root=owner("ExportedFace",4);
        assertTrue(MorphChannelBinding.matches(root,"Geometry.002",4,"ExportedFace*0",true));
        assertFalse(MorphChannelBinding.matches(root,"Geometry.002",3,"ExportedFace*0",true));
        assertFalse(MorphChannelBinding.matches(root,"Geometry.002",4,"ExportedFace*1",true));
        assertFalse(MorphChannelBinding.matches(root,"Geometry.002",4,"ExportedFace*00",true));
    }

    @Test void ordinaryNamesKeepTheirExistingMeaningAndFbxRulesDoNotLeakAcrossFormats() {
        var root=owner("ExportedFace",4);
        assertTrue(MorphChannelBinding.matches(root,"Geometry.002",4,"Geometry.002",false));
        assertTrue(MorphChannelBinding.matches(root,"Geometry.002",4,"ExportedFace",false));
        assertFalse(MorphChannelBinding.matches(root,"Geometry.002",4,"ExportedFace*0",false));
        assertTrue(MorphChannelBinding.matches(root,"ExportedFace*0",3,"ExportedFace*0",false));
    }

    @Test void doesNotGuessWhichMaterialSplitOrGeometryAnOrdinalRefersTo() {
        var split=owner("ExportedFace",4,5);
        assertFalse(MorphChannelBinding.matches(split,"GeometryA",4,"ExportedFace*0",true));
        assertFalse(MorphChannelBinding.matches(split,"GeometryB",5,"ExportedFace*1",true));
        assertTrue(MorphChannelBinding.matches(split,"GeometryA",4,"ExportedFace",true));
    }
}
