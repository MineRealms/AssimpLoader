package cn.minerealms.assimploader.workbench.model;

import cn.minerealms.assimploader.workbench.io.WorkbenchCodec;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchDocumentTest {
    private static WorkbenchDocument document() {
        return new WorkbenchDocument(1,"test:asset","assets/model.fbx","",ModelTransform.IDENTITY,
                Map.of(),Map.of(),Map.of(),WorkbenchDocument.AnimationPreview.DEFAULT,new JsonObject());
    }

    @Test void roundTripRetainsNodeMaterialsSocketsAnimationAndUnknownExtensionFields() {
        var extra=new JsonObject();extra.addProperty("future_rig_version",28);var unknown=new JsonObject();unknown.addProperty("property","kept");extra.add("unknown",unknown);
        var material=new WorkbenchDocument.MaterialOverride(.8f,.7f,.6f,.4f,.95f,.1f,30,
                WorkbenchDocument.AlphaMode.BLEND,.25f,false,Map.of("normal","textures/n.png","orm","textures/orm.png"));
        var expected=document().withTransform(new ModelTransform(15,4,-7,10,90,-3,2,3,4))
                .withNode("/Arm#0",new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY,false))
                .withMaterial(3,material).withSocket("tool",new WorkbenchDocument.Socket("/Arm#0",ModelTransform.IDENTITY))
                .withAnimation(new WorkbenchDocument.AnimationPreview("walk",1.23,.75f,false,true)).withExtension("rigworks",extra);
        assertEquals(expected,WorkbenchCodec.read(WorkbenchCodec.write(expected)));
        assertTrue(WorkbenchCodec.write(expected).contains("future_rig_version"));
        assertEquals(30,WorkbenchCodec.read(WorkbenchCodec.write(expected)).materials().get(3).emission());
    }

    @Test void extensionInputAndReturnedJsonCannotMutateSnapshot() {
        var input=new JsonObject();input.addProperty("x",1);
        var document=document().withExtension("module",input);
        input.addProperty("x",2);document.extensions().getAsJsonObject("module").addProperty("x",3);
        assertEquals(1,document.extensions().getAsJsonObject("module").get("x").getAsInt());
    }

    @Test void constructorsAndDecodeRejectNonfiniteTransformsAndUnknownSchema() {
        assertThrows(IllegalArgumentException.class,()->new ModelTransform(Float.NaN,0,0,0,0,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->ModelTransform.IDENTITY.withScale(0,1,1));
        assertThrows(IllegalArgumentException.class,()->WorkbenchCodec.read(WorkbenchCodec.write(document()).replace("\"version\": 1","\"version\": 2")));
        assertThrows(IllegalArgumentException.class,()->new WorkbenchDocument.MaterialOverride(1,1,1,1,2,.5f,0,WorkbenchDocument.AlphaMode.OPAQUE,.1f,true,Map.of()));
    }

    @Test void nodePathsDistinguishDuplicateNamesAndEscapeSlashes() {
        assertNotEquals(SceneMetadata.nodePath("/Root#0","Arm",0),SceneMetadata.nodePath("/Root#0","Arm",1));
        assertEquals("/A~1B~0#0",SceneMetadata.nodePath("","A/B~",0));
    }

    @Test void removedNodesAndClipsAreReportedAfterReimportRatherThanDiscardingOverrides() {
        var document=document().withNode("/OldArm#0",WorkbenchDocument.NodeOverride.DEFAULT)
                .withSocket("tool",new WorkbenchDocument.Socket("/OldArm#0",ModelTransform.IDENTITY))
                .withAnimation(new WorkbenchDocument.AnimationPreview("old_walk",0,1,true,false));
        var metadata=new SceneMetadata(List.of(new SceneMetadata.NodeInfo("/NewArm#0","NewArm",0,0)),List.of(),List.of(),
                List.of(),new SceneMetadata.Bounds(0,0,0,1,1,1),List.of(),true,0,"fbx","");
        assertEquals(3,document.validateReferences(metadata).size());
        assertEquals(1,document.nodes().size());
    }

    @Test void nonuniformTransformUsesExpectedTrsOrderWithoutChangingInput() {
        var transform=new ModelTransform(10,20,30,0,0,90,2,3,4);
        var point=transform.matrix().transformPosition(new org.joml.Vector3f(1,0,0));
        assertEquals(10,point.x,1e-5);assertEquals(22,point.y,1e-5);assertEquals(30,point.z,1e-5);
    }

    @Test void continuousDragIsOneUndoAndCancelRestoresStartingState() {
        var session=new WorkbenchSession(document());session.beginTransaction();
        session.edit(value->value.withTransform(ModelTransform.IDENTITY.withPosition(1,0,0)));
        session.edit(value->value.withTransform(ModelTransform.IDENTITY.withPosition(2,0,0)));
        session.commitTransaction();assertTrue(session.dirty());
        session.undo();assertEquals(document(),session.document());assertFalse(session.canUndo());
        session.redo();assertEquals(2,session.document().transform().x());
        session.markSaved();assertFalse(session.dirty());
        session.beginTransaction();session.edit(value->value.withTransform(ModelTransform.IDENTITY.withPosition(3,0,0)));
        session.cancelTransaction();assertFalse(session.dirty());assertEquals(2,session.document().transform().x());
        session.edit(value->value.withTransform(ModelTransform.IDENTITY.withPosition(4,0,0)));
        session.revert();assertEquals(2,session.document().transform().x());assertFalse(session.canUndo());
    }
}
