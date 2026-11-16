package cn.minerealms.assimploader.config;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Objects;

/** Selects visual inputs only. Neither mode changes server collision, movement or hit authority. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public record ClientVisualPolicy(Mode mode,boolean localGroundPlacement,boolean localEffects) {
    public enum Mode { HOST_CONTROLLED, CLIENT_LOCAL }
    private static volatile ClientVisualPolicy current=new ClientVisualPolicy(Mode.HOST_CONTROLLED,true,false);
    public ClientVisualPolicy {Objects.requireNonNull(mode,"Visual input mode");}
    public static ClientVisualPolicy current() {return current;}
    public boolean ground(boolean hostValue) {return mode==Mode.CLIENT_LOCAL?localGroundPlacement:hostValue;}
    public boolean effects(boolean hostValue) {return mode==Mode.CLIENT_LOCAL?localEffects:hostValue;}
    public String animation(String hostState,String localState) {return mode==Mode.CLIENT_LOCAL?localState:hostState;}
    public static void refreshFromConfig() {
        var config=AssimpLoaderConfig.CLIENT;
        current=new ClientVisualPolicy(AssimpLoaderConfig.getOrDefault(config.visualMode::get,Mode.HOST_CONTROLLED),
                AssimpLoaderConfig.getOrDefault(config.localGroundPlacement::get,true),
                AssimpLoaderConfig.getOrDefault(config.localEffects::get,false));
    }
    @SubscribeEvent public static void loading(ModConfigEvent.Loading event) {changed(event);}
    @SubscribeEvent public static void reloading(ModConfigEvent.Reloading event) {changed(event);}
    private static void changed(ModConfigEvent event) {if(event.getConfig().getSpec()==AssimpLoaderConfig.CLIENT_SPEC) refreshFromConfig();}
}
