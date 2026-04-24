package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.lookdev.IModelProvider;
import cn.minerealms.assimploader.lookdev.IMaterialProvider;
import cn.minerealms.assimploader.material.PBRMaterial;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Main LookDev viewer screen.
 *
 * Three-panel layout:
 * - Left: Model tree (20% width)
 * - Center: 3D preview (60% width)
 * - Right: Parameters (20% width)
 *
 * Provides model and material management for child fragments.
 */
public class LookDevScreen extends Fragment implements IModelProvider, IMaterialProvider {

    // Child fragments
    private ModelTreeFragment modelTreeFragment;
    private PreviewFragment previewFragment;
    private ParameterPanelFragment parameterPanelFragment;

    // Current state
    private AssimpScene currentModel;
    private List<PBRMaterial> materials = new ArrayList<>();
    private int selectedMaterialIndex = -1;

    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);

        AssimpLoader.LOGGER.info("LookDevScreen created");

        // Initialize child fragments
        modelTreeFragment = new ModelTreeFragment();
        previewFragment = new PreviewFragment();
        parameterPanelFragment = new ParameterPanelFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        // Create root horizontal layout
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Left panel: Model tree (20% width)
        View leftPanel = modelTreeFragment.onCreateView(inflater, root, savedInstanceState);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        leftParams.weight = 0.2f;
        leftPanel.setLayoutParams(leftParams);
        root.addView(leftPanel);

        // Center panel: Preview (60% width)
        View centerPanel = previewFragment.onCreateView(inflater, root, savedInstanceState);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        centerParams.weight = 0.6f;
        centerPanel.setLayoutParams(centerParams);
        root.addView(centerPanel);

        // Right panel: Parameters (20% width)
        View rightPanel = parameterPanelFragment.onCreateView(inflater, root, savedInstanceState);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        rightParams.weight = 0.2f;
        rightPanel.setLayoutParams(rightParams);
        root.addView(rightPanel);

        return root;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AssimpLoader.LOGGER.info("LookDevScreen view created");

        // Notify child fragments
        modelTreeFragment.onViewCreated(view, savedInstanceState);
        previewFragment.onViewCreated(view, savedInstanceState);
        parameterPanelFragment.onViewCreated(view, savedInstanceState);

        // Load test data
        loadTestData();
    }

    /**
     * Load test data for development
     */
    private void loadTestData() {
        // Create test materials
        PBRMaterial mat1 = new PBRMaterial("Test Material 1");
        mat1.setRoughnessFactor(0.3f);
        mat1.setMetallicFactor(0.8f);

        PBRMaterial mat2 = new PBRMaterial("Test Material 2");
        mat2.setRoughnessFactor(0.7f);
        mat2.setMetallicFactor(0.2f);

        materials.add(mat1);
        materials.add(mat2);

        // Select first material
        if (!materials.isEmpty()) {
            selectMaterial(0);
        }

        // Update tree
        if (modelTreeFragment != null) {
            modelTreeFragment.updateModel();
        }
    }

    // ==================== IModelProvider Implementation ====================

    @Nullable
    @Override
    public AssimpScene getCurrentModel() {
        return currentModel;
    }

    @Override
    public boolean loadModel(String modelPath) {
        // TODO Phase 5: Implement model loading
        AssimpLoader.LOGGER.info("Loading model: {}", modelPath);
        return false;
    }

    /**
     * Extract PBR materials from AssimpScene
     */
    private void extractMaterials(AssimpScene scene) {
        materials.clear();

        if (scene == null) {
            return;
        }

        // Get Assimp materials
        var assimpMaterials = scene.getMaterials();

        // Convert to PBR materials
        for (int i = 0; i < assimpMaterials.size(); i++) {
            var assimpMat = assimpMaterials.get(i);

            // Create PBR material with basic properties
            // TODO: Implement full conversion from AssimpMaterial to PBRMaterial
            PBRMaterial pbrMat = new PBRMaterial("Material_" + i);

            // Set default PBR values
            pbrMat.setRoughnessFactor(0.5f);
            pbrMat.setMetallicFactor(0.0f);

            materials.add(pbrMat);
        }

        AssimpLoader.LOGGER.info("Extracted {} materials from model", materials.size());
    }

    // ==================== IMaterialProvider Implementation ====================

    @Override
    public List<PBRMaterial> getMaterials() {
        return materials;
    }

    @Nullable
    @Override
    public PBRMaterial getSelectedMaterial() {
        if (selectedMaterialIndex >= 0 && selectedMaterialIndex < materials.size()) {
            return materials.get(selectedMaterialIndex);
        }
        return null;
    }

    @Override
    public boolean selectMaterial(int index) {
        if (index >= 0 && index < materials.size()) {
            selectedMaterialIndex = index;
            onMaterialSelected();
            return true;
        }
        return false;
    }

    @Override
    public int getSelectedMaterialIndex() {
        return selectedMaterialIndex;
    }

    /**
     * Called when a material is selected
     */
    private void onMaterialSelected() {
        PBRMaterial material = getSelectedMaterial();
        if (material == null) return;

        AssimpLoader.LOGGER.info("Material selected: {}", material.getName());

        // Update preview with new material
        if (previewFragment != null) {
            previewFragment.setMaterial(material);
        }

        // Update parameters with selected material
        if (parameterPanelFragment != null) {
            parameterPanelFragment.setMaterial(material);
        }
    }

    /**
     * Called when material parameters change
     */
    public void onMaterialParametersChanged() {
        // Refresh preview to show updated material
        if (previewFragment != null) {
            previewFragment.refresh();
        }
    }

    /**
     * Called when model is loaded
     */
    private void onModelLoaded() {
        AssimpLoader.LOGGER.info("Model loaded");

        // Extract materials from model
        if (currentModel != null) {
            extractMaterials(currentModel);

            // Auto-select first material if available
            if (!materials.isEmpty()) {
                selectMaterial(0);
            }
        }

        // Update preview with new model
        if (previewFragment != null) {
            previewFragment.setModel(currentModel);
        }

        // Update tree view
        if (modelTreeFragment != null) {
            modelTreeFragment.updateModel();
        }
    }
}
