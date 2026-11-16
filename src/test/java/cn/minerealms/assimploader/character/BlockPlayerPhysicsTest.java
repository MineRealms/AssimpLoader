package cn.minerealms.assimploader.character;

import cn.minerealms.assimploader.animation.*;
import cn.minerealms.assimploader.character.physics.*;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.assimploader.core.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.*;

import static cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockPlayerPhysicsTest {
    private static final String BASE="/assets/assimploader/models/character/block_player/";
    private InputStreamReader reader(String name) {return new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(BASE+name)));}
    @Test void bulletKneeAngleConventionAllowsAuthoredForwardFlexion() throws Exception {
        var shape=new com.bulletphysics.collision.shapes.SphereShape(.1f);
        var a=new com.bulletphysics.dynamics.RigidBody(new com.bulletphysics.dynamics.RigidBodyConstructionInfo(0,null,shape));
        var b=new com.bulletphysics.dynamics.RigidBody(new com.bulletphysics.dynamics.RigidBodyConstructionInfo(0,null,shape));
        var identity=new com.bulletphysics.linearmath.Transform();identity.setIdentity();
        a.setWorldTransform(identity);
        var flexed=new com.bulletphysics.linearmath.Transform();flexed.setIdentity();flexed.basis.rotX(1.206f);b.setWorldTransform(flexed);
        var constraint=new com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint(a,b,identity,identity,true);
        constraint.calculateTransforms();
        assertEquals(-1.206,constraint.getAngle(0),1e-5);
        try(var source=reader("physics.json")) {
            // Parse authoring joints through the imported fixture in the other acceptance test;
            // here only the angular bounds are needed to pin Bullet's distinct sign convention.
            var json=com.google.gson.JsonParser.parseReader(source).getAsJsonObject();
            for(var element:json.getAsJsonArray("joints")) {
                var joint=element.getAsJsonObject();
                if(joint.get("id").getAsString().startsWith("knee_")) {
                    assertTrue(constraint.getAngle(0)>=joint.getAsJsonArray("angularLower").get(0).getAsFloat());
                    assertTrue(constraint.getAngle(0)<=joint.getAsJsonArray("angularUpper").get(0).getAsFloat());
                }
            }
        } finally {a.destroy();b.destroy();}
    }
    @Test void compiledJointFramesMeetAtRestAndWholeBodySurvivesAnimationAndRagdoll() throws Exception {
        try(var input=getClass().getResourceAsStream(BASE+"block_player.fbx")) {
            var scene=new AssimpModelLoader().loadModelFromMemory("block_player.fbx",Objects.requireNonNull(input).readAllBytes(),ModelImportMode.DYNAMIC);
            try(var reader=reader("physics.json")) {
                var definition=CharacterPhysicsAsset.read(reader,scene);
                assertEquals(17,definition.bodies().size());assertEquals(16,definition.joints().size());
                var graph=new PoseGraph(scene.getRootNode(),new HashMap<>());
                var bodies=new HashMap<String,Body>();definition.bodies().forEach(b->bodies.put(b.id(),b));
                for(var joint:definition.joints()) {
                    var a=bodies.get(joint.bodyA());var b=bodies.get(joint.bodyB());
                    var fa=rigid(graph.global(a.bone())).mul(a.offset()).mul(joint.frameA());
                    var fb=rigid(graph.global(b.bone())).mul(b.offset()).mul(joint.frameB());
                    assertTrue(fa.equals(fb,1e-4f),joint.id());
                }
                for(float scale:new float[]{1,.5f,2})try(var state=new AnimationState("block-player",scene);
                        var rigReader=reader("rig.json")) {
                    state.initialize();state.transitionTo("idle",0);
                    CharacterRigDefinition.read(rigReader).install(state);
                    var physics=new BulletCharacterPhysics(definition,scene,scale);
                    physics.setFrame(new Matrix4f(),30_000_000,64,-30_000_000);
                    physics.setWorldBoxes(List.of(new BulletCharacterPhysics.WorldBox("floor",new Vec3(0,-.1f,0),new Vec3(6,.1f,6),Rotation.IDENTITY,4,65535)));
                    state.addModifier("physics",physics);
                    for(int i=0;i<120;i++)state.update(.05f);
                    assertEquals(720,physics.statistics().totalSubsteps());
                    var pendant=state.getPoseSnapshot().getNodeTransform("Pendant2").getTranslation(new Vector3f());
                    assertTrue(pendant.isFinite() && pendant.distance(graph.position("Pendant2"))<.6,"Pendant constraint escaped: "+pendant);
                    physics.setRagdollEnabled(true);
                    float startHead=state.getPoseSnapshot().getNodeTransform("Head").m31();
                    for(int i=0;i<100;i++)state.update(.05f);
                    var pose=state.getPoseSnapshot();
                    for(var body:definition.bodies()) {
                        var p=physics.bodyTransform(body.id()).getTranslation(new Vector3f());
                        assertTrue(p.isFinite() && p.length()<5*scale,"Escaped body "+body.id()+" "+p);
                        assertTrue(p.y>-.15f,"Body fell through floor "+body.id()+" "+p);
                    }
                    assertTrue(pose.getNodeTransform("Head").m31()<startHead-.3,"Ragdoll did not fall");
                    physics.setRagdollEnabled(false);state.update(.05f);
                    assertEquals(graph.position("Hips").y, state.getPoseSnapshot().getNodeTransform("Hips").m31(),.001);
                    System.out.println("Block player scale="+scale+" physics="+physics.statistics());
                }
            }
        }
    }
    private static Matrix4f rigid(Matrix4f m) {return new Matrix4f().translation(m.getTranslation(new Vector3f())).rotate(m.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize());}
}
