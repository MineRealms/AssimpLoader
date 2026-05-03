package cn.minerealms.assimploader.render.base;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Renderer for static (non-animated) Assimp models.
 *
 * This is the simplest renderer - just provide a model name and you're done.
 * Perfect for static decorative blocks, machines, furniture, etc.
 *
 * Example usage:
 * <pre>
 * public class CCBBlockEntityRenderer extends StaticBlockEntityRenderer<CCBBlockEntity> {
 *     {@literal @}Override
 *     protected String getModelName() {
 *         return "ccb";
 *     }
 * }
 * </pre>
 *
 * @param <T> BlockEntity type
 */
public abstract class StaticBlockEntityRenderer<T extends BlockEntity> extends BaseBlockEntityRenderer<T> {
    // No additional logic needed - all functionality is in BaseBlockEntityRenderer
    // Subclasses only need to provide getModelName()
}
