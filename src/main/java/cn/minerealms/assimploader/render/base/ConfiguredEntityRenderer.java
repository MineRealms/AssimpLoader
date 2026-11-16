package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.render.AttachmentTrailRenderer;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.ModelTextureCache;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Reusable renderer for a mod's own registered entities, backed by the same JSON instance resources. */
public abstract class ConfiguredEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    private final BaseAssimpRenderer renderer=new BaseAssimpRenderer() {
        @Override protected String getModelName() { return "configured_entity"; }
    };
    protected ConfiguredEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    protected abstract String modelName(T entity);
    protected org.joml.Matrix4f modelTransform(T entity,float yaw,float partialTick) {
        return new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw));
    }

    @Override public void render(T entity,float yaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light) {
        ClientRenderResources.checkGeneration();
        String name=modelName(entity);
        var scene=AssimpModelLoader.getCachedModel(name);
        if (scene==null) return;
        var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(entity,name,scene);
        stack.pushPose();
        try {
            var transform=modelTransform(entity,yaw,partialTick);
            cn.minerealms.assimploader.render.SceneTransforms.apply(stack,transform);
            if(state!=null && state.getModelInstance()!=null) cn.minerealms.assimploader.client.MinecraftAttachmentParticles.bind(
                    state.getModelInstance(),entity.level(),entity.position(),transform);
            renderer.renderScene(scene,state,stack,buffers,light,OverlayTexture.NO_OVERLAY);
            if(state!=null && state.getModelInstance()!=null) AttachmentTrailRenderer.render(state.getModelInstance(),stack,buffers,light);
        } finally { stack.popPose(); }
        super.render(entity,yaw,partialTick,stack,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(T entity) { return ModelTextureCache.WHITE; }
}
