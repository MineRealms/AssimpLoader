package cn.minerealms.assimploader.lookdev;

import cn.minerealms.assimploader.model.AssimpScene;

import javax.annotation.Nullable;

/**
 * Interface for providing models to the LookDev viewer.
 *
 * Implementations can provide models from:
 * - Loaded cache
 * - File system
 * - Resource packs
 * - Runtime generation
 */
public interface IModelProvider {

    /**
     * Get the current model being viewed
     *
     * @return The current model, or null if none loaded
     */
    @Nullable
    AssimpScene getCurrentModel();

    /**
     * Load a model from a resource location
     *
     * @param modelPath Path to the model file (e.g., "models/test.fbx")
     * @return true if loaded successfully
     */
    boolean loadModel(String modelPath);

    /**
     * Check if a model is currently loaded
     *
     * @return true if a model is loaded
     */
    default boolean hasModel() {
        return getCurrentModel() != null;
    }

    /**
     * Get the name of the current model
     *
     * @return Model name, or "No Model" if none loaded
     */
    default String getModelName() {
        AssimpScene model = getCurrentModel();
        return model != null ? "Model" : "No Model";
    }
}
