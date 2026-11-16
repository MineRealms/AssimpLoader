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
            if(!name.equals("highbone_probe")&&!name.equals("deer_showcase")) throw new IllegalArgumentException("Unknown acceptance fixture");
            String base="/assets/assimploader/models/validation/"+name;
            try(var model=KubeJSSmokeTest.class.getResourceAsStream(base+".glb");
                var definition=KubeJSSmokeTest.class.getResourceAsStream(base+".instance.json")) {
                if(model==null||definition==null) throw new IllegalStateException("Missing acceptance fixture");
                var scene=new AssimpModelLoader().loadModelFromMemory(base+".glb",model.readAllBytes(),ModelImportMode.DYNAMIC);
                AssimpModelLoader.registerModel(new net.minecraft.resources.ResourceLocation("assimploader","models/validation/"+name+".glb"),scene);
                var instance=ModelInstanceDefinition.read(new InputStreamReader(definition,StandardCharsets.UTF_8)).instantiate(scene);
                return new Probe(instance.animation());
            }
        }
        public void success() {result="ok";AssimpLoader.LOGGER.info("[KubeJSAssimp] JavaScript assertions passed");}
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
