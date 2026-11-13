package cn.minerealms.assimploader.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/** Default item fit uses transformed scene units rather than raw FBX centimeter vertices. */
public final class ItemModelTransforms {
    private ItemModelTransforms() {}

    public static float scale(ItemDisplayContext context, AABB bounds) {
        double extent = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        float target = switch (context) {
            case GUI -> 0.8f;
            case GROUND -> 0.3f;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.45f;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.55f;
            case HEAD -> 0.8f;
            default -> 0.65f;
        };
        return extent > 1.0e-6 && Double.isFinite(extent) ? (float) (target / extent) : target;
    }

    public static void applyDefault(PoseStack poses, ItemDisplayContext context, AABB bounds) {
        poses.translate(0.5, context == ItemDisplayContext.GROUND ? 0.25 : 0.5, 0.5);
        float scale = scale(context, bounds);
        poses.scale(scale, scale, scale);
        poses.translate(-(bounds.minX + bounds.maxX) * 0.5, -(bounds.minY + bounds.maxY) * 0.5,
                -(bounds.minZ + bounds.maxZ) * 0.5);
    }
}
