package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Items use the same scene traversal, texture cache and GPU buffers as world displays. */
public class AssimpItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final BaseAssimpRenderer renderer = new BaseAssimpRenderer() {
        @Override protected String getModelName() { return "item"; }
    };

    public AssimpItemRenderer(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher, Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light, int overlay) {
        ClientRenderResources.checkGeneration();
        var baked = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
        AssimpBakedModel model = baked instanceof AssimpBakedModel assimp ? assimp : null;
        String key = stack.getItem() instanceof AssimpModelItem item && item.getModelName() != null
                ? item.getModelName() : ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        AssimpScene scene = AssimpModelLoader.getCachedModel(key);
        if (scene == null && model != null) scene = model.getScene();
        if (scene == null) return;
        AnimationState animation = ClientAnimationManager.getInstance().getOrCreateAnimationState(stack, key, scene);
        ClientAnimationManager.getInstance().markRendered(animation, 0);
        poses.pushPose();
        try {
            // ItemRenderer applies JSON display transforms before entering BEWLR.
            if (model == null || !model.hasDisplayTransform(context)) {
                ItemModelTransforms.applyDefault(poses, context, SceneTransforms.bounds(scene));
            } else {
                poses.translate(0.5, 0.5, 0.5);
            }
            renderer.renderScene(scene, animation, poses, buffers, light, overlay);
            if (animation != null && animation.getModelInstance() != null) {
                AttachmentTrailRenderer.render(animation.getModelInstance(), poses, buffers, light);
            }
        } finally {
            poses.popPose();
        }
    }
}
