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
        View leftPanel = createPanel("Model Tree\n(Phase 5)", 0xFF2C2C2C);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        leftParams.weight = 0.2f;
        leftPanel.setLayoutParams(leftParams);
        root.addView(leftPanel);

        // Center panel: Preview (60% width)
        View centerPanel = createPanel("3D Preview\n(Phase 4)", 0xFF1E1E1E);
        LinearLayout.LayoutParams centerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        centerParams.weight = 0.6f;
        centerPanel.setLayoutParams(centerParams);
        root.addView(centerPanel);

        // Right panel: Parameters (20% width)
        View rightPanel = createPanel("Parameters\n(Phase 3)", 0xFF2C2C2C);
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

        // TODO: Add child fragments to their containers
        // For now, just showing placeholder panels
    }

    /**
     * Create a placeholder panel with text
     */
    private View createPanel(String text, int backgroundColor) {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(new icyllis.modernui.graphics.drawable.ColorDrawable(backgroundColor));
        panel.setGravity(Gravity.CENTER);

        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextSize(16);
        textView.setTextColor(0xFFCCCCCC);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        panel.addView(textView);

        return panel;
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
        AssimpLoader.LOGGER.info("Material selected: {}", getMaterialName());

        // Update preview
        if (previewFragment != null) {
            previewFragment.refresh();
        }

        // Update parameters with selected material
        if (parameterPanelFragment != null && selectedMaterialIndex >= 0 && selectedMaterialIndex < materials.size()) {
            parameterPanelFragment.setMaterial(materials.get(selectedMaterialIndex));
        }
    }

    /**
     * Called when model is loaded
     */
    private void onModelLoaded() {
        AssimpLoader.LOGGER.info("Model loaded: {}", getModelName());

        // Extract materials from model
        if (currentModel != null) {
            // TODO Phase 5: Extract materials from AssimpModel
            materials.clear();
            selectedMaterialIndex = -1;
        }

        // Update tree view
        if (modelTreeFragment != null) {
            modelTreeFragment.updateModel();
        }
    }
}
