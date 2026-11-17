package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.compat.kubejs.AssimpModelHandle;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PoseEvaluationPlanTest {
    private static final class Probe implements PoseModifier {
        final Category stage;
        final Set<String> after, bones;
        final List<String> calls;
        final String name;
        int closed, metadataReads;
        Probe(String name, Category stage, Set<String> after, Set<String> bones, List<String> calls) {
            this.name=name;this.stage=stage;this.after=after;this.bones=bones;this.calls=calls;
        }
        @Override public Category category(){metadataReads++;return stage;}
        @Override public Set<String> dependencies(){metadataReads++;return after;}
        @Override public Set<String> controlledBones(){metadataReads++;return bones;}
        @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta){calls.add(name);}
        @Override public void applyMorphs(AssimpScene scene,Map<AssimpMesh,float[]> weights){calls.add(name+":morph");}
        @Override public void close(){closed++;}
    }
    private static Probe probe(String name, PoseModifier.Category stage, String... after) {
        return new Probe(name,stage,Set.of(after),Set.of(),new ArrayList<>());
    }
    private static LinkedHashMap<String,PoseModifier> entries(Probe... probes) {
        var result=new LinkedHashMap<String,PoseModifier>();for(var probe:probes)result.put(probe.name,probe);return result;
    }
    private static List<String> ids(PoseEvaluationPlan plan){return plan.stages().stream().map(PoseEvaluationPlan.Stage::id).toList();}
    private static List<String> schedule(AnimationState state){return state.evaluationSchedule().stream().map(value->(String)value.get("id")).toList();}

    @Test void defaultOrderPreservesCategoriesAndInsertionOrderWithinEachCategory() {
        var plan=PoseEvaluationPlan.compile(entries(probe("ik",PoseModifier.Category.IK),
                probe("overlay_b",PoseModifier.Category.GENERAL),probe("motion",PoseModifier.Category.MOTION),
                probe("overlay_a",PoseModifier.Category.GENERAL),probe("spring",PoseModifier.Category.SPRING)));
        assertEquals(List.of("motion","overlay_b","overlay_a","ik","spring"),ids(plan));
    }

    @Test void topologyUsesStableReadyOrderAndAcceptsEarlierCategoryDependencies() {
        var plan=PoseEvaluationPlan.compile(entries(probe("dependent",PoseModifier.Category.GENERAL,"root","motion"),
                probe("unrelated",PoseModifier.Category.GENERAL),probe("root",PoseModifier.Category.GENERAL),
                probe("later",PoseModifier.Category.IK,"dependent"),probe("motion",PoseModifier.Category.MOTION)));
        assertEquals(List.of("motion","unrelated","root","dependent","later"),ids(plan));
    }

    @Test void missingBackwardAndCyclicDependenciesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(entries(probe("a",PoseModifier.Category.GENERAL,"missing"))));
        assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(entries(
                probe("a",PoseModifier.Category.GENERAL,"ik"),probe("ik",PoseModifier.Category.IK))));
        assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(entries(
                probe("a",PoseModifier.Category.GENERAL,"b"),probe("b",PoseModifier.Category.GENERAL,"a"))));
        assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(entries(probe("a",PoseModifier.Category.GENERAL,"a"))));
    }

    @Test void planSizeAndDependencyBudgetsBoundCompilationWork() {
        var excessive=new LinkedHashMap<String,PoseModifier>();
        for(int index=0;index<=PoseEvaluationPlan.MAX_MODIFIERS;index++) {
            String id="stage_"+index;excessive.put(id,probe(id,PoseModifier.Category.GENERAL));
        }
        assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(excessive));
        var dependencies=new LinkedHashSet<String>();
        for(int index=0;index<=PoseEvaluationPlan.MAX_DEPENDENCIES;index++)dependencies.add("input_"+index);
        var stage=new Probe("excessive",PoseModifier.Category.GENERAL,dependencies,Set.of(),new ArrayList<>());
        assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(entries(stage)));
    }

    @Test void failedReplacementAndDependentRemovalKeepTheOldPlanAndOwnership() {
        try(var state=new AnimationState("transaction",(AssimpScene)null)) {
            var a=probe("a",PoseModifier.Category.GENERAL);var b=probe("b",PoseModifier.Category.GENERAL,"a");
            state.replaceModifiers(entries(a,b));var before=state.evaluationSchedule();
            var invalid=probe("a",PoseModifier.Category.GENERAL,"b");
            assertThrows(IllegalArgumentException.class,()->state.addModifier("a",invalid));
            assertSame(a,state.getModifier("a"));assertSame(b,state.getModifier("b"));assertSame(before,state.evaluationSchedule());
            assertEquals(0,a.closed);assertEquals(0,b.closed);assertEquals(0,invalid.closed,"Rejected incoming modifiers remain caller-owned");
            assertThrows(IllegalArgumentException.class,()->state.removeModifier("a"));
            assertSame(before,state.evaluationSchedule());assertEquals(0,a.closed);
            state.removeModifiers(Set.of("a","b"));assertTrue(state.evaluationSchedule().isEmpty());
            assertEquals(1,a.closed);assertEquals(1,b.closed);
        }
    }

    @Test void batchChangesPermitForwardReferencesAndPublishOnlyCompleteCandidates() {
        try(var state=new AnimationState("batch",(AssimpScene)null)) {
            var a=probe("a",PoseModifier.Category.GENERAL,"b");var b=probe("b",PoseModifier.Category.GENERAL);
            state.replaceModifiers(entries(a,b));assertEquals(List.of("b","a"),schedule(state));
            var replacement=probe("a",PoseModifier.Category.GENERAL,"c");var c=probe("c",PoseModifier.Category.GENERAL);
            state.replaceModifiers(entries(replacement,c),Set.of("b"));
            assertEquals(List.of("c","a"),schedule(state));assertSame(replacement,state.getModifier("a"));
            assertEquals(1,a.closed);assertEquals(1,b.closed);assertEquals(0,replacement.closed);
            state.addModifier("a",replacement);assertEquals(0,replacement.closed,"Reinstalling an owned object cannot close it");
        }
    }

    @Test void physicsAndSpringAreExclusiveWritersWhileIkCanFeedPhysicsOnTheSameBone() {
        for(var second:List.of(PoseModifier.Category.PHYSICS,PoseModifier.Category.SPRING)) {
            var physics=new Probe("physics",PoseModifier.Category.PHYSICS,Set.of(),Set.of("head"),new ArrayList<>());
            var other=new Probe("other",second,Set.of(),Set.of("head"),new ArrayList<>());
            assertThrows(IllegalArgumentException.class,()->PoseEvaluationPlan.compile(entries(physics,other)));
        }
        var ik=new Probe("ik",PoseModifier.Category.IK,Set.of(),Set.of("head"),new ArrayList<>());
        var physics=new Probe("physics",PoseModifier.Category.PHYSICS,Set.of("ik"),Set.of("head"),new ArrayList<>());
        var spring=new Probe("tail",PoseModifier.Category.SPRING,Set.of(),Set.of("tail"),new ArrayList<>());
        assertEquals(List.of("ik","physics","tail"),ids(PoseEvaluationPlan.compile(entries(spring,physics,ik))));
        try(var state=new AnimationState("exclusive",(AssimpScene)null)) {
            state.replaceModifiers(entries(physics,ik));var before=state.evaluationSchedule();
            var conflict=new Probe("conflict",PoseModifier.Category.SPRING,Set.of(),Set.of("head"),new ArrayList<>());
            assertThrows(IllegalArgumentException.class,()->state.addModifier("conflict",conflict));
            assertSame(before,state.evaluationSchedule());assertEquals(0,physics.closed);
        }
    }

    @Test void runtimeUsesTheCompiledPlanForBothPoseAndMorphWithoutReadingMetadataAgain() {
        var calls=new ArrayList<String>();var after=new LinkedHashSet<>(Set.of("root"));
        var dependent=new Probe("dependent",PoseModifier.Category.GENERAL,after,Set.of(),calls);
        var root=new Probe("root",PoseModifier.Category.GENERAL,Set.of(),Set.of(),calls);
        try(var state=new AnimationState("runtime",AnimationTestScenes.create())) {
            state.initialize();state.replaceModifiers(entries(dependent,root));
            int metadataReads=dependent.metadataReads+root.metadataReads;
            var diagnostic=state.evaluationSchedule();after.clear();after.add("missing");
            calls.clear();state.update(.05f);
            assertEquals(List.of("root","dependent","root:morph","dependent:morph"),calls);
            assertEquals(metadataReads,dependent.metadataReads+root.metadataReads);
            assertEquals(List.of("root"),diagnostic.get(1).get("after"),"The compiled dependency snapshot is immutable");
            assertThrows(UnsupportedOperationException.class,diagnostic::clear);
            assertThrows(UnsupportedOperationException.class,()->diagnostic.get(0).put("id","modified"));
            assertThrows(UnsupportedOperationException.class,()->((List<?>)diagnostic.get(1).get("after")).clear());
        }
    }

    @Test void retainedAliasesStayOpenAndRetiredDependentsCloseBeforeTheirInputs() {
        var closed=new ArrayList<String>();
        PoseModifier root=new PoseModifier() {
            @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta){}
            @Override public void close(){closed.add("root");}
        };
        PoseModifier dependent=new PoseModifier() {
            @Override public Set<String> dependencies(){return Set.of("root");}
            @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta){}
            @Override public void close(){closed.add("dependent");}
        };
        try(var state=new AnimationState("lifetime",(AssimpScene)null)) {
            var batch=new LinkedHashMap<String,PoseModifier>();batch.put("root",root);batch.put("dependent",dependent);
            state.replaceModifiers(batch);state.addModifier("root_alias",root);
            state.removeModifiers(Set.of("root","dependent"));
            assertEquals(List.of("dependent"),closed,"A retained alias still owns the shared modifier");
        }
        assertEquals(List.of("dependent","root"),closed,"Final close releases retained identities once");
        closed.clear();
        try(var state=new AnimationState("reverse-close",(AssimpScene)null)) {
            var batch=new LinkedHashMap<String,PoseModifier>();batch.put("root",root);batch.put("dependent",dependent);
            state.replaceModifiers(batch);
        }
        assertEquals(List.of("dependent","root"),closed);
    }

    @Test void processorsInstallCompanionDependenciesAndReplaceOrRemoveAtomically() {
        try(var state=new AnimationState("processor",MorphTargetTest.fixture())) {
            var handle=new AssimpModelHandle(state);
            handle.processorDefinition("base",Map.of("version",1));
            handle.processorDefinition("detail",Map.of("version",1,"after",List.of("kubejs:processor:base")));
            var old=(ModelAnimationProcessor)state.getModifier("kubejs:processor:base");
            var oldIk=state.getModifier("kubejs:processor:base:ik");var before=handle.schedule();
            assertEquals(Set.of("kubejs:processor:base"),oldIk.dependencies());
            assertThrows(IllegalArgumentException.class,()->handle.processorDefinition("base",
                    Map.of("version",1,"after",List.of("kubejs:processor:detail"))));
            assertSame(old,state.getModifier("kubejs:processor:base"));assertSame(oldIk,state.getModifier("kubejs:processor:base:ik"));
            assertFalse(old.isClosed());assertSame(before,handle.schedule());
            assertThrows(IllegalArgumentException.class,()->handle.removeProcessor("base"));
            assertSame(oldIk,state.getModifier("kubejs:processor:base:ik"));assertFalse(old.isClosed());
            handle.clearScriptControls();assertTrue(handle.schedule().isEmpty());assertTrue(old.isClosed());
        }
    }

    @Test void failedScriptCleanupPreservesTrackingForALaterSuccessfulCleanup() {
        try(var state=new AnimationState("cleanup",MorphTargetTest.fixture())) {
            var handle=new AssimpModelHandle(state);handle.processorDefinition("base",Map.of("version",1));
            var old=(ModelAnimationProcessor)state.getModifier("kubejs:processor:base");
            state.addModifier("asset",probe("asset",PoseModifier.Category.GENERAL,"kubejs:processor:base"));
            assertThrows(IllegalArgumentException.class,handle::clearScriptControls);assertFalse(old.isClosed());
            state.removeModifier("asset");handle.clearScriptControls();assertTrue(old.isClosed());assertTrue(handle.schedule().isEmpty());
        }
    }

    @Test void processorAfterSchemaRejectsUnboundedOrNonStringIds() {
        var scene=MorphTargetTest.fixture();
        for(String value:List.of("null","{}","1","[2]","[\"\"]"))
            assertThrows(IllegalArgumentException.class,()->ModelAnimationProcessor.compile(scene,
                    JsonParser.parseString("{\"version\":1,\"after\":"+value+"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->ModelAnimationProcessor.compile(scene,
                JsonParser.parseString("{\"version\":1,\"after\":["+String.join(",",java.util.Collections.nCopies(65,"\"a\""))+"]}").getAsJsonObject()));
    }
}
