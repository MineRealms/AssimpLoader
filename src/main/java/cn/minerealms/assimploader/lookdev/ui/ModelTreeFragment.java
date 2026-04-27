package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.IMaterialProvider;
import cn.minerealms.assimploader.lookdev.IModelProvider;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import icyllis.modernui.annotation.NonNull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Fragment for displaying the model/material tree structure.
 *
 * Left panel of the LookDev viewer.
 * Shows:
 * - Model info
 * - Material list (clickable)
 * - Mesh list
 */
public class ModelTreeFragment extends Fragment {

    private ScrollView scrollView;
    private LinearLayout contentLayout;
    private IModelProvider modelProvider;
    private IMaterialProvider materialProvider;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "=== ModelTreeFragment.onCreateView START ===");

        try {
            DebugManager.debug(DebugCategory.LOOKDEV, "Getting context...");
            var context = requireContext();
            DebugManager.debug(DebugCategory.LOOKDEV, "Context obtained: " + context.getClass().getSimpleName());

            // Get providers from parent
            DebugManager.debug(DebugCategory.LOOKDEV, "Getting parent fragment...");
            Fragment parent = getParentFragment();
            if (parent == null) {
                DebugManager.warn(DebugCategory.LOOKDEV, "Parent fragment is NULL - providers not available yet");
            } else {
                DebugManager.debug(DebugCategory.LOOKDEV, "Parent fragment: " + parent.getClass().getSimpleName());
                if (parent instanceof IModelProvider) {
                    modelProvider = (IModelProvider) parent;
                    DebugManager.debug(DebugCategory.LOOKDEV, "✓ IModelProvider obtained from parent");
                }
                if (parent instanceof IMaterialProvider) {
                    materialProvider = (IMaterialProvider) parent;
                    DebugManager.debug(DebugCategory.LOOKDEV, "✓ IMaterialProvider obtained from parent");
                }
            }

            // Root scroll view
            DebugManager.debug(DebugCategory.LOOKDEV, "Creating ScrollView...");
            scrollView = new ScrollView(context);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            scrollView.setBackground(new ColorDrawable(0xFF2C2C2C));
            DebugManager.debug(DebugCategory.LOOKDEV, "ScrollView created with dark background");

            // Content layout
            DebugManager.debug(DebugCategory.LOOKDEV, "Creating content LinearLayout...");
            contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setPadding(contentLayout.dp(8), contentLayout.dp(8),
                    contentLayout.dp(8), contentLayout.dp(8));
            DebugManager.debug(DebugCategory.LOOKDEV, "Content layout created");

            // Initial content
            DebugManager.debug(DebugCategory.LOOKDEV, "Adding initial content...");
            addSectionTitle("Model Tree");
            addInfoText("No model loaded");
            DebugManager.debug(DebugCategory.LOOKDEV, "Initial content added");

            DebugManager.debug(DebugCategory.LOOKDEV, "Adding contentLayout to scrollView...");
            scrollView.addView(contentLayout);
            DebugManager.info(DebugCategory.LOOKDEV, "=== ModelTreeFragment.onCreateView COMPLETE - returning scrollView ===");
            return scrollView;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "FATAL ERROR in ModelTreeFragment.onCreateView", e);
            throw e;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateModel();
    }

    /**
     * Update the tree with current model
     */
    public void updateModel() {
        if (contentLayout == null) {
            DebugManager.warn(DebugCategory.LOOKDEV, "Cannot update model: contentLayout is null");
            return;
        }

        DebugManager.debug(DebugCategory.LOOKDEV, "Updating model tree view");

        // Clear existing content
        contentLayout.removeAllViews();

        // Models section - show all cached models
        addSectionTitle("Models");
        addModelsList();

        addSpacer(16);

        // Materials section
        addSectionTitle("Materials");
        if (materialProvider != null) {
            List<PBRMaterial> materials = materialProvider.getMaterials();
            if (materials.isEmpty()) {
                addInfoText("No materials");
                DebugManager.debug(DebugCategory.LOOKDEV, "No materials to display");
            } else {
                int selectedIndex = materialProvider.getSelectedMaterialIndex();
                DebugManager.debug(DebugCategory.LOOKDEV, "Displaying {} materials, selected: {}",
                    materials.size(), selectedIndex);
                for (int i = 0; i < materials.size(); i++) {
                    PBRMaterial material = materials.get(i);
                    boolean isSelected = (i == selectedIndex);
                    addMaterialItem(material, i, isSelected);
                }
            }
        } else {
            addInfoText("No material provider");
            DebugManager.warn(DebugCategory.LOOKDEV, "Material provider is null");
        }

        addSpacer(16);

        // Meshes section
        addSectionTitle("Meshes");
        if (modelProvider != null) {
            AssimpScene model = modelProvider.getCurrentModel();
            if (model != null) {
                int meshCount = model.getMeshCount();
                addInfoText("Mesh count: " + meshCount);
                DebugManager.debug(DebugCategory.LOOKDEV, "Model has {} meshes", meshCount);
            } else {
                addInfoText("No meshes");
            }
        } else {
            addInfoText("No model provider");
        }

        DebugManager.debug(DebugCategory.LOOKDEV, "Model tree view updated");
    }

    /**
     * Add list of all cached models
     */
    private void addModelsList() {
        try {
            // Get all cached models
            var cachedModels = cn.minerealms.assimploader.core.AssimpModelLoader.getAllCachedModels();

            if (cachedModels.isEmpty()) {
                addInfoText("No models loaded");
                DebugManager.warn(DebugCategory.LOOKDEV, "No models in cache");
                return;
            }

            DebugManager.info(DebugCategory.LOOKDEV, "Found {} cached models", cachedModels.size());

            // Get current model for comparison
            AssimpScene currentModel = modelProvider != null ? modelProvider.getCurrentModel() : null;

            // Add each model as a clickable item
            for (var entry : cachedModels.entrySet()) {
                String modelName = entry.getKey();
                AssimpScene scene = entry.getValue();
                boolean isSelected = (scene == currentModel);

                addModelItem(modelName, scene, isSelected);
            }

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to load models list", e);
            addInfoText("Error loading models");
        }
    }

    /**
     * Add a clickable model item
     */
    private void addModelItem(String modelName, AssimpScene scene, boolean isSelected) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(layout.dp(8), layout.dp(6), layout.dp(8), layout.dp(6));

        // Highlight selected model
        if (isSelected) {
            layout.setBackground(new ColorDrawable(0xFF3A3A3A));
        } else {
            layout.setBackground(new ColorDrawable(0xFF2C2C2C));
        }

        // Model name
        var nameView = new TextView(requireContext());
        nameView.setText(modelName);
        nameView.setTextSize(12);
        nameView.setTextColor(isSelected ? 0xFFFFFFFF : 0xFFCCCCCC);
        layout.addView(nameView);

        // Model info
        var infoView = new TextView(requireContext());
        String info = String.format("%d meshes, %d materials",
                scene.getMeshCount(),
                scene.getMaterialCount());
        infoView.setText(info);
        infoView.setTextSize(10);
        infoView.setTextColor(0xFF888888);
        layout.addView(infoView);

        // Click listener - load this model
        layout.setClickable(true);
        layout.setFocusable(true);
        layout.setOnClickListener(v -> {
            if (modelProvider != null) {
                DebugManager.info(DebugCategory.LOOKDEV, "User selected model: {}", modelName);

                // Load the selected model
                boolean success = modelProvider.loadModel(modelName);

                if (success) {
                    DebugManager.info(DebugCategory.LOOKDEV, "Model loaded successfully: {}", modelName);
                    updateModel(); // Refresh to show selection
                } else {
                    DebugManager.error(DebugCategory.LOOKDEV, "Failed to load model: {}", modelName);
                }
            }
        });

        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(layout, params);
    }

    private void addSectionTitle(String title) {
        var textView = new TextView(requireContext());
        textView.setText(title);
        textView.setTextSize(14);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, contentLayout.dp(8), 0, contentLayout.dp(4));
        contentLayout.addView(textView, params);
    }

    private void addInfoText(String text) {
        var textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextSize(12);
        textView.setTextColor(0xFFAAAAAA);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(textView, params);
    }

    private void addMaterialItem(PBRMaterial material, int index, boolean isSelected) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(layout.dp(8), layout.dp(6), layout.dp(8), layout.dp(6));

        // Highlight selected material
        if (isSelected) {
            layout.setBackground(new ColorDrawable(0xFF3A3A3A));
        } else {
            layout.setBackground(new ColorDrawable(0xFF2C2C2C));
        }

        // Material name
        var nameView = new TextView(requireContext());
        nameView.setText(material.getName());
        nameView.setTextSize(12);
        nameView.setTextColor(isSelected ? 0xFFFFFFFF : 0xFFCCCCCC);
        layout.addView(nameView);

        // Material properties
        var propsView = new TextView(requireContext());
        String props = String.format("R: %.2f M: %.2f",
                material.getRoughnessFactor(),
                material.getMetallicFactor());
        propsView.setText(props);
        propsView.setTextSize(10);
        propsView.setTextColor(0xFF888888);
        layout.addView(propsView);

        // Click listener - switch to Node Editor and load material
        layout.setClickable(true);
        layout.setFocusable(true);
        layout.setOnClickListener(v -> {
            if (materialProvider != null) {
                DebugManager.info(DebugCategory.LOOKDEV,
                    "User clicked material: {} (index: {})", material.getName(), index);

                // Select the material
                materialProvider.selectMaterial(index);
                updateModel(); // Refresh to show selection

                // Switch to Node Editor tab and load material
                switchToNodeEditorWithMaterial(material);
            }
        });

        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(layout, params);
    }

    /**
     * Switch to Node Editor tab and load the specified material
     */
    private void switchToNodeEditorWithMaterial(PBRMaterial material) {
        try {
            // Get parent LookDevScreen
            Fragment parent = getParentFragment();
            if (!(parent instanceof LookDevScreen)) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "Cannot switch to Node Editor: parent is not LookDevScreen");
                return;
            }

            LookDevScreen lookDevScreen = (LookDevScreen) parent;

            // Get TabbedCenterFragment from LookDevScreen
            Fragment tabbedFragment = lookDevScreen.getChildFragmentManager()
                .findFragmentByTag("tabbed_center");

            if (!(tabbedFragment instanceof TabbedCenterFragment)) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "Cannot switch to Node Editor: TabbedCenterFragment not found");
                return;
            }

            TabbedCenterFragment tabbedCenter = (TabbedCenterFragment) tabbedFragment;

            // Switch to Node Editor tab
            tabbedCenter.switchToNodeEditor();

            // Get NodeEditorFragment and load material
            cn.minerealms.assimploader.lookdev.nodeeditor.NodeEditorFragment nodeEditor =
                tabbedCenter.getNodeEditorFragment();

            if (nodeEditor != null) {
                nodeEditor.setMaterial(material);
                DebugManager.info(DebugCategory.LOOKDEV,
                    "Switched to Node Editor and loaded material: {}", material.getName());
            } else {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "Cannot load material: NodeEditorFragment not found");
            }

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "Failed to switch to Node Editor with material", e);
        }
    }

    private void addSpacer(int dp) {
        var spacer = new View(requireContext());
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, contentLayout.dp(dp));
        contentLayout.addView(spacer, params);
    }
}
