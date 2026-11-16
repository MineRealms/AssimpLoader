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
import net.minecraft.client.renderer.culling.Frustum;
import cn.minerealms.assimploader.render.ModelWorldBounds;
import cn.minerealms.assimploader.render.SceneTransforms;
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
    protected boolean groundPlacementEnabled(T entity) { return true; }
    protected boolean worldEffectsEnabled(T entity) { return true; }
    protected org.joml.Matrix4f modelTransform(T entity,float yaw,float partialTick) {
        return new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw));
    }

    @Override public boolean shouldRender(T entity,Frustum frustum,double cameraX,double cameraY,double cameraZ) {
        if (super.shouldRender(entity,frustum,cameraX,cameraY,cameraZ)) return true;
        var scene=AssimpModelLoader.getCachedModel(modelName(entity));
        if (scene==null) return false;
        var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(entity,modelName(entity),scene);
        var pose=state==null?null:state.getPoseSnapshot();
        var local=pose==null?SceneTransforms.bounds(scene):pose.getBounds();
        var world=ModelWorldBounds.transform(local,modelTransform(entity,entity.getYRot(),1),entity.getX(),entity.getY(),entity.getZ());
        var previous=ModelWorldBounds.transform(local,modelTransform(entity,entity.yRotO,0),entity.xo,entity.yo,entity.zo);
        world=world.minmax(previous).inflate(.05);
        // Rotation between tick endpoints can extend beyond the two endpoint boxes.
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(entity.getYRot()-entity.yRotO))>.01f) {
            double dx=Math.max(Math.abs(world.minX-entity.getX()),Math.abs(world.maxX-entity.getX()));
            double dz=Math.max(Math.abs(world.minZ-entity.getZ()),Math.abs(world.maxZ-entity.getZ()));
            double radius=Math.hypot(dx,dz);
            world=new net.minecraft.world.phys.AABB(Math.min(entity.xo,entity.getX())-radius,world.minY,
                    Math.min(entity.zo,entity.getZ())-radius,Math.max(entity.xo,entity.getX())+radius,
                    world.maxY,Math.max(entity.zo,entity.getZ())+radius);
        }
        double distance=Math.max(64,world.getSize()*64)*entity.getViewScale();
        return world.getCenter().distanceToSqr(cameraX,cameraY,cameraZ)<distance*distance
                && (entity.noCulling || frustum.isVisible(world));
    }

    @Override public void render(T entity,float yaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light) {
        ClientRenderResources.checkGeneration();
        String name=modelName(entity);
        var scene=AssimpModelLoader.getCachedModel(name);
        if (scene==null) return;
        var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(entity,name,scene);
        if(state!=null && state.getModelInstance()!=null) cn.minerealms.assimploader.client.MinecraftInstanceWorldBinding.bind(
                state.getModelInstance(),entity,current->modelTransform(current,current.getYRot(),1),this::groundPlacementEnabled,this::worldEffectsEnabled);
        ClientAnimationManager.getInstance().markRendered(state,
                entity.position().distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
        stack.pushPose();
        try {
            var transform=modelTransform(entity,yaw,partialTick);
            cn.minerealms.assimploader.render.SceneTransforms.apply(stack,transform);
            renderer.renderScene(scene,state,stack,buffers,light,OverlayTexture.NO_OVERLAY);
            if(state!=null && state.getModelInstance()!=null) AttachmentTrailRenderer.render(state.getModelInstance(),stack,buffers,light,
                    new org.joml.Matrix4f().translation(
                            (float)net.minecraft.util.Mth.lerp(partialTick,entity.xo,entity.getX()),
                            (float)net.minecraft.util.Mth.lerp(partialTick,entity.yo,entity.getY()),
                            (float)net.minecraft.util.Mth.lerp(partialTick,entity.zo,entity.getZ())).mul(transform));
        } finally { stack.popPose(); }
        super.render(entity,yaw,partialTick,stack,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(T entity) { return ModelTextureCache.WHITE; }
}
