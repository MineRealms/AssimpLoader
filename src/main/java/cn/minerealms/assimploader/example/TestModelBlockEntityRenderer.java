package cn.minerealms.assimploader.example;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Custom renderer for TestModelBlock using BlockEntityRenderer.
 */
public class TestModelBlockEntityRenderer extends AssimpBlockEntityRenderer<TestModelBlockEntity> {

    public TestModelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected String getModelName() {
        return "cnc";
    }
}
