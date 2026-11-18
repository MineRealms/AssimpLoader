package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CharacterViewGeometryTest {
    private static final Path ROOT=Path.of("src/main/resources/assets/rigworks/models/character/block_player");
    @Test void spyglassPitchLimitKeepsTheAnimatedBodyFrameAndHeadPosition() {
        var body=new Matrix4f().translation(1,2,3).rotateY(.7f).rotateZ(.2f);
        var head=new Matrix4f(body).translate(0,-.2f,.1f).rotateZ(.1f).rotateY(.4f).rotateX(-1.2f);
        var retained=new Matrix4f(head);
        var clamped=CharacterHeldItemRenderer.clampSpyglassPitch(head,body);
        var local=new Matrix4f(body).invert().mul(clamped);
        var angles=local.getEulerAnglesZYX(new Vector3f());
        assertEquals(-Math.PI/6,angles.x,1e-5);
        assertEquals(.4,angles.y,1e-5);assertEquals(.1,angles.z,1e-5);
        assertEquals(0,head.getTranslation(new Vector3f()).distance(clamped.getTranslation(new Vector3f())),1e-6);
        assertEquals(retained,head);
    }
    @Test void armPlacementMapsShoulderToNativeHandOriginWithoutChangingThePose() {
        var frame=new Matrix4f().translation(3,7,-2).rotateY(.4f).rotateX((float)Math.PI);
        var retained=new Matrix4f(frame);var result=CharacterFirstPersonArms.placement(frame,1,false);
        assertEquals(0,result.transformPosition(new Vector3f(3,7,-2)).distance(new Vector3f(-5f/16,2f/16,0)),1e-6);
        assertEquals(retained,frame);
        var left=CharacterFirstPersonArms.placement(frame,.75,true);
        assertEquals(0,left.transformPosition(new Vector3f(3,7,-2)).distance(new Vector3f(5f/16,2f/16,0)),1e-6);
    }
    @Test void realWeightedFixtureMasksBothArmsWithoutIncludingHeadOrOppositeArm() throws Exception {
        var scene=new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"),ModelImportMode.DYNAMIC);
        var left=new HashSet<String>();descendants(scene.findNodeByName("UpperArm.L"),left);
        var right=new HashSet<String>();descendants(scene.findNodeByName("UpperArm.R"),right);
        int leftCount=0,rightCount=0;
        for(var mesh:scene.getMeshes()) {
            int[] a=CharacterFirstPersonArms.selectTriangles(mesh,left),b=CharacterFirstPersonArms.selectTriangles(mesh,right);
            leftCount+=a.length;rightCount+=b.length;
            var seen=new HashSet<Integer>();for(int value:a)seen.add(value);
            for(int value:b)assertFalse(seen.contains(value),"Physical arm masks must not overlap");
            assertEquals(0,CharacterFirstPersonArms.selectTriangles(mesh,Set.of()).length);
        }
        assertTrue(leftCount>0);assertTrue(rightCount>0);
    }
    @Test void equipmentFramesStripFbxScaleAndFollowTheSameEvaluatedPose() throws Exception {
        var scene=new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"),ModelImportMode.DYNAMIC);
        try(var reader=Files.newBufferedReader(ROOT.resolve("profile.json"));var animation=new AnimationState("attachments",scene)) {
            var profile=CharacterProfile.read(reader);animation.initialize();
            var snapshot=animation.getPoseSnapshot();var frame=CharacterAttachmentFrames.frame(profile,snapshot,"head");
            assertNotNull(frame);assertEquals(1,frame.getScale(new Vector3f()).x,1e-5);
            assertEquals(0,frame.getTranslation(new Vector3f()).distance(snapshot.getNodeTransform(profile.bone("head").node()).getTranslation(new Vector3f())),1e-6);
            frame.identity();assertNotEquals(frame,CharacterAttachmentFrames.frame(profile,snapshot,"head"));
            for(boolean left:new boolean[]{false,true}) {
                String side=left?"left_":"right_";
                var shoulder=snapshot.getNodeTransform(profile.bone(side+"upper_arm").node()).getTranslation(new Vector3f());
                var elbow=snapshot.getNodeTransform(profile.bone(side+"forearm").node()).getTranslation(new Vector3f());
                var arm=CharacterAttachmentFrames.of(profile,scene).arm(snapshot,left);
                var axis=new Matrix4f(arm).invert().transformDirection(elbow.sub(shoulder)).normalize();
                assertEquals(0,axis.distance(new Vector3f(0,1,0)),1e-5,"Native arm geometry extends along local +Y");
            }
        }
    }
    private static void descendants(cn.minerealms.assimploader.model.AssimpNode node,Set<String> names){assertNotNull(node);names.add(node.getName());node.getChildren().forEach(child->descendants(child,names));}
}
