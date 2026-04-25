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

import icyllis.modernui.annotation.NonNull;
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
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "=== LookDevScreen.onCreateView START ===");

        try {
            // Create root horizontal layout
            DebugManager.debug(DebugCategory.LOOKDEV, "Creating root LinearLayout...");
            LinearLayout root = new LinearLayout(requireContext());
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            DebugManager.debug(DebugCategory.LOOKDEV, "Root layout created successfully");

            // Left panel: Model tree (20% width)
            DebugManager.debug(DebugCategory.LOOKDEV, "Creating left container for ModelTreeFragment...");
            icyllis.modernui.fragment.FragmentContainerView leftContainer =
                new icyllis.modernui.fragment.FragmentContainerView(requireContext());
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
            leftParams.weight = 0.2f;
            leftContainer.setLayoutParams(leftParams);
            leftContainer.setId(View.generateViewId());
            DebugManager.debug(DebugCategory.LOOKDEV, "Left container created with ID: " + leftContainer.getId());
            root.addView(leftContainer);

            // Center panel: Preview (60% width)
            DebugManager.debug(DebugCategory.LOOKDEV, "Creating center container for PreviewFragment...");
            icyllis.modernui.fragment.FragmentContainerView centerContainer =
                new icyllis.modernui.fragment.FragmentContainerView(requireContext());
            LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
            centerParams.weight = 0.6f;
            centerContainer.setLayoutParams(centerParams);
            centerContainer.setId(View.generateViewId());
            DebugManager.debug(DebugCategory.LOOKDEV, "Center container created with ID: " + centerContainer.getId());
            root.addView(centerContainer);

            // Right panel: Parameters (20% width)
            DebugManager.debug(DebugCategory.LOOKDEV, "Creating right container for ParameterPanelFragment...");
            icyllis.modernui.fragment.FragmentContainerView rightContainer =
                new icyllis.modernui.fragment.FragmentContainerView(requireContext());
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
            rightParams.weight = 0.2f;
            rightContainer.setLayoutParams(rightParams);
            rightContainer.setId(View.generateViewId());
            DebugManager.debug(DebugCategory.LOOKDEV, "Right container created with ID: " + rightContainer.getId());
            root.addView(rightContainer);

            DebugManager.info(DebugCategory.LOOKDEV, "=== LookDevScreen.onCreateView COMPLETE - returning root ===");
            return root;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "FATAL ERROR in LookDevScreen.onCreateView", e);
            throw e;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            DebugManager.info(DebugCategory.LOOKDEV, "=== LookDevScreen.onViewCreated START ===");

            // Add child fragments using FragmentManager
            LinearLayout root = (LinearLayout) view;
            icyllis.modernui.fragment.FragmentContainerView leftContainer =
                (icyllis.modernui.fragment.FragmentContainerView) root.getChildAt(0);
            icyllis.modernui.fragment.FragmentContainerView centerContainer =
                (icyllis.modernui.fragment.FragmentContainerView) root.getChildAt(1);
            icyllis.modernui.fragment.FragmentContainerView rightContainer =
                (icyllis.modernui.fragment.FragmentContainerView) root.getChildAt(2);

            DebugManager.debug(DebugCategory.LOOKDEV, "Adding child fragments via FragmentManager...");
            getChildFragmentManager().beginTransaction()
                .add(leftContainer.getId(), modelTreeFragment, "model_tree")
                .add(centerContainer.getId(), previewFragment, "preview")
                .add(rightContainer.getId(), parameterPanelFragment, "parameters")
                .commit();
            DebugManager.info(DebugCategory.LOOKDEV, "✓ Child fragments added successfully");

            // Setup keyboard shortcuts
            setupKeyboardShortcuts(view);
            DebugManager.debug(DebugCategory.LOOKDEV, "Keyboard shortcuts configured");

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
        // Try to load first available model from cache
        var cachedModels = cn.minerealms.assimploader.core.AssimpModelLoader.getAllCachedModels();

        if (!cachedModels.isEmpty()) {
            // Get first model from cache
            var firstEntry = cachedModels.entrySet().iterator().next();
            String modelName = firstEntry.getKey();
            AssimpScene scene = firstEntry.getValue();

            DebugManager.info(DebugCategory.LOOKDEV, "Loading model from cache: {}", modelName);
            currentModel = scene;

            // Extract materials from the loaded model
            extractMaterials(scene);

            // Update preview with model
            if (previewFragment != null) {
                previewFragment.setModel(currentModel);
            }
        } else {
            DebugManager.warn(DebugCategory.LOOKDEV, "No models in cache, creating test materials only");

            // Create test materials as fallback
            PBRMaterial mat1 = new PBRMaterial("Test Material 1");
            mat1.setRoughnessFactor(0.3f);
            mat1.setMetallicFactor(0.8f);

            PBRMaterial mat2 = new PBRMaterial("Test Material 2");
            mat2.setRoughnessFactor(0.7f);
            mat2.setMetallicFactor(0.2f);

            materials.add(mat1);
            materials.add(mat2);
        }

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
        try {
            DebugManager.info(DebugCategory.LOOKDEV, "Loading model: {}", modelPath);

            // Get model from cache
            AssimpScene scene = cn.minerealms.assimploader.core.AssimpModelLoader.getCachedModel(modelPath);

            if (scene == null) {
                DebugManager.error(DebugCategory.LOOKDEV, "Model not found in cache: {}", modelPath);
                return false;
            }

            // Set as current model
            currentModel = scene;
            DebugManager.info(DebugCategory.LOOKDEV, "Model loaded: {} meshes, {} materials",
                scene.getMeshCount(), scene.getMaterialCount());

            // Extract materials from the model
            extractMaterials(scene);

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

            // Auto-select first material if available
            if (!materials.isEmpty()) {
                selectMaterial(0);
                DebugManager.debug(DebugCategory.LOOKDEV, "Auto-selected first material");
            }

            return true;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to load model: " + modelPath, e);
            return false;
        }
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
            DebugManager.debug(DebugCategory.LOOKDEV, "Found {} Assimp materials in scene", assimpMaterials.size());

            // Convert to PBR materials
            for (int i = 0; i < assimpMaterials.size(); i++) {
                var assimpMat = assimpMaterials.get(i);

                // Create PBR material with name from index
                String materialName = "Material " + (i + 1);
                PBRMaterial pbrMat = new PBRMaterial(materialName);

                // Set default PBR values (will be overridable in UI)
                pbrMat.setRoughnessFactor(0.5f);
                pbrMat.setMetallicFactor(0.0f);

                // Extract texture paths if available
                String diffuseTex = assimpMat.getDiffuseTexture();
                String normalTex = assimpMat.getNormalTexture();
                String specularTex = assimpMat.getSpecularTexture();

                // Convert texture paths to ResourceLocations
                if (diffuseTex != null) {
                    DebugManager.debug(DebugCategory.LOOKDEV, "Material {} has diffuse texture: {}", i, diffuseTex);

                    // Check if it's an embedded texture (e.g., "*0", "*1")
                    if (assimpMat.isEmbeddedTexture(diffuseTex)) {
                        int texIndex = assimpMat.getEmbeddedTextureIndex(diffuseTex);
                        if (texIndex >= 0) {
                            // Create ResourceLocation for embedded texture
                            net.minecraft.resources.ResourceLocation texLoc =
                                new net.minecraft.resources.ResourceLocation("assimploader",
                                    "lookdev/embedded_" + texIndex);
                            pbrMat.setBaseColorTexture(texLoc);
                            DebugManager.debug(DebugCategory.LOOKDEV, "Set base color texture: {}", texLoc);
                        }
                    } else {
                        // External texture file - create ResourceLocation from path
                        // Extract filename without extension
                        String filename = diffuseTex.substring(diffuseTex.lastIndexOf('/') + 1);
                        filename = filename.substring(diffuseTex.lastIndexOf('\\') + 1);
                        net.minecraft.resources.ResourceLocation texLoc =
                            new net.minecraft.resources.ResourceLocation("assimploader",
                                "textures/" + filename);
                        pbrMat.setBaseColorTexture(texLoc);
                        DebugManager.debug(DebugCategory.LOOKDEV, "Set base color texture: {}", texLoc);
                    }
                }

                if (normalTex != null) {
                    DebugManager.debug(DebugCategory.LOOKDEV, "Material {} has normal texture: {}", i, normalTex);

                    if (assimpMat.isEmbeddedTexture(normalTex)) {
                        int texIndex = assimpMat.getEmbeddedTextureIndex(normalTex);
                        if (texIndex >= 0) {
                            net.minecraft.resources.ResourceLocation texLoc =
                                new net.minecraft.resources.ResourceLocation("assimploader",
                                    "lookdev/embedded_" + texIndex);
                            pbrMat.setNormalTexture(texLoc);
                            DebugManager.debug(DebugCategory.LOOKDEV, "Set normal texture: {}", texLoc);
                        }
                    }
                }

                if (specularTex != null) {
                    DebugManager.debug(DebugCategory.LOOKDEV, "Material {} has specular texture: {}", i, specularTex);

                    if (assimpMat.isEmbeddedTexture(specularTex)) {
                        int texIndex = assimpMat.getEmbeddedTextureIndex(specularTex);
                        if (texIndex >= 0) {
                            net.minecraft.resources.ResourceLocation texLoc =
                                new net.minecraft.resources.ResourceLocation("assimploader",
                                    "lookdev/embedded_" + texIndex);
                            pbrMat.setMetallicRoughnessTexture(texLoc);
                            DebugManager.debug(DebugCategory.LOOKDEV, "Set metallic/roughness texture: {}", texLoc);
                        }
                    }
                }

                materials.add(pbrMat);
                DebugManager.debug(DebugCategory.LOOKDEV, "Converted material {}: {}", i, pbrMat.getName());
            }

            DebugManager.info(DebugCategory.LOOKDEV, "Extracted {} materials from model", materials.size());
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

            DebugManager.info(DebugCategory.LOOKDEV, "Material selected: {} (index: {})", material.getName(), selectedMaterialIndex);

            // Update preview with new material (this creates the override manager)
            if (previewFragment != null) {
                previewFragment.setMaterial(material);
                DebugManager.debug(DebugCategory.LOOKDEV, "Preview fragment updated with material");
            }

            // Update parameters with selected material and override manager
            if (parameterPanelFragment != null && previewFragment != null) {
                parameterPanelFragment.setMaterial(material);
                parameterPanelFragment.setOverrideManager(previewFragment.getOverrideManager());
                DebugManager.debug(DebugCategory.LOOKDEV, "Parameter panel updated with material and override manager");
            }
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to handle material selection", e);
        }
    }

    /**
     * Called when material parameters change
     */
    public void onMaterialParametersChanged() {
        DebugManager.info(DebugCategory.LOOKDEV, "LookDevScreen.onMaterialParametersChanged called");
        // Refresh preview to show updated material
        if (previewFragment != null) {
            DebugManager.info(DebugCategory.LOOKDEV, "Calling previewFragment.refresh()");
            previewFragment.refresh();
        } else {
            DebugManager.warn(DebugCategory.LOOKDEV, "previewFragment is null, cannot refresh");
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
