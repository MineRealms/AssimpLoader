package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import cn.minerealms.assimploader.rocket.common.RocketEngineState;

public class ShowcaseBlockEntity extends BlockEntity {
    private boolean rocketBurning = true;
    private ResourceLocation exhaustProfile = new ResourceLocation("assimploader", "kerolox");
    private double targetMassFlowKgS = -1;
    private final RocketEngineState engineState = new RocketEngineState();
    public ShowcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ShowcaseRegistry.BLOCK_ENTITY.get(), pos, state);
    }

    public ShowcaseModel getModel() {
        return ((ShowcaseBlock) getBlockState().getBlock()).getModel();
    }

    public boolean isRocketBurning() { return rocketBurning; }
    public ResourceLocation getExhaustProfile() { return exhaustProfile; }
    public RocketEngineState getEngineState() { return engineState; }
    /** Returns -1 for the profile's automatically derived nominal flow. */
    public double getTargetMassFlowKgS() { return targetMassFlowKgS; }

    public void setRocketBurning(boolean burning) { rocketBurning = burning; syncRocket(); }
    public void setExhaustProfile(ResourceLocation profile) {
        if (profile == null) throw new IllegalArgumentException("exhaust profile is required");
        exhaustProfile = profile;
        syncRocket();
    }
    public void setTargetMassFlowKgS(double massFlowKgS) {
        if (!Double.isFinite(massFlowKgS) || massFlowKgS < -1 || massFlowKgS > 1_000_000_000)
            throw new IllegalArgumentException("target mass flow must be -1 (automatic) or in [0, 1e9] kg/s");
        targetMassFlowKgS = massFlowKgS;
        syncRocket();
    }

    private void syncRocket() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("RocketBurning", rocketBurning);
        tag.putString("ExhaustProfile", exhaustProfile.toString());
        tag.putDouble("TargetMassFlowKgS", targetMassFlowKgS);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        rocketBurning = !tag.contains("RocketBurning") || tag.getBoolean("RocketBurning");
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("ExhaustProfile"));
        exhaustProfile = parsed == null ? new ResourceLocation("assimploader", "kerolox") : parsed;
        double savedFlow = tag.contains("TargetMassFlowKgS") ? tag.getDouble("TargetMassFlowKgS") : -1;
        targetMassFlowKgS = Double.isFinite(savedFlow) && savedFlow >= -1 && savedFlow <= 1_000_000_000
                ? savedFlow : -1;
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public AABB getRenderBoundingBox() {
        return ShowcaseVisibility.renderBounds(getModelBounds(), worldPosition,
                AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.frustumCullingEnabled::get, true),
                AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.alwaysRenderLargeModels::get, false),
                AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.largeModelThreshold::get, 16.0));
    }

    public AABB getModelBounds() {
        return ShowcaseVisibility.modelBounds(AssimpModelLoader.getCachedModel(getModel().modelName()));
    }
}
