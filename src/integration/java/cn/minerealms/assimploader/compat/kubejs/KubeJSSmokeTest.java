package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ModelInstanceDefinition;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Numerical real-Rhino acceptance only; never creates a production script binding by default. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class KubeJSSmokeTest {
    private static String result;
    private static final com.google.gson.JsonObject checks=new com.google.gson.JsonObject();
    private static boolean done;
    private static long titleAt;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!Boolean.getBoolean("assimp.kubejsSmoke")||done||event.phase!=TickEvent.Phase.END) return;
        var client=Minecraft.getInstance();
        if(!(client.screen instanceof TitleScreen)) return;
        if(titleAt==0) titleAt=System.currentTimeMillis();
        if(result==null&&System.currentTimeMillis()-titleAt<15_000) return;
        done=true;
        try {
            var json=new com.google.gson.JsonObject();json.addProperty("success","ok".equals(result));
            json.addProperty("failure",result==null?"acceptance script did not execute":result);
            json.add("checks",checks.deepCopy());
            java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("kubejs-smoke-result.json"),json.toString());
        } catch(java.io.IOException exception) {throw new IllegalStateException("Cannot write KubeJS acceptance result",exception);}
        if("ok".equals(result)) AssimpLoader.LOGGER.info("[KubeJSAssimp] SUCCESS real client_scripts binding, arrays, maps, layers, Morph, queries and expired handles");
        else AssimpLoader.LOGGER.error("[KubeJSAssimp] FAILED {}",result==null?"acceptance script did not execute":result);
        client.stop();
    }

    public static final class ProbeApi {
        public void onClient(Runnable callback) {
            Minecraft.getInstance().execute(()->{
                try {callback.run();}
                catch(Throwable error) {result=error.toString();AssimpLoader.LOGGER.error("[KubeJSAssimp] Client callback failed",error);}
            });
        }
        public Probe create(String name) throws Exception {
            if(!name.equals("highbone_probe")&&!name.equals("deer_showcase")&&!name.equals("animation_probe_morph")) throw new IllegalArgumentException("Unknown acceptance fixture");
            String namespace="assimploader";
            String base="models/validation/"+name;
            String extension=".glb";
            var resources=Minecraft.getInstance().getResourceManager();
            // Other mods own these assets; Java module-local class resources cannot see them after extraction.
            try(var model=resources.open(new net.minecraft.resources.ResourceLocation(namespace,base+extension))) {
                var scene=new AssimpModelLoader().loadModelFromMemory(base+extension,model.readAllBytes(),ModelImportMode.DYNAMIC);
                if(scene==null)throw new IllegalStateException("Acceptance fixture did not import: "+name);
                AssimpModelLoader.registerModel(new net.minecraft.resources.ResourceLocation("assimploader","models/validation/"+name+".glb"),scene);
                var sidecar=resources.getResource(new net.minecraft.resources.ResourceLocation(namespace,base+".instance.json"));
                if(sidecar.isPresent())try(var definition=sidecar.get().open()) {
                    var instance=ModelInstanceDefinition.read(new InputStreamReader(definition,StandardCharsets.UTF_8)).instantiate(scene);
                    return new Probe(instance.animation());
                }
                var state=new AnimationState(name,scene);state.initialize();return new Probe(state);
            }
        }
        public void record(String name,String json) {checks.add(name,com.google.gson.JsonParser.parseString(json));}
        public void success() {
            var gpu=cn.minerealms.assimploader.client.ShaderParameterGpuChecks.verify();
            checks.add("shaderParameters",new com.google.gson.Gson().toJsonTree(gpu));
            result="ok";AssimpLoader.LOGGER.info("[KubeJSAssimp] JavaScript assertions passed; GPU shader parameters {}",gpu);
        }
        public void failure(String error) {result=error;AssimpLoader.LOGGER.error("[KubeJSAssimp] JavaScript assertion failed: {}",error);}
    }
    public static final class Probe implements AutoCloseable {
        private final AnimationState state;
        private final java.util.Map<String,cn.minerealms.assimploader.animation.PoseModifier> checkpoints=new java.util.LinkedHashMap<>();
        Probe(AnimationState state) {this.state=state;}
        public AssimpModelHandle handle() {return new AssimpModelHandle(state);}
        public void step(double seconds) {state.update((float)seconds);}
        public double seconds() {return state.getSimulationTime();}
        public double morphWeight(int mesh,int target) {return state.getPoseSnapshot().getMorphWeights(state.getScene().getMeshes().get(mesh))[target];}
        public void checkpointProcessor(String id) {
            String key="kubejs:processor:"+id;
            if(!(state.getModifier(key) instanceof cn.minerealms.assimploader.animation.ModelAnimationProcessor))
                throw new IllegalArgumentException("No acceptance processor: "+id);
            checkpoints.put(key,state.getModifier(key));checkpoints.put(key+":ik",state.getModifier(key+":ik"));
        }
        public boolean processorPreserved(String id) {
            String key="kubejs:processor:"+id;var previous=checkpoints.get(key);
            return previous instanceof cn.minerealms.assimploader.animation.ModelAnimationProcessor processor&&!processor.isClosed()
                    &&state.getModifier(key)==previous&&state.getModifier(key+":ik")==checkpoints.get(key+":ik");
        }
        @Override public void close() {checkpoints.clear();state.close();}
    }
}
