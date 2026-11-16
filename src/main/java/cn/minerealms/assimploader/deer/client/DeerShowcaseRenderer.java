package cn.minerealms.assimploader.deer.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.deer.DeerShowcaseEntity;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.base.ConfiguredEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class DeerShowcaseRenderer extends ConfiguredEntityRenderer<DeerShowcaseEntity> {
    public static final ResourceLocation MODEL_RESOURCE =
            new ResourceLocation(AssimpLoader.MODID, "models/validation/deer_showcase.glb");
    public static final String MODEL_ID = AssimpModelLoader.canonicalIdentity(MODEL_RESOURCE);

    public DeerShowcaseRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = .7f;
    }

    @Override protected String modelName(DeerShowcaseEntity entity) { return MODEL_ID; }
    @Override protected boolean groundPlacementEnabled(DeerShowcaseEntity entity) { return entity.groundIkEnabled(); }
    @Override protected boolean worldEffectsEnabled(DeerShowcaseEntity entity) { return entity.effectsEnabled(); }

    @Override protected Matrix4f modelTransform(DeerShowcaseEntity entity, float yaw, float partialTick) {
        return new Matrix4f().rotateY((float) Math.toRadians(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
    }

    @Override public void render(DeerShowcaseEntity entity, float yaw, float partialTick,
            PoseStack stack, MultiBufferSource buffers, int light) {
        ClientRenderResources.checkGeneration();
        var scene = AssimpModelLoader.getCachedModel(MODEL_ID, ModelImportMode.DYNAMIC);
        if (scene != null) {
            var state = ClientAnimationManager.getInstance().getOrCreateAnimationState(entity, MODEL_ID, scene);
            if (state != null && state.getModelInstance() != null) {
                String requested = entity.getShowcaseState();
                if (requested.equals("auto")) {
                    double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
                    requested = dx * dx + dz * dz > 1e-5 ? "walk" : "idle";
                }
                if (!requested.equals(state.getStateMachine().getCurrentStateName())) {
                    // Client resources define valid states; unknown overrides safely return to idle.
                    if (!state.getModelInstance().requestState(requested)) state.getModelInstance().requestState("idle");
                }
            }
        }
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }
}
