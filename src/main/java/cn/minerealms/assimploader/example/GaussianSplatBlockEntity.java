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
    private boolean loadFailed = false;
    private String modelName = "cactus"; // Default model name

    public GaussianSplatBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.GAUSSIAN_SPLAT_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Initialize splat data (lazy initialization)
     */
    private void initializeSplatData() {
        if (!initialized && !loadFailed) {
            // Try multiple extensions
            String[] extensions = {".splat", ".ply"};

            for (String ext : extensions) {
                try {
                    // Load splat data from resources
                    ResourceLocation location = new ResourceLocation("assimploader", "splats/" + modelName + ext);

                    cn.minerealms.assimploader.debug.DebugManager.info(
                        cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                        "GaussianSplatBlockEntity: Attempting to load splat data from {}",
                        location
                    );

                    SplatResourceManager resourceManager = new SplatResourceManager(
                        Minecraft.getInstance().getResourceManager()
                    );
                    splatData = resourceManager.load(location);

                    if (splatData == null) {
                        cn.minerealms.assimploader.debug.DebugManager.warn(
                            cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                            "GaussianSplatBlockEntity: SplatResourceManager returned null for {}",
                            location
                        );
                        continue; // Try next extension
                    }

                    cn.minerealms.assimploader.debug.DebugManager.info(
                        cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                        "GaussianSplatBlockEntity: Loaded {} splats from {}",
                        splatData.getSplatCount(), location
                    );

                    if (splatData.getSplatCount() == 0) {
                        cn.minerealms.assimploader.debug.DebugManager.warn(
                            cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                            "GaussianSplatBlockEntity: Loaded 0 splats - trying next format"
                        );
                        splatData = null;
                        continue; // Try next extension
                    }

                    initialized = true;
                    return; // Success!

                } catch (IOException e) {
                    cn.minerealms.assimploader.debug.DebugManager.warn(
                        cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                        "GaussianSplatBlockEntity: Failed to load {} with extension {}: {}",
                        modelName, ext, e.getMessage()
                    );
                    // Try next extension
                } catch (Exception e) {
                    cn.minerealms.assimploader.debug.DebugManager.error(
                        cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                        "GaussianSplatBlockEntity: Unexpected error loading splat data: {}",
                        e.getMessage()
                    );
                    e.printStackTrace();
                    // Try next extension
                }
            }

            // All formats failed
            cn.minerealms.assimploader.debug.DebugManager.error(
                cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                "GaussianSplatBlockEntity: Failed to load {} with any supported format. Block will render as fallback.",
                modelName
            );
            loadFailed = true;
        }
    }

    /**
     * Tick method - called every tick
     */
    public void tick() {
        if (!initialized && !loadFailed) {
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
        this.loadFailed = false;
        this.splatData = null;
    }
}
