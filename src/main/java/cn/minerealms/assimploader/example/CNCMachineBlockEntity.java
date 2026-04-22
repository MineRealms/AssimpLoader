package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for CNC Machine block.
 * Stores the loaded Assimp scene for rendering.
 */
public class CNCMachineBlockEntity extends BlockEntity {

    private AssimpScene scene;
    private boolean sceneLoaded = false;
    private static final ResourceLocation MODEL_LOCATION =
        new ResourceLocation("assimploader", "models/block/cnc.fbx");

    public CNCMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.CNC_MACHINE_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Load the FBX model asynchronously on first access
     */
    public void loadModel() {
        if (sceneLoaded || scene != null) {
            return;
        }

        try {
            // Load model from resources
            String path = "/assets/" + MODEL_LOCATION.getNamespace() + "/" + MODEL_LOCATION.getPath();
            var inputStream = getClass().getResourceAsStream(path);

            if (inputStream == null) {
                AssimpLoader.LOGGER.error("Failed to find model: {}", path);
                sceneLoaded = true; // Mark as attempted to avoid repeated failures
                return;
            }

            byte[] data = inputStream.readAllBytes();
            inputStream.close();

            // Load with AssimpModelLoader
            scene = AssimpLoader.getInstance().getModelLoader().loadModelFromMemory(path, data);
            sceneLoaded = true;

            if (scene != null) {
                AssimpLoader.LOGGER.info("CNC Machine model loaded: {} meshes, {} materials",
                    scene.getMeshes().size(), scene.getMaterials().size());
            }
        } catch (Exception e) {
            AssimpLoader.LOGGER.error("Failed to load CNC Machine model", e);
            sceneLoaded = true;
        }
    }

    public AssimpScene getScene() {
        if (!sceneLoaded) {
            loadModel();
        }
        return scene;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    }
}
