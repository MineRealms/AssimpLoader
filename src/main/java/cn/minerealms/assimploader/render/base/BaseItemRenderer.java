package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.SimpleMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for Item renderers using Assimp models.
 *
 * Features:
 * - Supports both static and animated models
 * - DisplayContext-aware transformations
 * - Hot-reload support
 * - Texture caching
 * - Skeletal animation support
 *
 * Subclasses need to:
 * - Provide model name mapping via getModelName()
 * - Optionally override applyItemTransformations()
 *
 * Example usage:
 * <pre>
 * public class MyItemRenderer extends BaseItemRenderer {
 *     {@literal @}Override
 *     protected String getModelName(ItemStack stack) {
 *         String itemName = ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
 *         return itemName.replace("_item", "");
 *     }
 * }
 * </pre>
 */
public abstract class BaseItemRenderer extends BaseAssimpRenderer {

    private final Map<String, Boolean> loggedItems = new HashMap<>();
    private final BlockEntityWithoutLevelRenderer delegate;

    public BaseItemRenderer(BlockEntityRenderDispatcher dispatcher) {
        this.delegate = new BlockEntityWithoutLevelRenderer(dispatcher, Minecraft.getInstance().getEntityModels());
    }

    /**
     * Get the model name for this item stack.
     * Override this to provide custom model name mapping.
     */
    protected abstract String getModelName(ItemStack stack);

    /**
     * Render the item.
     */
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext,
                             @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                             int combinedLight, int combinedOverlay) {

        String itemName = ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        String modelName = getModelName(stack);

        // Only log once per item type
        if (!loggedItems.containsKey(itemName)) {
            DebugManager.info(DebugCategory.RENDER, "BaseItemRenderer: Rendering item '{}' (model: {})",
                itemName, modelName);
            loggedItems.put(itemName, true);
        }

        // Try to load the model
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);

        if (scene == null) {
            if (!loggedItems.containsKey(itemName + "_error")) {
                DebugManager.warn(DebugCategory.RENDER, "Model not found for item '{}': {}", itemName, modelName);
                loggedItems.put(itemName + "_error", true);
            }
            return; // Model not found
        }

        // Check hot-reload
        checkHotReload(scene, modelName);

        // Load embedded textures
        loadEmbeddedTextures(scene, modelName);

        // Log import mode once per model
        if (!modeLoggedMap.containsKey(modelName)) {
            DebugManager.info(DebugCategory.RENDER, "Item model '{}' import mode: {} (isDynamic: {})",
                modelName, scene.getImportMode(), scene.isDynamic());
            modeLoggedMap.put(modelName, true);
        }

        poseStack.pushPose();

        // Apply display context transformations
        applyItemTransformations(poseStack, displayContext);

        // Calculate light level
        float lightLevel = (combinedLight & 0xFFFF) / 240.0f;

        // Render each mesh (items use static rendering only for now)
        for (AssimpMesh mesh : scene.getMeshes()) {
            ResourceLocation texture = getTextureLocation(scene, mesh, modelName);

            // Use static method for SimpleMeshRenderer
            SimpleMeshRenderer.render(mesh, poseStack, buffer, texture, combinedLight, combinedOverlay);
        }

        poseStack.popPose();
    }

    /**
     * Apply transformations based on display context.
     * Override this to customize item positioning/scaling.
     */
    protected void applyItemTransformations(PoseStack poseStack, ItemDisplayContext displayContext) {
        switch (displayContext) {
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;

            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.4f, 0.4f, 0.4f);
                break;

            case GUI:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.6f, 0.6f, 0.6f);
                break;

            case GROUND:
                poseStack.translate(0.5, 0.25, 0.5);
                poseStack.scale(0.3f, 0.3f, 0.3f);
                break;

            case FIXED:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;

            case HEAD:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(1.0f, 1.0f, 1.0f);
                break;

            default:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;
        }
    }
}
