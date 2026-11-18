package cn.minerealms.assimploader.workbench.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchSessionPersistenceTest {
    private static WorkbenchDocument document() {
        return new WorkbenchDocument(1,"test:source","model.fbx","",ModelTransform.IDENTITY,
                Map.of(),Map.of(),Map.of(),WorkbenchDocument.AnimationPreview.DEFAULT,new JsonObject());
    }

    @Test void completedWriteSetsItsSnapshotAsBaselineWithoutLosingNewerEditsOrDrag() {
        var session=new WorkbenchSession(document());
        session.edit(d->d.withTransform(d.transform().withPosition(1,0,0)));
        var persisted=session.document();
        session.beginTransaction();session.edit(d->d.withTransform(d.transform().withPosition(2,0,0)));
        session.markSaved(persisted);
        assertEquals(2,session.document().transform().x());assertTrue(session.dirty());
        assertEquals(persisted,session.savedDocument());
        session.edit(d->d.withTransform(d.transform().withPosition(3,0,0)));
        session.cancelTransaction();assertEquals(persisted,session.document());assertFalse(session.dirty());
        session.edit(d->d.withTransform(d.transform().withPosition(4,0,0)));
        session.revert();assertEquals(persisted,session.document());assertFalse(session.dirty());
    }

    @Test void firstSaveRebasesLatestExtensionEditsUndoRedoAndActiveTransaction() {
        var session=new WorkbenchSession(document());
        session.edit(d->d.withTransform(d.transform().withPosition(1,0,0)));
        var extension=new JsonObject();extension.addProperty("latest",true);
        session.edit(d->d.withExtension("rigworks",extension));
        session.undo();assertTrue(session.canRedo());
        session.beginTransaction();session.edit(d->d.withTransform(d.transform().withPosition(2,0,0)));
        var imported=new WorkbenchDocument(1,"workbench:copied","assets/model.fbx","a".repeat(64),ModelTransform.IDENTITY,
                Map.of(),Map.of(),Map.of(),WorkbenchDocument.AnimationPreview.DEFAULT,new JsonObject());
        session.rebase(d->d.rebaseAsset(document(),imported));
        assertEquals("workbench:copied",session.document().assetId());
        assertEquals(2,session.document().transform().x());
        session.cancelTransaction();assertEquals(1,session.document().transform().x());
        session.redo();assertTrue(session.document().extensions().getAsJsonObject("rigworks").get("latest").getAsBoolean());
        session.undo();session.undo();assertEquals(0,session.document().transform().x());
        assertEquals("assets/model.fbx",session.document().sourcePath());
        assertEquals(imported.contentHash(),session.savedDocument().contentHash());
    }

    @Test void rebaseFailureLeavesAllSessionStateUntouched() {
        var session=new WorkbenchSession(document());
        session.edit(d->d.withTransform(d.transform().withPosition(1,0,0)));
        var before=session.document();var revision=session.revision();
        assertThrows(IllegalStateException.class,()->session.rebase(d->{if(d.equals(document()))throw new IllegalStateException("no copy");return d.withSource("assets/model.fbx","");}));
        assertEquals(before,session.document());assertEquals(document(),session.savedDocument());assertEquals(revision,session.revision());
        session.undo();assertEquals(document(),session.document());
    }

    @Test void portableRebaseRemapsInheritedTexturesAndKeepsOverridesAndUnknownMaterials() {
        var sourceMaterial=new WorkbenchDocument.MaterialOverride(1,1,1,1,0,.5f,0,WorkbenchDocument.AlphaMode.OPAQUE,.1f,true,
                Map.of("albedo","C:/art/a.png","normal","C:/art/n.png"));
        var portableMaterial=new WorkbenchDocument.MaterialOverride(1,1,1,1,0,.5f,0,WorkbenchDocument.AlphaMode.OPAQUE,.1f,true,
                Map.of("albedo","textures/a.png","normal","textures/n.png"));
        var editedMaterial=new WorkbenchDocument.MaterialOverride(.5f,.6f,.7f,1,1,.2f,5,WorkbenchDocument.AlphaMode.OPAQUE,.1f,true,
                Map.of("albedo","C:/art/a.png","normal","custom/n.png"));
        var source=document().withMaterial(0,sourceMaterial);
        var imported=source.withSource("assets/model.fbx","b".repeat(64)).withMaterial(0,portableMaterial);
        var edited=source.withMaterial(0,editedMaterial).withMaterial(99,sourceMaterial);
        var rebased=edited.rebaseAsset(source,imported);
        assertEquals("textures/a.png",rebased.materials().get(0).textures().get("albedo"));
        assertEquals("custom/n.png",rebased.materials().get(0).textures().get("normal"));
        assertEquals(5,rebased.materials().get(0).emission());assertEquals(sourceMaterial,rebased.materials().get(99));
    }
}
