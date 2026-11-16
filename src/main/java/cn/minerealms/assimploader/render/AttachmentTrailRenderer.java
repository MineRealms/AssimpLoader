package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.animation.AttachmentTrail;
import cn.minerealms.assimploader.animation.ModelInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Draw after the model using the same model-space pose stack. History advances only at tick. */
public final class AttachmentTrailRenderer {
    private AttachmentTrailRenderer() {}
    public static void render(ModelInstance instance, PoseStack stack, MultiBufferSource buffers, int light) {
        for (AttachmentTrail trail : instance.trails().values()) {
            var samples = trail.samples();
            if (samples.size() < 2) continue;
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(ModelTextureCache.WHITE));
            float[] color = trail.color();
            for (int i=1;i<samples.size();i++) {
                var previous = samples.get(i-1); var current = samples.get(i);
                float a = (float)Math.max(0,1-(instance.animation().getSimulationTime()-previous.time())/trail.lifetime());
                float b = (float)Math.max(0,1-(instance.animation().getSimulationTime()-current.time())/trail.lifetime());
                vertex(consumer,stack,previous.start(),0,0,color,a,light);
                vertex(consumer,stack,previous.end(),1,0,color,a,light);
                vertex(consumer,stack,current.end(),1,1,color,b,light);
                vertex(consumer,stack,current.start(),0,1,color,b,light);
            }
        }
    }
    private static void vertex(VertexConsumer out,PoseStack stack,Vector3f point,float u,float v,float[] color,float fade,int light) {
        out.vertex(stack.last().pose(),point.x,point.y,point.z).color(color[0],color[1],color[2],color[3]*fade)
                .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0,1,0).endVertex();
    }
}
