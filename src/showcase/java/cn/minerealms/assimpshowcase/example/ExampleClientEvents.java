package cn.minerealms.assimpshowcase.example;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.render.AssimpItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * Shared client renderer for Assimp model items.
 */
public class ExampleClientEvents {

    private static AssimpItemRenderer itemRenderer;

    public static BlockEntityWithoutLevelRenderer getItemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new AssimpItemRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher());
            DebugManager.info(DebugCategory.CORE, "Lazy-initialized AssimpItemRenderer for FBX items");
        }
        return itemRenderer;
    }
}

