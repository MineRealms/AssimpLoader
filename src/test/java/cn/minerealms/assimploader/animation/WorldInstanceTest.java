package cn.minerealms.assimploader.animation;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorldInstanceTest {
    private static ModelInstanceDefinition definition() {
        return ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"test:model","attachments":{"a":{"node":"Joint"},"b":{"node":"Joint","translation":[0,1,0]}},
                 "footPlacement":{"left":{"chain":["Root","Joint"],"rise":0.5,"drop":2}},
                 "trails":{"ribbon":{"start":"a","end":"b","capacity":16,"lifetime":5}},
                 "particles":{"dust":{"attachment":"a","particle":"minecraft:cloud","rate":10}}}
                """));
    }

    @Test void worldTrailsRetainPriorPositionsAndTeleportResetsHistory() {
        try(var instance=definition().instantiate(AnimationTestScenes.create())) {
            instance.setWorldContext(new Matrix4f().translation(100,0,0),null);
            instance.animation().update(.05f);
            Vector3f first=instance.trails().get("ribbon").samples().get(0).start();
            assertTrue(first.x>99);
            instance.setWorldContext(new Matrix4f().translation(101,0,0),null);
            instance.animation().update(.05f);
            var samples=instance.trails().get("ribbon").samples();
            assertEquals(first,samples.get(0).start());
            assertTrue(samples.get(samples.size()-1).start().x-first.x>.5);
            instance.setWorldContext(new Matrix4f().translation(200,0,0),null);
            instance.animation().update(.05f);
            assertTrue(instance.trails().get("ribbon").samples().stream().allMatch(s->s.start().x>199));
            instance.clearWorldContext();
            assertFalse(instance.trailsInWorldSpace());
            assertTrue(instance.trails().get("ribbon").samples().stream().allMatch(s->s.start().x<10));
        }
    }

    @Test void groundContextBindsWithoutSamplingDuringRenderOrZeroDelta() {
        try(var instance=definition().instantiate(AnimationTestScenes.create())) {
            int[] calls={0};
            instance.setWorldContext(new Matrix4f().translation(100,0,0),(origin,distance)->{
                calls[0]++; assertTrue(origin.x>99); return Optional.of(new Vector3f(origin).add(0,-.6f,0));
            });
            assertEquals(0,calls[0]);
            instance.animation().update(.05f);
            assertEquals(1,calls[0]);
            assertNotNull(instance.feet().get("left").getLastTarget());
            instance.setGroundPlacementEnabled(false);
            instance.setWorldContext(new Matrix4f().translation(100,0,0),(origin,distance)->{fail("disabled sampler");return Optional.empty();});
            instance.animation().update(.05f);
            assertNull(instance.feet().get("left").getLastTarget());
        }
    }

    @Test void worldEffectsCanBeDisabledAndResumeWithoutCatchupBurst() {
        try(var instance=definition().instantiate(AnimationTestScenes.create())) {
            int[] emitted={0}; instance.setParticleSink((id,p,v)->emitted[0]++);
            instance.animation().update(.1f); assertEquals(1,emitted[0]);
            instance.setWorldEffectsEnabled(false);
            instance.animation().update(10);
            assertEquals(1,emitted[0]); assertTrue(instance.trails().get("ribbon").samples().isEmpty());
            instance.setWorldEffectsEnabled(true);
            instance.animation().update(.1f);
            assertEquals(2,emitted[0]); assertEquals(2,instance.emittedParticles());
        }
    }
}
