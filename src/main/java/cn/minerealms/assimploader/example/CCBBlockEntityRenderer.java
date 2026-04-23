package cn.minerealms.assimploader.example;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Custom renderer for CCB block using BlockEntityRenderer.
 */
public class CCBBlockEntityRenderer extends AssimpBlockEntityRenderer<CCBBlockEntity> {

    public CCBBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected String getModelName() {
        return "ccb";
    }
}
