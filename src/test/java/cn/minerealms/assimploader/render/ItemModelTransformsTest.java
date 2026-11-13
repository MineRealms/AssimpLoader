package cn.minerealms.assimploader.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemModelTransformsTest {
    @Test
    void centimeterSizedModelsFitWithinAnInventorySlotAndRemainCentered() {
        AABB bounds = new AABB(-80, 0, -140, 80, 170, 140);
        float scale = ItemModelTransforms.scale(ItemDisplayContext.GUI, bounds);
        assertEquals(0.8f, bounds.getZsize() * scale, 1.0e-6);
        PoseStack stack = new PoseStack();
        ItemModelTransforms.applyDefault(stack, ItemDisplayContext.GUI, bounds);
        Vector3f center = stack.last().pose().transformPosition(new Vector3f(0, 85, 0));
        assertEquals(0.5, center.x, 1.0e-6);
        assertEquals(0.5, center.y, 1.0e-6);
        assertEquals(0.5, center.z, 1.0e-6);
        assertTrue(ItemModelTransforms.scale(ItemDisplayContext.GROUND, bounds) < scale);
    }
}
