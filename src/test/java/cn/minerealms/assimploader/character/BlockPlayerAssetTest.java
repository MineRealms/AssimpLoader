package cn.minerealms.assimploader.character;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockPlayerAssetTest {
    @Test void fbxAndGlbPreserveRigClipsAndFiniteWeightedGeometry() throws Exception {
        for (String format : List.of("fbx", "glb")) {
            var scene = new AssimpModelLoader().loadModel(Path.of("src/main/resources/assets/assimploader/models/character/block_player/block_player." + format), ModelImportMode.DYNAMIC);
            assertTrue(scene.hasSkeleton());
            var graph = new PoseGraph(scene.getRootNode(), new java.util.HashMap<>());
            for (String bone : List.of("Hips", "Head", "Thigh.L", "Shin.R", "Hair2", "Pendant2"))
                assertTrue(graph.contains(bone), bone);
            try (var state = new AnimationState("block_player", scene)) {
                state.initialize();
                var clips = List.of(state.getAvailableAnimations());
                System.out.println("Block player " + format + " clips=" + clips);
                System.out.println("Block player " + format + " Hips=" + graph.position("Hips") + " Head=" + graph.position("Head") + " Foot=" + graph.position("Foot.L"));
                for (String requested : List.of("idle", "walk", "run", "crouch", "jump", "swim", "dead", "swing")) {
                    String clip = clips.stream().filter(n -> n.equals(requested) || n.endsWith("|" + requested)).findFirst().orElseThrow(() -> new AssertionError("Missing " + requested + " in " + clips));
                    assertTrue(state.transitionTo(clip, 0));
                    state.update(.25f);
                    var low = new Vector3f(Float.POSITIVE_INFINITY);
                    var high = new Vector3f(Float.NEGATIVE_INFINITY);
                    for (var mesh : scene.getMeshes()) {
                        float[] vertices = CpuSkinning.geometry(state.getPoseSnapshot(), mesh).positions();
                        for (int i=0;i<vertices.length;i+=3) {
                            var p = new Vector3f(vertices[i], vertices[i+1], vertices[i+2]);
                            assertTrue(p.isFinite()); low.min(p); high.max(p);
                        }
                    }
                    assertTrue(high.distance(low) < 4, () -> format + " invalid units " + low + " / " + high);
                    if (requested.equals("idle")) {
                        System.out.println("Block player " + format + " bounds=" + low + " / " + high);
                        assertEquals(0, low.y, .025);
                        assertEquals(1.825, high.y, .04);
                    }
                }
            }
        }
    }
}
