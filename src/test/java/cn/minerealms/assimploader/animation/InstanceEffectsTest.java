package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.FootPlacementModifier;
import cn.minerealms.assimploader.collision.ModelCollision;
import cn.minerealms.assimploader.collision.Ray;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstanceEffectsTest {
    @Test void particleRateUsesSimulationTimeAndBoundsHitchWork() {
        AttachmentParticleEmitter emitter=new AttachmentParticleEmitter("tool","minecraft:flame",10,new Vector3f(0,1,0));
        int[] emitted={0};
        AttachmentParticleEmitter.Sink sink=(name,point,velocity) -> {
            emitted[0]++;
            assertEquals("minecraft:flame",name);
            assertEquals(1,velocity.y);
            point.zero(); velocity.zero();
        };
        emitter.update(.05,new Vector3f(3),sink);
        assertEquals(0,emitted[0]);
        emitter.update(.05,new Vector3f(3),sink);
        assertEquals(1,emitted[0]);
        emitter.update(10,new Vector3f(3),sink);
        assertEquals(33,emitted[0]);
        emitter.reset();
        emitter.update(.05,new Vector3f(3),sink);
        assertEquals(33,emitted[0]);
    }

    @Test void morphLayersBlendIndependentlyAndExplicitWeightsOverrideClips() {
        var scene=MorphTargetTest.fixture(); var mesh=scene.getMeshes().get(0);
        var state=new AnimationState("morph",scene); state.initialize(); state.stop();
        state.setLayer("pulse","Pulse",.5f,false,Map.of("MorphNode",1f));
        state.update(1);
        assertArrayEquals(new float[]{.625f},state.getPoseSnapshot().getMorphWeights(mesh),1e-5f);
        state.setMorphWeights(mesh,new float[]{0});
        assertFalse(state.getPoseSnapshot().hasActiveMorphs());
        state.resetMorphWeights();
        assertArrayEquals(new float[]{.625f},state.getPoseSnapshot().getMorphWeights(mesh),1e-5f);
        state.close();
    }

    @Test void trailIsBoundedTimeDeduplicatedAndResetsOnClockRewind() {
        AttachmentTrail trail = new AttachmentTrail("a","b",3,.5,new float[]{1,.5f,0,1});
        for (int i=0;i<10;i++) trail.sample(i*.05,new Vector3f(i,0,0),new Vector3f(i,1,0));
        assertEquals(3,trail.samples().size());
        trail.sample(.45,new Vector3f(100),new Vector3f(100));
        assertEquals(3,trail.samples().size());
        trail.samples().get(0).start().zero();
        assertEquals(7,trail.samples().get(0).start().x);
        trail.sample(2,new Vector3f(),new Vector3f(1));
        assertEquals(1,trail.samples().size());
        trail.sample(0,new Vector3f(),new Vector3f(1));
        assertEquals(1,trail.samples().size());
    }

    @Test void configuredEffectsShareSimulationClockAndStopClearsHistory() {
        ModelInstanceDefinition definition = ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"test:model","attachments":{
                  "a":{"node":"Joint"},"b":{"node":"Joint","translation":[0,1,0]}},
                 "trails":{"blade":{"start":"a","end":"b","capacity":8,"lifetime":2}},
                 "materials":{"*":{"scrollU":0.5,"alpha":0.8,"pulseFrequency":1,"pulseDepth":0.5}}}
                """));
        try (ModelInstance instance=definition.instantiate(AnimationTestScenes.create())) {
            instance.animation().update(.5f);
            assertEquals(.25,instance.material("any").u(),1e-6);
            assertEquals(.4,instance.material("any").alpha(),1e-6);
            assertEquals(2,instance.trails().get("blade").samples().size());
            instance.animation().invalidateCache();
            assertEquals(2,instance.trails().get("blade").samples().size());
            instance.animation().pause();
            instance.animation().update(1);
            assertEquals(.25,instance.material("any").u(),1e-6);
            instance.animation().stop();
            assertTrue(instance.trails().get("blade").samples().isEmpty());
        }
    }

    @Test void exactModelQueryUsesMorphedVerticesAndInstanceTransform() {
        var scene=MorphTargetTest.fixture();
        var mesh=scene.getMeshes().get(0);
        PoseSnapshot zero=PoseSnapshot.capture(0,scene,Map.of(),Map.of(mesh,new float[]{0}));
        PoseSnapshot one=PoseSnapshot.capture(1,scene,Map.of(),Map.of(mesh,new float[]{1}));
        ModelCollision collision=new ModelCollision(List.of());
        Ray ray=new Ray(new Vector3f(5.2f,.2f,-2),new Vector3f(0,0,1));
        assertTrue(collision.raycast(zero,ray,10,ModelCollision.Mode.MESH).isEmpty());
        var hit=collision.raycast(one,ray,10,ModelCollision.Mode.MESH).orElseThrow();
        assertEquals(2,hit.distance(),1e-5);
        assertTrue(collision.raycast(one,ray,1.5f,ModelCollision.Mode.MESH).isEmpty());
        collision.clear();
        assertEquals(2,collision.raycast(one,ray,10,ModelCollision.Mode.MESH).orElseThrow().distance(),1e-5);
    }

    @Test void footPlacementConvertsGroundToModelSpaceAndSkipsZeroDelta() {
        var scene=AnimationTestScenes.create();
        Map<String,Matrix4f> locals=new java.util.LinkedHashMap<>();
        int[] calls={0};
        FootPlacementModifier feet=new FootPlacementModifier(List.of("Root","Joint"),(origin,distance)->{
            calls[0]++;
            assertTrue(origin.x > 100);
            return Optional.of(new Vector3f(origin.x,origin.y-.7f,origin.z));
        },.5f,2,0,1);
        feet.setModelToWorld(new Matrix4f().translation(100,0,0));
        feet.apply(scene,locals,0);
        assertEquals(0,calls[0]);
        feet.apply(scene,locals,.05);
        assertEquals(1,calls[0]);
        assertTrue(locals.get("Root").isFinite());
    }
}
