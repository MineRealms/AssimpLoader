package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ModelInstanceDefinition;
import cn.minerealms.assimploader.animation.ik.ChainIkModifier;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AssimpModelHandleTest {
    private AnimationState fixture(String name) throws Exception {
        String base="/assets/assimploader/models/validation/"+name;
        try(var model=getClass().getResourceAsStream(base+".glb");var definition=getClass().getResourceAsStream(base+".instance.json")) {
            assertNotNull(model);assertNotNull(definition);
            var scene=new AssimpModelLoader().loadModelFromMemory(base+".glb",model.readAllBytes(),ModelImportMode.DYNAMIC);
            return ModelInstanceDefinition.read(new InputStreamReader(definition,StandardCharsets.UTF_8)).instantiate(scene).animation();
        }
    }

    @Test void commandsUseTheExistingStateAndDoNotAdvanceTheClockOrOtherInstances() throws Exception {
        try(var state=fixture("highbone_probe");var other=fixture("highbone_probe")) {
            var handle=new AssimpModelHandle(state);
            assertTrue(handle.state("wave"));assertFalse(handle.state("unknown"));
            assertTrue(handle.play("Wave",0));state.update(.65f);
            double seconds=state.getSimulationTime();var untouched=other.getPoseSnapshot();
            handle.performance(Map.of("updateIntervalTicks",4,"renderInterpolation",true,"maxTrailSamples",16));
            assertTrue(state.getPerformanceSettings().ik());
            assertEquals(4,handle.performance().get("updateIntervalTicks"));
            assertEquals(16,state.getPerformanceSettings().maxTrailSamples());
            handle.layer("script","Wave",.25,false,Map.of("B255",1));
            handle.layerWeight("script",.75);assertEquals(.75,state.getLayer("script").getWeight());
            handle.removeLayer("script");assertNull(state.getLayer("script"));
            handle.morph(0,new double[]{1});double active=handle.bounds().get("maxX");
            handle.morph(0,new double[]{0});assertTrue(active>handle.bounds().get("maxX"));
            assertEquals(256,handle.meshes().get(0).get("bones"));
            assertTrue(handle.node("B255").containsKey("x"));
            handle.resetMorphs().pause().resume().resetDynamics();
            assertEquals(seconds,state.getSimulationTime());assertSame(untouched,other.getPoseSnapshot());
        }
    }

    @Test void invalidInputsAndExpiredOrCrossThreadHandlesFailClearly() throws Exception {
        var state=fixture("highbone_probe");var handle=new AssimpModelHandle(state);
        try {
            assertThrows(IllegalArgumentException.class,()->handle.performance(Map.of("updateIntervalTicks",1.5)));
            assertThrows(IllegalArgumentException.class,()->handle.performance(Map.of("typo",true)));
            assertThrows(IllegalArgumentException.class,()->handle.performance(Map.of("renderInterpolation","true")));
            assertThrows(IllegalArgumentException.class,()->handle.morph(0,new double[]{Double.NaN}));
            assertThrows(IllegalArgumentException.class,()->handle.morph(20,new double[]{0}));
            assertThrows(IllegalArgumentException.class,()->handle.morph(0,new double[0]));
            assertThrows(IllegalArgumentException.class,()->handle.node("missing"));
            assertThrows(IllegalArgumentException.class,()->handle.target("missing",0,0,0));
            var error=new AtomicReference<Throwable>();
            Thread worker=new Thread(()->{try {handle.bounds();} catch(Throwable value) {error.set(value);}});
            worker.start();worker.join();assertInstanceOf(IllegalStateException.class,error.get());
        } finally {state.close();}
        assertFalse(handle.isValid());assertThrows(IllegalStateException.class,handle::bounds);
    }

    @Test void mountsCollisionAndExistingIkTargetsUseModelSpace() throws Exception {
        try(var state=fixture("animation_probe_morph")) {
            var handle=new AssimpModelHandle(state);
            assertTrue(handle.attachment("tool").containsKey("x"));
            state.addModifier("ik",new ChainIkModifier(List.of("Shoulder","Elbow","ToolSocket"),new Vector3f(1,2,0),1,12,.001f));
            handle.target("ik",1,2,.5);
            var bounds=handle.bounds();
            double x=(bounds.get("minX")+bounds.get("maxX"))/2,y=(bounds.get("minY")+bounds.get("maxY"))/2;
            assertNotNull(handle.raycast("bounds",x,y,bounds.get("minZ")-10,0,0,1,20));
            assertThrows(IllegalArgumentException.class,()->handle.raycast("bounds",0,0,0,0,0,0,20));
            assertThrows(IllegalArgumentException.class,()->handle.attachment("missing"));
            handle.effects(false).groundPlacement(false);
            assertFalse(state.getModelInstance().trailsEnabled());
        }
    }
}
