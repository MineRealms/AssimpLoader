package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterGripDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CharacterHandContactsTest {
    private static final Path ROOT=Path.of("src/main/resources/assets/rigworks/models/character/block_player");
    private static CharacterEquipmentDefinition.Position point(Vector3f point) {
        return new CharacterEquipmentDefinition.Position(point.x,point.y,point.z);
    }
    @Test void realFbxReachesSupportContactWithImportedRollAndNeverMovesPrimaryHand() throws Exception {
        var scene=new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"),ModelImportMode.DYNAMIC);
        CharacterProfile profile;CharacterEquipmentDefinition base;
        try(var reader=Files.newBufferedReader(ROOT.resolve("profile.json"))){profile=CharacterProfile.read(reader);}
        try(var reader=Files.newBufferedReader(ROOT.resolve("equipment.json"))){base=CharacterEquipmentDefinition.read(reader);}
        for(var primary:HumanoidArm.values()) for(float rootPitch:new float[]{-.8f,0,.9f}) for(boolean orient:new boolean[]{false,true}) {
            var locals=new LinkedHashMap<>(SkinBindPose.locals(scene));
            var graph=new PoseGraph(scene.getRootNode(),locals);
            var rootTurn=new Quaternionf().rotationXYZ(rootPitch,.5f,.2f);
            graph.rotateModel("Hips",rootTurn);
            String support=primary==HumanoidArm.RIGHT?".L":".R",main=primary==HumanoidArm.RIGHT?".R":".L";
            var primaryFrame=graph.global("Hand"+main);
            var shoulder=graph.position("UpperArm"+support);
            var target=rootTurn.transform(new Vector3f(primary==HumanoidArm.RIGHT?-.12f:.12f,-.25f,.3f)).add(shoulder);
            var display=new Matrix4f().translate(.08f,-.02f,.1f).rotateXYZ(.2f,-.4f,.6f).scale(.7f);
            var mount=primary==HumanoidArm.RIGHT?base.rightHand():base.leftHand();
            var item=mount.transform(primaryFrame).mul(display);
            var offset=orient?new Vector3f(.04f,0,.01f):new Vector3f();
            var targetContact=item.getUnnormalizedRotation(new Quaternionf()).normalize().transform(new Vector3f(offset)).add(target);
            var socket=new Matrix4f(item).invert().transformPosition(new Vector3f(targetContact));
            var rule=new CharacterGripDefinition("probe",Set.of(),Set.of(),Set.of("using"),"any",point(socket),
                    point(new Vector3f(.3f,-.5f,-.15f)),point(offset),new CharacterProfile.Rotation(0,0,0,1),2,160,1,orient?1:0,false);
            var equipment=new CharacterEquipmentDefinition(true,base.rightHand(),base.leftHand(),List.of(rule));
            var contacts=new CharacterHandContacts(profile,equipment,scene);
            float upper=shoulder.distance(graph.position("Forearm"+support));
            float lower=graph.position("Forearm"+support).distance(graph.position("Hand"+support));
            contacts.resolve("probe",primary,display);contacts.apply(scene,locals,0);
            var result=new PoseGraph(scene.getRootNode(),locals);
            assertEquals(0,contacts.statistics().error(),2e-5,"Support contact must reach the configured item-space point");
            assertFalse(contacts.statistics().clamped());
            assertTrue(primaryFrame.equals(result.global("Hand"+main),1e-6f),"Primary hand cannot feed back from support IK");
            assertEquals(upper,shoulder.distance(result.position("Forearm"+support)),2e-5);
            assertEquals(lower,result.position("Forearm"+support).distance(result.position("Hand"+support)),2e-5);
            if(orient)assertTrue(item.getUnnormalizedRotation(new Quaternionf()).normalize().equals(
                    result.global("Hand"+support).getUnnormalizedRotation(new Quaternionf()).normalize(),2e-5f));
        }
    }

    @Test void zeroWeightAndDisabledRulePreserveTheIncomingPose() throws Exception {
        var scene=new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"),ModelImportMode.DYNAMIC);
        CharacterProfile profile;CharacterEquipmentDefinition equipment;
        try(var reader=Files.newBufferedReader(ROOT.resolve("profile.json"))){profile=CharacterProfile.read(reader);}
        try(var reader=Files.newBufferedReader(ROOT.resolve("equipment.json"))){equipment=CharacterEquipmentDefinition.read(reader);}
        var locals=new LinkedHashMap<>(SkinBindPose.locals(scene));
        var before=new LinkedHashMap<String,Matrix4f>();locals.forEach((id,matrix)->before.put(id,new Matrix4f(matrix)));
        var contacts=new CharacterHandContacts(profile,equipment,scene);
        contacts.resolve("bow_support",HumanoidArm.RIGHT,new Matrix4f());contacts.weight(0);contacts.apply(scene,locals,0);
        assertEquals(before,locals);assertEquals("",contacts.statistics().rule());
        contacts.weight(1);contacts.disable();contacts.apply(scene,locals,0);assertEquals(before,locals);
        assertThrows(IllegalArgumentException.class,()->contacts.weight(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->contacts.resolve("unknown",HumanoidArm.LEFT,new Matrix4f()));
    }

    @Test void zeroDefinitionWeightPreservesShearWithoutAddingOverrides() throws Exception {
        var scene=new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"),ModelImportMode.DYNAMIC);
        CharacterProfile profile;CharacterEquipmentDefinition base;
        try(var reader=Files.newBufferedReader(ROOT.resolve("profile.json"))){profile=CharacterProfile.read(reader);}
        try(var reader=Files.newBufferedReader(ROOT.resolve("equipment.json"))){base=CharacterEquipmentDefinition.read(reader);}
        var rule=new CharacterGripDefinition("zero",Set.of(),Set.of(),Set.of("held"),"any",
                point(new Vector3f()),point(new Vector3f(.3f,-.5f,0)),point(new Vector3f(.04f,0,0)),
                CharacterProfile.Rotation.IDENTITY,2,160,0,1,false);
        var contacts=new CharacterHandContacts(profile,new CharacterEquipmentDefinition(true,base.rightHand(),base.leftHand(),List.of(rule)),scene);
        var locals=new LinkedHashMap<>(SkinBindPose.locals(scene));
        locals.compute("Hand.L",(id,matrix)->new Matrix4f(matrix).m10(.2f));
        var before=new LinkedHashMap<String,Matrix4f>();locals.forEach((id,matrix)->before.put(id,new Matrix4f(matrix)));
        contacts.resolve("zero",HumanoidArm.RIGHT,new Matrix4f());contacts.apply(scene,locals,0);
        assertEquals(before,locals,"Zero definition weight must be an exact no-op, including shear and local override keys");
        assertEquals("",contacts.statistics().rule());
    }
}
