package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.compat.kubejs.AssimpModelHandle;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ModelAnimationProcessorTest {
    private static AssimpScene scene(String name,String extension) throws Exception {
        String path="/assets/assimploader/models/validation/"+name+extension;
        try(var stream=ModelAnimationProcessorTest.class.getResourceAsStream(path)) {
            assertNotNull(stream,path);
            return new AssimpModelLoader().loadModelFromMemory(path,stream.readAllBytes(),ModelImportMode.DYNAMIC);
        }
    }
    private static AssimpScene scene() throws Exception {return scene("highbone_probe",".glb");}
    private static ModelAnimationProcessor compile(AssimpScene scene,String json) {
        return ModelAnimationProcessor.compile(scene,JsonParser.parseString(json).getAsJsonObject());
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> options(String json) {return new Gson().fromJson(json,Map.class);}
    private static String declaration() {
        return """
                {"version":1,"clips":{"wave":{"clip":"Wave","enabled":true,"weight":1,"mask":{"B255":1}}},
                 "poses":{"offset":{"bones":{"B255":{"position":[0.05,0,0]}},"additive":true,"weight":1}}}
                """;
    }

    @Test void processorComposesAfterNativeMotionAndBeforeIkUsingCachedControls() throws Exception {
        var scene=scene();
        try(var state=new AnimationState("processor",scene)) {
            state.initialize();
            var nativePose=new Matrix4f(scene.findNodeByName("B255").getTransformation()).translate(100,0,0);
            state.addModifier("native",new PoseModifier() {
                @Override public Category category(){return Category.MOTION;}
                @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta){locals.put("B255",new Matrix4f(nativePose));}
            });
            var seenByIk=new AtomicReference<Matrix4f>();
            state.addModifier("ik_observer",new PoseModifier() {
                @Override public Category category(){return Category.IK;}
                @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta){seenByIk.set(new Matrix4f(locals.get("B255")));}
            });
            var handle=new AssimpModelHandle(state);
            handle.processor("mixed",options(declaration())).processorPhase("mixed","wave",.35);
            var compiled=state.getModifier("kubejs:processor:mixed");
            double simulationTime=state.getSimulationTime();
            for(int index=0;index<10;index++)handle.processorWeight("mixed","offset",1).processorEnabled("mixed","offset",true);
            assertSame(compiled,state.getModifier("kubejs:processor:mixed"));
            assertEquals(simulationTime,state.getSimulationTime(),"Control changes must not advance the simulation clock");
            var player=new AnimationPlayer(scene.getAnimations().stream().filter(clip->clip.getName().equals("Wave")).findFirst().orElseThrow(),scene.getRootNode());
            player.synchronizePhase(.35);
            var expected=new Matrix4f(player.getBoneTransforms().get("B255")).translate(.05f,0,0);
            state.update(.05f);
            assertTrue(expected.equals(seenByIk.get(),1e-5f),"IK must consume the clip plus pose overlay after native reset");
            assertTrue(expected.equals(state.getPoseSnapshot().copyLocalTransforms().get("B255"),1e-5f));
            handle.processorEnabled("mixed",false);state.update(.05f);
            assertTrue(nativePose.equals(seenByIk.get(),1e-5f));
        }
    }

    @Test void additiveClipUsesSkinBindReferenceWhenTheNodeTreeWasSavedInAnAnimatedPose() throws Exception {
        var scene=scene();var skinRest=SkinBindPose.locals(scene).get("B255");
        var savedNode=scene.findNodeByName("B255").getTransformation();var original=new Matrix4f(savedNode);
        try {
            savedNode.rotateX(.8f);
            try(var processor=compile(scene,"""
                    {"version":1,"clips":{"wave":{"clip":"Wave","additive":true,"mask":{"B255":1}}}}
                    """)) {
                processor.phase("wave",.35);
                var locals=new LinkedHashMap<String,Matrix4f>();locals.put("B255",new Matrix4f(skinRest));
                processor.apply(scene,locals,0);
                var player=new AnimationPlayer(scene.getAnimations().stream().filter(clip->clip.getName().equals("Wave")).findFirst().orElseThrow(),scene.getRootNode());
                player.synchronizePhase(.35);
                assertTrue(player.getBoneTransforms().get("B255").equals(locals.get("B255"),1e-5f),
                        "Additive delta must be measured from skin rest, not the saved animated node transform");
            }
        } finally {savedNode.set(original);}
    }

    @Test void namedWeightsMasksAndDisabledControlsAreIsolatedPerInstance() throws Exception {
        var scene=scene();
        try(var first=new AnimationState("first",scene);var second=new AnimationState("second",scene)) {
            first.initialize();second.initialize();
            var a=new AssimpModelHandle(first);var b=new AssimpModelHandle(second);
            var json=options("""
                    {"version":1,"poses":{"pose":{"bones":{"B255":{"position":[0.2,0,0]},"B254":{"position":[0.4,0,0]}},
                    "additive":true,"mask":{"B255":1},"weight":1}}}
                    """);
            a.processor("demo",json);b.processor("demo",json);
            a.processorWeight("demo","pose",0);first.update(.05f);second.update(.05f);
            var x=first.getPoseSnapshot().copyLocalTransforms();var y=second.getPoseSnapshot().copyLocalTransforms();
            assertTrue(x.get("B254").equals(y.get("B254"),1e-5f),"Unmasked bone changed");
            assertFalse(x.get("B255").equals(y.get("B255"),1e-5f));
            a.processorEnabled("demo","pose",false);first.update(.05f);
            assertEquals(List.of("pose"),a.processorControls("demo"));
            assertThrows(IllegalArgumentException.class,()->a.processorWeight("demo","pose",1.0001));
            assertThrows(IllegalArgumentException.class,()->a.processorWeight("demo","missing",.5));
            assertThrows(IllegalArgumentException.class,()->a.processorPhase("demo","pose",.5));
        }
    }

    @Test void invalidReplacementRetainsPreviousCompiledProcessorAndItsIkStage() throws Exception {
        var scene=scene();
        try(var state=new AnimationState("transaction",scene)) {
            state.initialize();var handle=new AssimpModelHandle(state);
            handle.processor("demo",options(declaration()));
            var previous=state.getModifier("kubejs:processor:demo");var ik=state.getModifier("kubejs:processor:demo:ik");
            assertThrows(IllegalArgumentException.class,()->handle.processor("demo",options("{\"version\":1,\"clips\":{\"bad\":{\"clip\":\"missing\"}}}")));
            assertSame(previous,state.getModifier("kubejs:processor:demo"));assertSame(ik,state.getModifier("kubejs:processor:demo:ik"));
            assertFalse(((ModelAnimationProcessor)previous).isClosed());
            handle.processor("demo",options("{\"version\":1}"));
            assertTrue(((ModelAnimationProcessor)previous).isClosed());
        }
    }

    @Test void cleanupAndStateCloseRetireOnlyProcessorOwnedModifiers() throws Exception {
        var scene=scene();var state=new AnimationState("lifecycle",scene);state.initialize();
        var handle=new AssimpModelHandle(state);handle.processor("demo",options(declaration()));
        var compiled=(ModelAnimationProcessor)state.getModifier("kubejs:processor:demo");
        var independent=new PoseModifier(){@Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta){}};
        state.addModifier("asset_owned",independent);
        handle.clearScriptControls();
        assertTrue(compiled.isClosed());assertNull(state.getModifier("kubejs:processor:demo:ik"));
        assertSame(independent,state.getModifier("asset_owned"));
        handle.processor("demo",options(declaration()));compiled=(ModelAnimationProcessor)state.getModifier("kubejs:processor:demo");
        state.close();assertTrue(compiled.isClosed());assertFalse(handle.isValid());
    }

    @Test void ownedIkUsesItsOwnStageAndBoundedTargetWithoutTakingOverOtherRigModifiers() throws Exception {
        var scene=scene("animation_probe",".fbx");
        try(var state=new AnimationState("ik",scene)) {
            state.initialize();var handle=new AssimpModelHandle(state);
            handle.processor("reach",options("""
                    {"version":1,"ik":{"hand":{"chain":["Shoulder","Elbow","ToolSocket"],"target":[0.5,1.4,0.1],
                    "enabled":false,"weight":0.5,"iterations":24,"tolerance":0.01}}}
                    """));
            assertEquals(PoseModifier.Category.GENERAL,state.getModifier("kubejs:processor:reach").category());
            assertEquals(PoseModifier.Category.IK,state.getModifier("kubejs:processor:reach:ik").category());
            handle.processorTarget("reach","hand",.4,1.2,.2).processorEnabled("reach","hand",true);
            state.update(.05f);
            assertTrue(state.getPoseSnapshot().getNodeTransform("ToolSocket").isFinite());
            assertThrows(IllegalArgumentException.class,()->handle.processorTarget("reach","hand",Double.NaN,0,0));
            assertThrows(IllegalArgumentException.class,()->handle.processorTarget("reach","hand",1e7,0,0));
            handle.removeProcessor("reach");assertNull(state.getModifier("kubejs:processor:reach:ik"));
        }
    }

    @Test void schemaRejectsUnknownFieldsInvalidTypesAndUnboundedInputsBeforeInstallation() throws Exception {
        var scene=scene();
        for(String json:List.of(
                "{\"version\":1.5}","{\"version\":\"1\"}","{\"version\":1,\"enabled\":\"true\"}",
                "{\"version\":1,\"postPhysics\":true}",
                "{\"version\":1,\"poses\":{\"pose\":{\"bones\":{\"missing\":{}}}}}",
                "{\"version\":1,\"clips\":{\"wave\":{\"clip\":\"Wave\",\"weight\":-0.1}}}",
                "{\"version\":1,\"clips\":{\"wave\":{\"clip\":\"Wave\",\"enabled\":1}}}",
                "{\"version\":1,\"clips\":{\"wave\":{\"clip\":\"Wave\",\"speed\":20}}}",
                "{\"version\":1,\"poses\":{\"pose\":{\"bones\":{\"B255\":{\"scale\":[0,1,1]}}}}}",
                "{\"version\":1,\"poses\":{\"pose\":{\"bones\":{\"B255\":{\"rotation\":[0,\"90\",0]}}}}}",
                "{\"version\":1,\"poses\":{\"pose\":{\"bones\":{},\"stage\":\"post_physics\"}}}"))
            assertThrows(IllegalArgumentException.class,()->compile(scene,json),json);
        assertThrows(IllegalArgumentException.class,()->ModelAnimationProcessor.read(scene,"pack:oversized",new StringReader(" ".repeat(65_537))));
    }

    @Test void sourceDefinitionIsCompiledIntoOwnedStateAndNoLongerReadsMutableJson() throws Exception {
        var scene=scene();var json=JsonParser.parseString(declaration()).getAsJsonObject();
        try(var processor=ModelAnimationProcessor.compile(scene,json)) {
            json.getAsJsonObject("clips").getAsJsonObject("wave").addProperty("clip","missing");
            processor.phase("wave",.3);var locals=new LinkedHashMap<String,Matrix4f>();
            processor.apply(scene,locals,0);assertTrue(locals.get("B255").isFinite());
            processor.close();assertThrows(IllegalStateException.class,()->processor.weight("wave",.5));
        }
    }
}
