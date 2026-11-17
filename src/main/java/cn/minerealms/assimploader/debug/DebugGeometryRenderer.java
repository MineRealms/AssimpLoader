package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.character.client.LocalPlayerCharacter;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.SceneTransforms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;

/** Buffered lines reuse the submitted model transform and never advance a solver. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class DebugGeometryRenderer {
    private static final Map<AssimpScene,PoseSnapshot> DEFAULTS=new WeakHashMap<>();
    static {cn.minerealms.assimploader.core.AssimpModelLoader.addCacheInvalidationListener(()->Minecraft.getInstance().execute(DEFAULTS::clear));}
    private DebugGeometryRenderer(){}
    public static void render(AssimpScene scene,PoseSnapshot snapshot,ModelInstance instance,PoseStack poses,MultiBufferSource buffers) {
        if(!ClientDebugTools.enabled() || OculusCompat.isRenderingShadowPass())return;
        var config=AssimpLoaderConfig.CLIENT.debugView;
        if(snapshot==null)snapshot=DEFAULTS.computeIfAbsent(scene,value->PoseSnapshot.capture(0,value,Map.of()));
        var lines=buffers.getBuffer(RenderType.lines());
        if(config.bounds.get())LevelRenderer.renderLineBox(poses,lines,snapshot.getBounds(),.35f,.85f,1,1);
        if(config.axes.get())axes(poses,lines,new Vector3f(),.35f);
        if(config.skeleton.get())bones(scene.getRootNode(),snapshot,poses,lines,new int[]{256});
        if(instance!=null && config.collision.get()) {
            int count=0;
            for(var proxy:instance.collision().proxies()) {
                if(count++>=128)break;
                var capsule=proxy.at(snapshot);capsule(poses,lines,capsule.start(),capsule.end(),capsule.radius(),1,.65f,.25f);
            }
        }
        if(instance!=null && config.attachments.get()) {
            int count=0;
            for(var attachment:instance.attachments().values()) {
                if(count++>=128)break;
                var node=snapshot.getNodeTransform(attachment.node());if(node==null)continue;
                poses.pushPose();
                SceneTransforms.apply(poses,node.mul(attachment.offset()));axes(poses,lines,new Vector3f(),.12f);poses.popPose();
            }
        }
    }
    private static void bones(AssimpNode parent,PoseSnapshot pose,PoseStack poses,VertexConsumer lines,int[] remaining) {
        if(parent==null || remaining[0]--<=0)return;
        var origin=pose.getNodeTransform(parent.getName());if(origin==null)return;
        var a=origin.getTranslation(new Vector3f());
        for(var child:parent.getChildren()) {
            if(remaining[0]<=0)return;
            var transform=pose.getNodeTransform(child.getName());
            if(transform!=null) {
                var b=transform.getTranslation(new Vector3f());
                if(a.distanceSquared(b)>1e-10f){line(poses,lines,a,b,.35f,1,.65f);cross(poses,lines,b,.018f,.9f,1,.4f);}
            }
            bones(child,pose,poses,lines,remaining);
        }
    }
    @SubscribeEvent public static void physics(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES || !ClientDebugTools.enabled() || OculusCompat.isRenderingShadowPass())return;
        var config=AssimpLoaderConfig.CLIENT.debugView;
        if(!config.collision.get() && !config.worldCollision.get())return;
        var physics=LocalPlayerCharacter.currentPhysics();if(physics==null)return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();var anchor=physics.debugAnchor();
        var poses=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
        poses.pushPose();poses.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);
        for(var shape:physics.debugShapes(config.worldCollision.get())) {
            if(!shape.world() && !config.collision.get())continue;
            poses.pushPose();SceneTransforms.apply(poses,shape.transform());var size=shape.size();
            float r=shape.world()?.55f:1,g=shape.world()?.6f:.5f,b=shape.world()?.95f:.2f;
            switch(shape.shape()) {
                case BOX -> LevelRenderer.renderLineBox(poses,lines,new AABB(-size.x,-size.y,-size.z,size.x,size.y,size.z),r,g,b,1);
                case SPHERE -> capsule(poses,lines,new Vector3f(),new Vector3f(),size.x,r,g,b);
                case CAPSULE -> capsule(poses,lines,new Vector3f(0,-size.y/2,0),new Vector3f(0,size.y/2,0),size.x,r,g,b);
            }
            poses.popPose();
        }
        // The entity box remains Minecraft's authoritative shape, distinct from visual physics.
        if(mc.player!=null && config.collision.get()) {
            var box=mc.player.getBoundingBox().move(-anchor.x,-anchor.y,-anchor.z);
            LevelRenderer.renderLineBox(poses,lines,box,1,.3f,.55f,1);
        }
        poses.popPose();buffers.endBatch(RenderType.lines());
    }
    private static void capsule(PoseStack poses,VertexConsumer lines,Vector3f a,Vector3f b,float radius,float r,float g,float blue) {
        var axis=new Vector3f(b).sub(a);
        if(axis.lengthSquared()<1e-10f)axis.set(0,1,0);else axis.normalize();
        var u=new Vector3f(axis).cross(Math.abs(axis.y)<.9f?new Vector3f(0,1,0):new Vector3f(1,0,0)).normalize();
        var v=new Vector3f(axis).cross(u).normalize();
        for(int i=0;i<16;i++) {
            double t=i*Math.PI/8,n=(i+1)*Math.PI/8;
            var offset=new Vector3f(u).mul((float)Math.cos(t)*radius).fma((float)Math.sin(t)*radius,v);
            var next=new Vector3f(u).mul((float)Math.cos(n)*radius).fma((float)Math.sin(n)*radius,v);
            line(poses,lines,new Vector3f(a).add(offset),new Vector3f(a).add(next),r,g,blue);
            line(poses,lines,new Vector3f(b).add(offset),new Vector3f(b).add(next),r,g,blue);
            if(i%4==0)line(poses,lines,new Vector3f(a).add(offset),new Vector3f(b).add(offset),r,g,blue);
            for(var tangent:new Vector3f[]{u,v}) {
                var first=new Vector3f(tangent).mul((float)Math.cos(t)*radius).fma((float)Math.sin(t)*radius,axis);
                var second=new Vector3f(tangent).mul((float)Math.cos(n)*radius).fma((float)Math.sin(n)*radius,axis);
                var center=Math.sin(t)>=0?b:a;
                line(poses,lines,new Vector3f(center).add(first),new Vector3f(center).add(second),r,g,blue);
            }
        }
    }
    private static void axes(PoseStack poses,VertexConsumer lines,Vector3f p,float size) {
        line(poses,lines,p,new Vector3f(p).add(size,0,0),1,.3f,.3f);
        line(poses,lines,p,new Vector3f(p).add(0,size,0),.3f,1,.4f);
        line(poses,lines,p,new Vector3f(p).add(0,0,size),.3f,.6f,1);
    }
    private static void cross(PoseStack poses,VertexConsumer lines,Vector3f p,float size,float r,float g,float b) {
        line(poses,lines,new Vector3f(p).add(-size,0,0),new Vector3f(p).add(size,0,0),r,g,b);
        line(poses,lines,new Vector3f(p).add(0,-size,0),new Vector3f(p).add(0,size,0),r,g,b);
    }
    private static void line(PoseStack poses,VertexConsumer consumer,Vector3f a,Vector3f b,float r,float g,float blue) {
        var normal=new Vector3f(b).sub(a);if(normal.lengthSquared()<1e-12f)return;normal.normalize();
        consumer.vertex(poses.last().pose(),a.x,a.y,a.z).color(r,g,blue,1).normal(poses.last().normal(),normal.x,normal.y,normal.z).endVertex();
        consumer.vertex(poses.last().pose(),b.x,b.y,b.z).color(r,g,blue,1).normal(poses.last().normal(),normal.x,normal.y,normal.z).endVertex();
    }
}
