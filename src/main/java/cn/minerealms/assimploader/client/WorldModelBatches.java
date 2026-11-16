package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.config.GpuBatchPolicy;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.GpuInstanceBatcher;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.util.ArrayList;
import java.util.List;

/** Batches configured entities within the normal client world pass, before translucent terrain. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class WorldModelBatches {
    private static GpuInstanceBatcher renderer;
    private static boolean active,failed,flushing;
    private static long frame;
    private static final Matrix4f projection=new Matrix4f();
    private static final List<Runnable> fallback=new ArrayList<>();
    private WorldModelBatches() {}

    public static GpuInstanceBatcher.Statistics statistics() {return renderer==null?null:renderer.statistics();}
    public static boolean isCollecting() {return active;}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.START) {active=false;fallback.clear();frame++;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY) {
            active=false;
            var policy=GpuBatchPolicy.current();
            boolean unavailable=!policy.enabled() || OculusCompat.shouldAvoidCustomShaders() || OculusCompat.isRenderingShadowPass()
                    || AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO)==AssimpLoaderConfig.OptimizationLevel.FALLBACK;
            if(renderer!=null && (unavailable || !renderer.policy().equals(policy))) {renderer.close();renderer=null;}
            if(failed || unavailable) return;
            if(!GpuInstanceBatcher.available()) return;
            try {
                if(renderer==null) renderer=new GpuInstanceBatcher(policy);
                renderer.begin();projection.set(event.getProjectionMatrix());active=true;
            } catch(Exception failure) {disable(failure);}
        } else if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES && active) {
            beforeBufferFlush();active=false;
        }
    }

    /** Called before vanilla submits any buffered surface, including an automatically flushed translucent entity. */
    public static void beforeBufferFlush() {
        if(!active || flushing || fallback.isEmpty() || renderer==null) return;
        flushing=true;
        var target=Minecraft.getInstance().getMainRenderTarget();
        int previous=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        try {
            target.bindWrite(true);
            int depth=projection.equals(RenderSystem.getProjectionMatrix(),.00001f)?target.getDepthTextureId():0;
            double[] depthRange=new double[2];GL11.glGetDoublev(GL11.GL_DEPTH_RANGE,depthRange);
            if(depthRange[0]!=0 || depthRange[1]!=1 || GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE)!=1
                    || GL.getCapabilities().OpenGL45 && (GL11.glGetInteger(GL45.GL_CLIP_DEPTH_MODE)!=GL45.GL_NEGATIVE_ONE_TO_ONE
                    || GL11.glGetInteger(GL45.GL_CLIP_ORIGIN)!=GL45.GL_LOWER_LEFT)) depth=0;
            renderer.flush(projection,depth,target.width,target.height,GL11.glGetInteger(GL13.GL_SAMPLES),frame);
            fallback.clear();renderer.begin();
        } catch(Exception failure) {disable(failure);}
        finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previous);GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);flushing=false;
        }
    }

    public static boolean submit(AssimpScene scene,AnimationState state,PoseSnapshot pose,PoseStack stack,
                                  MultiBufferSource buffers,int light,int overlay,Runnable immediate) {
        if(!active || renderer==null || OculusCompat.shouldAvoidCustomShaders() || OculusCompat.isRenderingShadowPass()) return false;
        if(state!=null && state.getModelInstance()!=null && state.getModelInstance().hasMaterialAnimation()) return false;
        if(scene.hasSkeleton() && !AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.gpuSkinningEnabled::get,true)) return false;
        if(!(buffers instanceof MultiBufferSource.BufferSource)) return false;
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=Minecraft.getInstance().getMainRenderTarget().frameBufferId) return false;
        try {
            if(!renderer.submit(scene,pose,new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose()),light,overlay)) return false;
            fallback.add(immediate);
            if(state!=null&&state.getModelInstance()!=null) state.getModelInstance().noteRenderPass(false);
            return true;
        } catch(Exception failure) {disable(failure);return false;}
    }
    private static void disable(Exception failure) {
        failed=true;active=false;
        AssimpLoader.LOGGER.warn("GPU world batching disabled until resource reload; using immediate rendering",failure);
        if(renderer!=null) renderer.close();renderer=null;
        for(var draw:fallback) draw.run();
        fallback.clear();
    }
    public static void clear() {
        active=failed=flushing=false;fallback.clear();
        if(renderer!=null) renderer.close();renderer=null;
    }
}
