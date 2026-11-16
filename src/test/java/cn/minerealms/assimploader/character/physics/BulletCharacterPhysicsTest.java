package cn.minerealms.assimploader.character.physics;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AINode;
import org.lwjgl.system.MemoryStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class BulletCharacterPhysicsTest {
    @Test void gravityUsesFixedBulletStepsAndWorldsAreIndependent() {
        var def=definition(new Vec3(0,-10,0),List.of(sphere("ball",null,Mode.DYNAMIC,new Vec3(0,2,0),1,65535)),List.of());
        try(var a=actor(def);var b=actor(def)) {
            a.advance(new HashMap<>(),0);b.advance(new HashMap<>(),0);
            for(int i=0;i<60;i++) a.advance(new HashMap<>(),1d/120);
            for(int i=0;i<30;i++) b.advance(new HashMap<>(),1d/60);
            assertEquals(60,a.statistics().totalSubsteps());assertEquals(60,b.statistics().totalSubsteps());
            assertEquals(.72917,a.bodyTransform("ball").m31(),.015);
            assertEquals(a.bodyTransform("ball").m31(),b.bodyTransform("ball").m31(),1e-6);
            a.applyImpulse("ball",new Vector3f(2,0,0));a.advance(new HashMap<>(),1d/120);
            assertTrue(a.bodyTransform("ball").m30()>.01);assertEquals(0,b.bodyTransform("ball").m30(),1e-6);
        }
    }

    @Test void contactRestingHeightAndCollisionFiltersComeFromBullet() {
        var bodies=List.of(sphere("contact",null,Mode.DYNAMIC,new Vec3(-1,2,0),1,2),sphere("filtered",null,Mode.DYNAMIC,new Vec3(1,2,0),1,0));
        try(var physics=actor(definition(new Vec3(0,-10,0),bodies,List.of()))) {
            physics.setWorldBoxes(List.of(floor()));
            for(int i=0;i<240;i++) physics.advance(new HashMap<>(),1d/120);
            assertEquals(.25,physics.bodyTransform("contact").m31(),.025);
            assertEquals(0,physics.linearVelocity("contact").y,.05);
            assertTrue(physics.bodyTransform("filtered").m31()<-10);
            assertEquals(1,physics.statistics().worldBoxes());
            physics.setWorldBoxes(List.of());assertEquals(0,physics.statistics().worldBoxes());
            for(int i=0;i<30;i++) physics.advance(new HashMap<>(),1d/120);
            assertTrue(physics.bodyTransform("contact").m31()<.05);
        }
    }

    @Test void followRagdollAndResetHaveExplicitSeparateBehavior() {
        var body=sphere("follow",null,Mode.FOLLOW,new Vec3(0,2,0),1,65535);
        try(var physics=actor(definition(new Vec3(0,-10,0),List.of(body),List.of()))) {
            for(int i=0;i<20;i++) physics.advance(new HashMap<>(),.05);
            assertEquals(2,physics.bodyTransform("follow").m31(),1e-6);
            physics.setRagdollEnabled(true);
            for(int i=0;i<60;i++) physics.advance(new HashMap<>(),1d/120);
            assertTrue(physics.bodyTransform("follow").m31()<1);
            physics.setRagdollEnabled(false);physics.advance(new HashMap<>(),0);
            assertEquals(2,physics.bodyTransform("follow").m31(),1e-6);assertEquals(0,physics.linearVelocity("follow").length(),1e-6);
            assertFalse(physics.statistics().ragdoll());assertEquals(PoseModifier.Category.PHYSICS,physics.category());
        }
    }

    @Test void lockedSixDofMaintainsRelativePositionAndRotationUnderGravityAndImpulse() {
        var fixed=sphere("fixed",null,Mode.FOLLOW,new Vec3(0,2,0),1,65535);
        var dynamic=sphere("dynamic",null,Mode.DYNAMIC,new Vec3(0,1,0),1,65535);
        var joint=new Joint("hinge","fixed","dynamic",new Vec3(0,-1,0),Rotation.IDENTITY,Vec3.ZERO,Rotation.IDENTITY,
                Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,true,null);
        try(var physics=actor(definition(new Vec3(0,-10,0),List.of(fixed,dynamic),List.of(joint)))) {
            physics.advance(new HashMap<>(),0);physics.applyImpulse("dynamic",new Vector3f(2,0,0));physics.applyTorqueImpulse("dynamic",new Vector3f(0,0,1));
            for(int i=0;i<180;i++) physics.advance(new HashMap<>(),1d/120);
            Matrix4f result=physics.bodyTransform("dynamic");
            assertEquals(0,result.m30(),.025);assertEquals(1,result.m31(),.025);
            assertEquals(0,result.getNormalizedRotation(new Quaternionf()).angle(),.05);
        }
    }

    @Test void angularLimitsActuallyBoundAFreeRotatingAxis() {
        var fixed=sphere("fixed",null,Mode.FOLLOW,new Vec3(0,2,0),1,65535);
        var dynamic=sphere("dynamic",null,Mode.DYNAMIC,new Vec3(0,2,0),1,65535);
        var joint=new Joint("limited","fixed","dynamic",Vec3.ZERO,Rotation.IDENTITY,Vec3.ZERO,Rotation.IDENTITY,
                Vec3.ZERO,Vec3.ZERO,new Vec3(0,0,-.25f),new Vec3(0,0,.25f),true,null);
        try(var physics=actor(definition(Vec3.ZERO,List.of(fixed,dynamic),List.of(joint)))) {
            physics.advance(new HashMap<>(),0);physics.applyTorqueImpulse("dynamic",new Vector3f(0,0,.5f));
            float maximum=0;
            for(int i=0;i<240;i++) {
                physics.advance(new HashMap<>(),1d/120);
                maximum=Math.max(maximum,physics.bodyTransform("dynamic").getNormalizedRotation(new Quaternionf()).angle());
            }
            assertTrue(maximum>.1f);assertTrue(maximum<.5f,"Six-DOF angular limit failed: "+maximum);
        }
    }

    @Test void optionalSpringRestoresDisplacementThroughBulletForces() {
        var fixed=sphere("fixed",null,Mode.FOLLOW,Vec3.ZERO,1,65535);
        var dynamic=sphere("dynamic",null,Mode.DYNAMIC,new Vec3(1,0,0),1,65535);
        var spring=new Spring(new Vec3(20,20,20),Vec3.ZERO,new Vec3(8,8,8),Vec3.ZERO,100);
        var joint=new Joint("spring","fixed","dynamic",Vec3.ZERO,Rotation.IDENTITY,Vec3.ZERO,Rotation.IDENTITY,
                new Vec3(1,1,1),new Vec3(-1,-1,-1),new Vec3(1,1,1),new Vec3(-1,-1,-1),true,spring);
        try(var physics=actor(definition(Vec3.ZERO,List.of(fixed,dynamic),List.of(joint)))) {
            for(int i=0;i<240;i++) physics.advance(new HashMap<>(),1d/120);
            assertEquals(0,physics.bodyTransform("dynamic").m30(),.025);
            assertEquals(0,physics.linearVelocity("dynamic").length(),.05);
        }
    }

    @Test void boneFeedbackKeepsParentOrderAndHybridPreservesAnimatedTranslation() {
        try(MemoryStack stack=MemoryStack.stackPush()) {
            AssimpNode root=tree(stack);
            var dynamic=sphere("dynamic","Parent",Mode.DYNAMIC,Vec3.ZERO,1,65535);
            var hybrid=sphere("hybrid","Child",Mode.HYBRID,Vec3.ZERO,2,65535);
            try(var physics=new BulletCharacterPhysics(definition(new Vec3(0,-10,0),List.of(hybrid,dynamic),List.of()),root)) {
                Map<String,Matrix4f> locals=new LinkedHashMap<>();physics.advance(locals,0);
                physics.applyTorqueImpulse("hybrid",new Vector3f(0,0,.01f));
                for(int i=0;i<30;i++) {locals.clear();physics.advance(locals,1d/120);}
                PoseGraph graph=new PoseGraph(root,locals);
                assertEquals(2,graph.position("Child").y,.001);
                assertTrue(graph.position("Parent").y<.8);
                assertTrue(graph.global("Child").getNormalizedRotation(new Quaternionf()).angle()>.01);
                assertEquals(Set.of("Parent","Child"),physics.controlledBones());
            }
        }
    }

    @Test void doubleAnchorRebasePreservesWorldPositionAndTeleportClearsVelocity() {
        try(var physics=actor(definition(Vec3.ZERO,List.of(sphere("ball",null,Mode.DYNAMIC,new Vec3(0,2,0),1,65535)),List.of()))) {
            physics.setFrame(new Matrix4f(),30000000,100,30000000);physics.advance(new HashMap<>(),0);
            physics.applyImpulse("ball",new Vector3f(1,0,0));physics.advance(new HashMap<>(),1d/120);
            float prior=physics.bodyTransform("ball").m30();
            physics.setFrame(new Matrix4f(),30000000.125,100,30000000);physics.advance(new HashMap<>(),0);
            assertEquals(prior,physics.bodyTransform("ball").m30()+.125,1e-6);
            physics.setFrame(new Matrix4f().rotateZ((float)Math.PI/2),30000100,100,30000000);physics.advance(new HashMap<>(),0);
            assertEquals(-2,physics.bodyTransform("ball").m30(),1e-5);assertEquals(0,physics.linearVelocity("ball").length(),1e-6);
        }
    }

    @Test void boundedCatchupInvalidInputsAndLifecycle() throws Exception {
        var physics=actor(definition(Vec3.ZERO,List.of(sphere("ball",null,Mode.DYNAMIC,Vec3.ZERO,1,65535)),List.of()));
        physics.advance(new HashMap<>(),1000);
        assertEquals(8,physics.statistics().lastSubsteps());assertTrue(physics.statistics().droppedSeconds()>999);
        assertThrows(IllegalArgumentException.class,()->physics.advance(new HashMap<>(),Double.NaN));
        assertThrows(IllegalArgumentException.class,()->physics.setFrame(new Matrix4f().scale(2),0,0,0));
        assertThrows(IllegalArgumentException.class,()->physics.setWorldBoxes(Collections.nCopies(129,floor())));
        AtomicReference<Throwable> failure=new AtomicReference<>();Thread other=new Thread(()-> {try {physics.advance(new HashMap<>(),0);} catch(Throwable value) {failure.set(value);}});
        other.start();other.join();assertInstanceOf(IllegalStateException.class,failure.get());
        physics.close();physics.close();assertTrue(physics.statistics().closed());assertEquals(0,physics.statistics().bodies());
        assertThrows(IllegalStateException.class,()->physics.advance(new HashMap<>(),0));
    }

    @Test void strictJsonRejectsUnknownUnsupportedAndFractionalFields() {
        var value=JsonParser.parseString("{\"version\":1,\"bodies\":[{\"id\":\"b\",\"mode\":\"DYNAMIC\",\"shape\":\"CAPSULE\",\"size\":[0.1,0.5,0]}]}").getAsJsonObject();
        assertEquals(Shape.CAPSULE,CharacterPhysicsDefinition.parse(value).bodies().get(0).shape());
        value.addProperty("maxSubsteps",2.5);assertThrows(IllegalArgumentException.class,()->CharacterPhysicsDefinition.parse(value));value.remove("maxSubsteps");
        value.addProperty("unknown",true);assertThrows(IllegalArgumentException.class,()->CharacterPhysicsDefinition.parse(value));value.remove("unknown");
        value.getAsJsonArray("bodies").get(0).getAsJsonObject().addProperty("mode","cloth");assertThrows(IllegalArgumentException.class,()->CharacterPhysicsDefinition.parse(value));
        assertThrows(IllegalArgumentException.class,()->definition(Vec3.ZERO,List.of(sphere("duplicate",null,Mode.DYNAMIC,Vec3.ZERO,1,0),sphere("duplicate",null,Mode.FOLLOW,Vec3.ZERO,1,0)),List.of()));
    }

    @Test void boxAndCapsuleUseTheirActualBulletShapesAndInertia() {
        var box=new Body("box",null,Mode.DYNAMIC,Shape.BOX,new Vec3(.3f,.4f,.2f),new Vec3(-2,2,0),Rotation.IDENTITY,2,0,0,.7f,0,1,2,true);
        var capsule=new Body("capsule",null,Mode.DYNAMIC,Shape.CAPSULE,new Vec3(.2f,.6f,0),new Vec3(2,2,0),Rotation.IDENTITY,1,0,0,.7f,0,1,2,true);
        try(var physics=actor(definition(new Vec3(0,-10,0),List.of(box,capsule),List.of()))) {
            physics.setWorldBoxes(List.of(floor()));
            for(int i=0;i<240;i++) physics.advance(new HashMap<>(),1d/120);
            var boxPose=physics.bodyTransform("box");
            double boxSupport=Math.abs(boxPose.m01())*.3+Math.abs(boxPose.m11())*.4+Math.abs(boxPose.m21())*.2;
            assertEquals(boxSupport,boxPose.m31(),.025);
            var capsulePose=physics.bodyTransform("capsule");
            assertEquals(.2+.3*Math.abs(capsulePose.m11()),capsulePose.m31(),.025);
            float before=physics.linearVelocity("box").x;
            physics.applyImpulse("box",new Vector3f(2,0,0));assertEquals(before+1,physics.linearVelocity("box").x,1e-5);
        }
    }

    @Test void physicsRunsAfterIkAndConflictingSpringWritersAreRejected() {
        assertTrue(PoseModifier.Category.PHYSICS.ordinal()>PoseModifier.Category.IK.ordinal());
        try(var state=new AnimationState("ownership",(AssimpScene)null)) {
            state.addModifier("physics",modifier(PoseModifier.Category.PHYSICS,"head"));
            assertThrows(IllegalArgumentException.class,()->state.addModifier("conflict",modifier(PoseModifier.Category.SPRING,"head")));
            state.addModifier("safe",modifier(PoseModifier.Category.SPRING,"tail"));
            state.removeModifier("physics");state.addModifier("spring",modifier(PoseModifier.Category.SPRING,"head"));
            assertThrows(IllegalArgumentException.class,()->state.addModifier("physics",modifier(PoseModifier.Category.PHYSICS,"head")));
        }
    }

    private static PoseModifier modifier(PoseModifier.Category category,String bone) {
        return new PoseModifier() {
            @Override public Category category() {return category;}
            @Override public Set<String> controlledBones() {return Set.of(bone);}
            @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {}
        };
    }

    private static BulletCharacterPhysics actor(CharacterPhysicsDefinition definition) {return new BulletCharacterPhysics(definition,(AssimpNode)null);}
    private static CharacterPhysicsDefinition definition(Vec3 gravity,List<Body> bodies,List<Joint> joints) {return new CharacterPhysicsDefinition(gravity,1d/120,8,16,4,128,bodies,joints);}
    private static Body sphere(String id,String bone,Mode mode,Vec3 position,int group,int mask) {return new Body(id,bone,mode,Shape.SPHERE,new Vec3(.25f,0,0),position,Rotation.IDENTITY,1,0,0,.7f,0,group,mask,true);}
    private static BulletCharacterPhysics.WorldBox floor() {return new BulletCharacterPhysics.WorldBox("floor",new Vec3(0,-.5f,0),new Vec3(10,.5f,10),Rotation.IDENTITY,2,65535);}
    private static AssimpNode tree(MemoryStack stack) {
        AINode child=node(stack,"Child",1);AINode parent=node(stack,"Parent",1,child);AINode root=node(stack,"Root",0,parent);
        return new AssimpNode(root,CoordinateSystem.MINECRAFT_LEFT_HANDED);
    }
    private static AINode node(MemoryStack stack,String name,float y,AINode... children) {
        AINode node=AINode.calloc(stack);node.mName().data(stack.UTF8(name));node.mTransformation().a1(1).b2(1).c3(1).d4(1).b4(y);
        if(children.length>0) {var pointers=stack.mallocPointer(children.length);for(AINode child:children) pointers.put(child.address());pointers.flip();node.mChildren(pointers);}return node;
    }
}
