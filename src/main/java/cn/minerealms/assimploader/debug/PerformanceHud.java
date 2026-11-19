package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.client.WorldModelBatches;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Independent from F3 and debug geometry; samples text four times per second. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class PerformanceHud {
    private static final float[] history=new float[120];
    private static int cursor,count;
    private static long lastFrame,nextText;
    private static List<String> text=List.of();
    private PerformanceHud(){}
    private static boolean enabled(){return AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.debugView.hud::get,false);}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;
        if(!enabled()){lastFrame=0;count=cursor=0;return;}
        long now=System.nanoTime();
        if(lastFrame!=0){history[cursor]=(now-lastFrame)/1_000_000f;cursor=(cursor+1)%history.length;count=Math.min(history.length,count+1);}
        lastFrame=now;
        if(now>=nextText){sample();nextText=now+250_000_000;}
    }
    private static void sample() {
        var runtime=ClientAnimationManager.getInstance().getPerformanceStatistics();
        var cache=OculusEntityBufferCache.poseStatistics();var compute=ClientRenderResources.computeStatistics();
        var batch=WorldModelBatches.statistics();
        float[] sorted=Arrays.copyOf(history,count);Arrays.sort(sorted);
        float mean=0;for(float value:sorted)mean+=value;mean=count==0?0:mean/count;
        float p95=count==0?0:sorted[Math.min(count-1,(int)Math.ceil(count*.95)-1)];
        var memory=Runtime.getRuntime();
        text=List.of("ASSIMPLOADER  "+runtime.mode(),
                String.format(Locale.ROOT,"%d FPS  frame %.2f ms  p95 %.2f",Minecraft.getInstance().getFps(),mean,p95),
                "Instances "+runtime.instances()+"  eval/skip/pause "+runtime.evaluated()+"/"+runtime.skipped()+"/"+runtime.paused(),
                "Oculus/Iris "+(OculusCompat.isShadersActive()?"shader pack":OculusCompat.isOculusLoaded()?"installed / vanilla":"absent")+"  view "+MaterialDebugViews.current(),
                "Pose buffers "+cache.buffers()+"  MiB "+String.format(Locale.ROOT,"%.1f",cache.residentBytes()/1048576d),
                "Compute dispatch/hit "+compute.dispatches()+"/"+compute.hits()+"  MiB "+String.format(Locale.ROOT,"%.1f",compute.bytes()/1048576d),
                batch==null?"Batch inactive":"Batch instances/draws "+batch.lastInstances()+"/"+batch.lastSubmissions()+"  HiZ "+batch.lastHiZ(),
                "Heap MiB "+(memory.totalMemory()-memory.freeMemory())/1048576);
        var extended=new java.util.ArrayList<>(text);extended.addAll(ClientDiagnostics.lines());text=List.copyOf(extended);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(!enabled() || mc.player==null || mc.screen!=null)return;
        var graphics=event.getGuiGraphics();
        float scale=Math.min(AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.debugView.hudScale,1),
                Math.min(mc.getWindow().getGuiScaledWidth()/340f,mc.getWindow().getGuiScaledHeight()/155f));
        if(scale<=0)return;
        graphics.pose().pushPose();graphics.pose().scale(scale,scale,1);
        int width=326,height=24+text.size()*11+28;
        graphics.fill(8,8,width+8,height,0xd914171a);graphics.fill(8,8,10,height,0xff58d5bd);
        int y=16;
        for(int i=0;i<text.size();i++) {
            graphics.drawString(mc.font,mc.font.plainSubstrByWidth(text.get(i),width-16),17,y,i==0?0xff58d5bd:0xffdce4e9,false);y+=11;
        }
        int graphY=y+5;
        for(int i=0;i<count;i++) {
            float value=history[(cursor-count+i+history.length)%history.length];
            int h=Math.min(24,Math.max(1,(int)(value*.6f))),x=17+i*2;
            graphics.fill(x,graphY+24-h,x+1,graphY+24,value>33.4?0xfff08181:value>16.7?0xffe7ca72:0xff58d5bd);
        }
        graphics.pose().popPose();
    }
}
