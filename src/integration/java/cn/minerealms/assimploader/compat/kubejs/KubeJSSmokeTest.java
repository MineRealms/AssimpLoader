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
            if(!name.equals("highbone_probe")&&!name.equals("deer_showcase")&&!name.equals("block_player")) throw new IllegalArgumentException("Unknown acceptance fixture");
            String namespace=name.equals("block_player")?"rigworks":"assimploader";
            String base=name.equals("block_player")?"models/character/block_player/block_player":"models/validation/"+name;
            String extension=name.equals("block_player")?".fbx":".glb";
            var resources=Minecraft.getInstance().getResourceManager();
            // Other mods own these assets; Java module-local class resources cannot see them after extraction.
            try(var model=resources.open(new net.minecraft.resources.ResourceLocation(namespace,base+extension));
                var definition=resources.open(new net.minecraft.resources.ResourceLocation(namespace,base+".instance.json"))) {
                var scene=new AssimpModelLoader().loadModelFromMemory(base+extension,model.readAllBytes(),ModelImportMode.DYNAMIC);
                AssimpModelLoader.registerModel(new net.minecraft.resources.ResourceLocation("assimploader","models/validation/"+name+".glb"),scene);
                var instance=ModelInstanceDefinition.read(new InputStreamReader(definition,StandardCharsets.UTF_8)).instantiate(scene);
                return new Probe(instance.animation());
            }
        }
        public void success() {
            var gpu=cn.minerealms.assimploader.client.ShaderParameterGpuChecks.verify();
            result="ok";AssimpLoader.LOGGER.info("[KubeJSAssimp] JavaScript assertions passed; GPU shader parameters {}",gpu);
        }
        public void failure(String error) {result=error;AssimpLoader.LOGGER.error("[KubeJSAssimp] JavaScript assertion failed: {}",error);}
    }
    public static final class Probe implements AutoCloseable {
        private final AnimationState state;
        Probe(AnimationState state) {this.state=state;}
        public AssimpModelHandle handle() {return new AssimpModelHandle(state);}
        public void step(double seconds) {state.update((float)seconds);}
        public double seconds() {return state.getSimulationTime();}
        @Override public void close() {state.close();}
    }
}
