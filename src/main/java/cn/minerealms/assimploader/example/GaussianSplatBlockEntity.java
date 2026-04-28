package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.io.splat.SplatLoader;
import cn.minerealms.assimploader.io.splat.SplatResourceManager;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;

/**
 * BlockEntity for rendering Gaussian Splat models in the world.
 *
 * Similar to CircleBlockEntity but for 3DGS models.
 */
public class GaussianSplatBlockEntity extends BlockEntity {

    private GaussianSplatData splatData;
    private boolean initialized = false;
    private String modelName = "example"; // Default model name

    public GaussianSplatBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.GAUSSIAN_SPLAT_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Initialize splat data (lazy initialization)
     */
    private void initializeSplatData() {
        if (!initialized) {
            try {
                // Load splat data from resources
                ResourceLocation location = new ResourceLocation("assimploader", "splats/" + modelName + ".splat");
                SplatResourceManager resourceManager = new SplatResourceManager(
                    Minecraft.getInstance().getResourceManager()
                );
                splatData = resourceManager.load(location);

                cn.minerealms.assimploader.debug.DebugManager.info(
                    cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                    "GaussianSplatBlockEntity: Loaded {} splats from {}",
                    splatData.getSplatCount(), location
                );

                initialized = true;
            } catch (IOException e) {
                cn.minerealms.assimploader.debug.DebugManager.error(
                    cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                    "GaussianSplatBlockEntity: Failed to load splat data: {}", e.getMessage()
                );
            }
        }
    }

    /**
     * Tick method - called every tick
     */
    public void tick() {
        if (!initialized) {
            initializeSplatData();
        }
    }

    public GaussianSplatData getSplatData() {
        if (!initialized) {
            initializeSplatData();
        }
        return splatData;
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("ModelName")) {
            modelName = tag.getString("ModelName");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("ModelName", modelName);
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
        this.initialized = false;
        this.splatData = null;
    }
}
