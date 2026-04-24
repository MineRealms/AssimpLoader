package cn.minerealms.assimploader.lookdev;

import cn.minerealms.assimploader.material.PBRMaterial;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Interface for providing materials to the LookDev viewer.
 *
 * Implementations can provide materials from:
 * - Current model
 * - Material library
 * - Custom materials
 */
public interface IMaterialProvider {

    /**
     * Get all materials from the current model
     *
     * @return List of materials, empty if no model loaded
     */
    List<PBRMaterial> getMaterials();

    /**
     * Get the currently selected material
     *
     * @return The selected material, or null if none selected
     */
    @Nullable
    PBRMaterial getSelectedMaterial();

    /**
     * Select a material by index
     *
     * @param index Material index
     * @return true if selection successful
     */
    boolean selectMaterial(int index);

    /**
     * Get the index of the currently selected material
     *
     * @return Material index, or -1 if none selected
     */
    int getSelectedMaterialIndex();

    /**
     * Check if any material is selected
     *
     * @return true if a material is selected
     */
    default boolean hasMaterial() {
        return getSelectedMaterial() != null;
    }

    /**
     * Get the name of the selected material
     *
     * @return Material name, or "No Material" if none selected
     */
    default String getMaterialName() {
        PBRMaterial material = getSelectedMaterial();
        return material != null ? material.getName() : "No Material";
    }
}
