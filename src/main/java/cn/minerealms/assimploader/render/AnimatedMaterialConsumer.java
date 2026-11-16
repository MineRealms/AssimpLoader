package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.animation.MaterialAnimation;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Applies instance parameters while preserving the active vanilla/Oculus vertex contract. */
public final class AnimatedMaterialConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final MaterialAnimation.Sample sample;
    public AnimatedMaterialConsumer(VertexConsumer delegate, MaterialAnimation.Sample sample) { this.delegate = delegate; this.sample = sample; }
    @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x,y,z); return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) {
        delegate.color(channel(r,sample.red()),channel(g,sample.green()),channel(b,sample.blue()),channel(a,sample.alpha())); return this;
    }
    private static int channel(int value,float factor) { return Math.max(0,Math.min(255,Math.round(value*factor))); }
    @Override public VertexConsumer uv(float u,float v) { delegate.uv(u+sample.u(),v+sample.v()); return this; }
    @Override public VertexConsumer overlayCoords(int u,int v) { delegate.overlayCoords(u,v); return this; }
    @Override public VertexConsumer uv2(int u,int v) { delegate.uv2(u,v); return this; }
    @Override public VertexConsumer normal(float x,float y,float z) { delegate.normal(x,y,z); return this; }
    @Override public void endVertex() { delegate.endVertex(); }
    @Override public void defaultColor(int r,int g,int b,int a) {
        delegate.defaultColor(channel(r,sample.red()),channel(g,sample.green()),channel(b,sample.blue()),channel(a,sample.alpha()));
    }
    @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
}
