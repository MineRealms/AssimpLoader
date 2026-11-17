package cn.minerealms.rigworks.character;

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
    @Test void pbrImagesContainAuthoredColorsAndTangentNormal() throws Exception {
        String base="/assets/rigworks/models/character/block_player/";
        try(var input=getClass().getResourceAsStream(base+"block_player_albedo.png")) {
            var image=javax.imageio.ImageIO.read(java.util.Objects.requireNonNull(input));
            var colors=new java.util.HashSet<Integer>();
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors.add(image.getRGB(x,y));
            assertTrue(colors.size()>32,"Color texture must not be blank or uniform");
        }
        try(var input=getClass().getResourceAsStream(base+"block_player_normal.png")) {
            var image=javax.imageio.ImageIO.read(java.util.Objects.requireNonNull(input));
            int pixel=image.getRGB(0,0);
            assertEquals(128,(pixel>>16)&255,1);assertEquals(128,(pixel>>8)&255,1);assertEquals(255,pixel&255);
        }
    }
    @Test void fbxAndGlbPreserveRigClipsAndFiniteWeightedGeometry() throws Exception {
        for (String format : List.of("fbx", "glb")) {
            var scene = new AssimpModelLoader().loadModel(Path.of("src/main/resources/assets/rigworks/models/character/block_player/block_player." + format), ModelImportMode.DYNAMIC);
            assertTrue(scene.hasSkeleton());
            var graph = new PoseGraph(scene.getRootNode(), new java.util.HashMap<>());
            for (String bone : List.of("Hips", "Head", "Thigh.L", "Shin.R", "Hair2", "Pendant2"))
                assertTrue(graph.contains(bone), bone);
            try (var state = new AnimationState("block_player", scene)) {
                state.initialize();
                var clips = List.of(state.getAvailableAnimations());
                assertEquals(15,clips.size());
                boolean blended=false;
                for(var mesh:scene.getMeshes()) {
                    assertEquals(java.util.Set.of("Blink","Smile"),mesh.getMorphTargets().stream().map(t->t.getName()).collect(java.util.stream.Collectors.toSet()));
                    var data=mesh.getSkinnedData();assertNotNull(data);
                    var weights=data.getBoneWeights();int stride=weights.length/data.getVertexCount();
                    for(int vertex=0;vertex<data.getVertexCount();vertex++) {
                        float sum=0;int influences=0;
                        for(int i=0;i<stride;i++){float w=weights[vertex*stride+i];sum+=w;if(w>0)influences++;}
                        assertEquals(1,sum,1e-5);blended|=influences>1;
                    }
                }
                assertTrue(blended,"The fixture must exercise genuinely blended skin weights");
                var usedMaterials=scene.getMeshes().stream().map(mesh->mesh.getMaterialIndex()).collect(java.util.stream.Collectors.toSet());
                for(var material:scene.getMaterials()) {
                    if(!usedMaterials.contains(material.getIndex()))continue;
                    System.out.println("Block player "+format+" material="+material.getName()+" diffuse="+material.getDiffuseTexture()+" normal="+material.getNormalTexture()+" pbr="+material.getPbrInputs());
                    assertNotNull(material.getDiffuseTexture(),format+" "+material.getName());
                    assertNotNull(material.getNormalTexture());
                    assertNotNull(material.getPbrInputs().roughness());
                    assertNotNull(material.getPbrInputs().metallic());
                }
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
