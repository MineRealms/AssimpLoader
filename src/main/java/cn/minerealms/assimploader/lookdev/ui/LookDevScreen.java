package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.comparison.ComparisonSnapshot;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.lookdev.IModelProvider;
import cn.minerealms.assimploader.lookdev.IMaterialProvider;
import cn.minerealms.assimploader.material.PBRMaterial;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
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

    // Comparison state
    private boolean comparisonMode = false;
    private ComparisonSnapshot currentSnapshot = null;

    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            DebugManager.info(DebugCategory.LOOKDEV, "LookDevScreen created");

            // Initialize child fragments
            modelTreeFragment = new ModelTreeFragment();
            previewFragment = new PreviewFragment();
            parameterPanelFragment = new ParameterPanelFragment();

            DebugManager.debug(DebugCategory.LOOKDEV, "Child fragments initialized: ModelTree, Preview, ParameterPanel");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to create LookDevScreen", e);
            throw e;
        }
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

        try {
            DebugManager.info(DebugCategory.LOOKDEV, "LookDevScreen view created");

            // Setup keyboard shortcuts
            setupKeyboardShortcuts(view);
            DebugManager.debug(DebugCategory.LOOKDEV, "Keyboard shortcuts configured");

            // Notify child fragments
            modelTreeFragment.onViewCreated(view, savedInstanceState);
            previewFragment.onViewCreated(view, savedInstanceState);
            parameterPanelFragment.onViewCreated(view, savedInstanceState);
            DebugManager.debug(DebugCategory.LOOKDEV, "Child fragments view created");

            // Load test data
            loadTestData();
            DebugManager.debug(DebugCategory.LOOKDEV, "Test data loaded");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to create view", e);
            throw e;
        }
    }

    /**
     * Setup keyboard shortcuts
     * R: Reset camera
     * Space: Pause rotation (TODO)
     * 1-7: Toggle channels
     */
    private void setupKeyboardShortcuts(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus();

        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            switch (keyCode) {
                case KeyEvent.KEY_R:
                    // Reset camera
                    if (previewFragment != null) {
                        previewFragment.resetCamera();
                    }
                    return true;

                case KeyEvent.KEY_ESCAPE:
                    // Exit comparison mode
                    if (comparisonMode) {
                        exitComparisonMode();
                        return true;
                    }
                    return false;

                case KeyEvent.KEY_SPACE:
                    // TODO: Pause/resume rotation
                    return true;

                case KeyEvent.KEY_1:
                    toggleChannel(0); // BaseColor
                    return true;

                case KeyEvent.KEY_2:
                    toggleChannel(1); // Normal
                    return true;

                case KeyEvent.KEY_3:
                    toggleChannel(2); // Metallic
                    return true;

                case KeyEvent.KEY_4:
                    toggleChannel(3); // Roughness
                    return true;

                case KeyEvent.KEY_5:
                    toggleChannel(4); // Emissive
                    return true;

                case KeyEvent.KEY_6:
                    toggleChannel(5); // AO
                    return true;

                case KeyEvent.KEY_7:
                    toggleChannel(6); // Height
                    return true;
            }

            return false;
        });
    }

    /**
     * Toggle a material channel by index
     */
    private void toggleChannel(int channelIndex) {
        if (parameterPanelFragment != null) {
            parameterPanelFragment.toggleChannel(channelIndex);
        }
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
        DebugManager.info(DebugCategory.LOOKDEV, "Loading model: %s", modelPath);
        return false;
    }

    /**
     * Extract PBR materials from AssimpScene
     */
    private void extractMaterials(AssimpScene scene) {
        try {
            materials.clear();

            if (scene == null) {
                DebugManager.warn(DebugCategory.LOOKDEV, "Cannot extract materials: scene is null");
                return;
            }

            // Get Assimp materials
            var assimpMaterials = scene.getMaterials();
            DebugManager.debug(DebugCategory.LOOKDEV, "Found %d Assimp materials in scene", assimpMaterials.size());

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
                DebugManager.debug(DebugCategory.LOOKDEV, "Converted material %d: %s", i, pbrMat.getName());
            }

            DebugManager.info(DebugCategory.LOOKDEV, "Extracted %d materials from model", materials.size());
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to extract materials", e);
        }
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
        try {
            PBRMaterial material = getSelectedMaterial();
            if (material == null) {
                DebugManager.warn(DebugCategory.LOOKDEV, "Cannot select material: material is null");
                return;
            }

            DebugManager.info(DebugCategory.LOOKDEV, "Material selected: %s (index: %d)", material.getName(), selectedMaterialIndex);

            // Update preview with new material
            if (previewFragment != null) {
                previewFragment.setMaterial(material);
                DebugManager.debug(DebugCategory.LOOKDEV, "Preview fragment updated with material");
            }

            // Update parameters with selected material
            if (parameterPanelFragment != null) {
                parameterPanelFragment.setMaterial(material);
                DebugManager.debug(DebugCategory.LOOKDEV, "Parameter panel updated with material");
            }
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to handle material selection", e);
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
     * Show comparison with snapshot
     */
    public void showComparison(ComparisonSnapshot snapshot) {
        try {
            this.currentSnapshot = snapshot;
            this.comparisonMode = true;

            DebugManager.info(DebugCategory.LOOKDEV, "Entering comparison mode with snapshot: %s", snapshot.getLabel());

            // TODO: Update preview to show split-screen comparison
            // Left side: snapshot state
            // Right side: current state
            if (previewFragment != null) {
                previewFragment.setComparisonMode(true, snapshot);
                DebugManager.debug(DebugCategory.LOOKDEV, "Preview fragment set to comparison mode");
            }
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to show comparison", e);
        }
    }

    /**
     * Exit comparison mode
     */
    public void exitComparisonMode() {
        try {
            this.comparisonMode = false;
            this.currentSnapshot = null;

            DebugManager.info(DebugCategory.LOOKDEV, "Exiting comparison mode");

            if (previewFragment != null) {
                previewFragment.setComparisonMode(false, null);
                DebugManager.debug(DebugCategory.LOOKDEV, "Preview fragment exited comparison mode");
            }
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to exit comparison mode", e);
        }
    }

    /**
     * Check if in comparison mode
     */
    public boolean isComparisonMode() {
        return comparisonMode;
    }

    /**
     * Called when model is loaded
     */
    private void onModelLoaded() {
        try {
            DebugManager.info(DebugCategory.LOOKDEV, "Model loaded");

            // Extract materials from model
            if (currentModel != null) {
                extractMaterials(currentModel);

                // Auto-select first material if available
                if (!materials.isEmpty()) {
                    selectMaterial(0);
                    DebugManager.debug(DebugCategory.LOOKDEV, "Auto-selected first material");
                }
            }

            // Update preview with new model
            if (previewFragment != null) {
                previewFragment.setModel(currentModel);
                DebugManager.debug(DebugCategory.LOOKDEV, "Preview fragment updated with model");
            }

            // Update tree view
            if (modelTreeFragment != null) {
                modelTreeFragment.updateModel();
                DebugManager.debug(DebugCategory.LOOKDEV, "Model tree updated");
            }
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to handle model loaded", e);
        }
    }
}
