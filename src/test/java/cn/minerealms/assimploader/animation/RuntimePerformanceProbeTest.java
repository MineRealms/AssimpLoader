package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in CPU probe, not a frame-rate or GPU benchmark. Writes measured samples for review. */
@EnabledIfSystemProperty(named="assimp.runtimeBenchmark",matches="true")
class RuntimePerformanceProbeTest {
    private static volatile float sink;
    @Test void measurePoseAndCpuSubmissionInputs() throws Exception {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int bones:new int[]{64,256}) {
            var scene=new AssimpModelLoader().loadModelFromMemory("probe.gltf",fixture(bones),ModelImportMode.DYNAMIC);
            assertNotNull(scene);
            var mesh=scene.getMeshes().get(0);
            assertEquals(bones,mesh.getBoneOffsets().size());
            for(int count:new int[]{1,10,50}) for(boolean deform:new boolean[]{false,true}) {
                List<AnimationState> states=new ArrayList<>();
                for(int i=0;i<count;i++) { var state=new AnimationState("probe",scene); state.initialize(); states.add(state); }
                for(int i=0;i<100;i++) step(states,deform);
                double[] samples=new double[120];
                for(int i=0;i<samples.length;i++) {
                    long start=System.nanoTime(); step(states,deform); samples[i]=(System.nanoTime()-start)/1e6;
                }
                Arrays.sort(samples);
                rows.add(Map.of("bones",bones,"vertices",mesh.getVertexCount(),"instances",count,
                        "path",deform?"pose_and_cpu_skinning":"pose_only","medianMs",samples[60],"p95Ms",samples[113]));
                states.forEach(AnimationState::close); CpuSkinning.clear();
            }
        }
        Path output=Path.of("build/reports/runtime-benchmark.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "java",System.getProperty("java.version"),"os",System.getProperty("os.name"),
                "processors",Runtime.getRuntime().availableProcessors(),"warmup",100,"samples",120,"results",rows)));
        System.out.println("Runtime CPU benchmark: "+output.toAbsolutePath());
    }
    private static void step(List<AnimationState> states,boolean deform) {
        for(var state:states) {
            state.update(.05f);
            if(deform) sink=CpuSkinning.geometry(state.getPoseSnapshot(),state.getScene().getMeshes().get(0)).positions()[0];
        }
    }
    private static byte[] fixture(int bones) {
        int vertices=4095, positions=vertices*12, joints=vertices*8, weights=vertices*16, indices=vertices*4;
        ByteBuffer bytes=ByteBuffer.allocate(positions+joints+weights+indices+bones*64).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<vertices;i++) bytes.putFloat(i%64*.01f).putFloat(i/64*.01f).putFloat((i%3)*.01f);
        for(int i=0;i<vertices;i++) for(int j=0;j<4;j++) bytes.putShort((short)((i+j)%bones));
        for(int i=0;i<vertices;i++) for(int j=0;j<4;j++) bytes.putFloat(.25f);
        for(int i=0;i<vertices;i++) bytes.putInt(i);
        for(int i=0;i<bones;i++) for(int c=0;c<16;c++) bytes.putFloat(c%5==0?1:0);
        JsonObject root=new JsonObject();
        root.add("asset",com.google.gson.JsonParser.parseString("{\"version\":\"2.0\"}"));
        JsonObject buffer=new JsonObject(); buffer.addProperty("byteLength",bytes.capacity());
        buffer.addProperty("uri","data:application/octet-stream;base64,"+Base64.getEncoder().encodeToString(bytes.array()));
        root.add("buffers",list(buffer));
        JsonArray views=new JsonArray(); int offset=0;
        for(int size:new int[]{positions,joints,weights,indices,bones*64}) {
            JsonObject view=new JsonObject(); view.addProperty("buffer",0); view.addProperty("byteOffset",offset); view.addProperty("byteLength",size);
            views.add(view); offset+=size;
        }
        root.add("bufferViews",views);
        JsonArray accessors=new JsonArray();
        accessors.add(accessor(0,5126,vertices,"VEC3")); accessors.add(accessor(1,5123,vertices,"VEC4"));
        accessors.add(accessor(2,5126,vertices,"VEC4")); accessors.add(accessor(3,5125,vertices,"SCALAR"));
        accessors.add(accessor(4,5126,bones,"MAT4")); root.add("accessors",accessors);
        JsonObject attributes=new JsonObject(); attributes.addProperty("POSITION",0); attributes.addProperty("JOINTS_0",1); attributes.addProperty("WEIGHTS_0",2);
        JsonObject primitive=new JsonObject(); primitive.add("attributes",attributes); primitive.addProperty("indices",3);
        JsonObject mesh=new JsonObject(); mesh.add("primitives",list(primitive)); root.add("meshes",list(mesh));
        JsonArray nodes=new JsonArray(); JsonObject meshNode=new JsonObject(); meshNode.addProperty("mesh",0); meshNode.addProperty("skin",0); nodes.add(meshNode);
        JsonArray boneIndices=new JsonArray(),sceneNodes=new JsonArray(); sceneNodes.add(0); sceneNodes.add(1);
        for(int i=0;i<bones;i++) {
            JsonObject node=new JsonObject(); node.addProperty("name","Bone"+i);
            if(i+1<bones) { JsonArray child=new JsonArray(); child.add(i+2); node.add("children",child); }
            nodes.add(node); boneIndices.add(i+1);
        }
        root.add("nodes",nodes);
        JsonObject skin=new JsonObject(); skin.addProperty("inverseBindMatrices",4); skin.add("joints",boneIndices); root.add("skins",list(skin));
        JsonObject scene=new JsonObject(); scene.add("nodes",sceneNodes); root.add("scenes",list(scene)); root.addProperty("scene",0);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static JsonObject accessor(int view,int type,int count,String shape) {
        JsonObject result=new JsonObject(); result.addProperty("bufferView",view); result.addProperty("componentType",type);
        result.addProperty("count",count); result.addProperty("type",shape); return result;
    }
    private static JsonArray list(JsonObject value) { JsonArray result=new JsonArray(); result.add(value); return result; }
}
