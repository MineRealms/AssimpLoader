package cn.minerealms.assimploader.lookdev.ui;

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

import javax.annotation.Nonnull;
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
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();

        // Get providers from parent
        Fragment parent = getParentFragment();
        if (parent instanceof IModelProvider) {
            modelProvider = (IModelProvider) parent;
        }
        if (parent instanceof IMaterialProvider) {
            materialProvider = (IMaterialProvider) parent;
        }

        // Root scroll view
        scrollView = new ScrollView(context);
        scrollView.setBackground(new ColorDrawable(0xFF2C2C2C));

        // Content layout
        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(contentLayout.dp(8), contentLayout.dp(8),
                contentLayout.dp(8), contentLayout.dp(8));

        // Initial content
        addSectionTitle("Model Tree");
        addInfoText("No model loaded");

        scrollView.addView(contentLayout);
        return scrollView;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateModel();
    }

    /**
     * Update the tree with current model
     */
    public void updateModel() {
        if (contentLayout == null) return;

        // Clear existing content
        contentLayout.removeAllViews();

        // Model info section
        addSectionTitle("Model");
        if (modelProvider != null) {
            AssimpScene model = modelProvider.getCurrentModel();
            if (model != null) {
                String path = model.getSourcePath() != null ? model.getSourcePath().toString() : "Unknown";
                addInfoText("Loaded: " + path);
            } else {
                addInfoText("No model loaded");
            }
        } else {
            addInfoText("No model provider");
        }

        addSpacer(16);

        // Materials section
        addSectionTitle("Materials");
        if (materialProvider != null) {
            List<PBRMaterial> materials = materialProvider.getMaterials();
            if (materials.isEmpty()) {
                addInfoText("No materials");
            } else {
                int selectedIndex = materialProvider.getSelectedMaterialIndex();
                for (int i = 0; i < materials.size(); i++) {
                    PBRMaterial material = materials.get(i);
                    boolean isSelected = (i == selectedIndex);
                    addMaterialItem(material, i, isSelected);
                }
            }
        } else {
            addInfoText("No material provider");
        }

        addSpacer(16);

        // Meshes section (placeholder)
        addSectionTitle("Meshes");
        if (modelProvider != null) {
            AssimpScene model = modelProvider.getCurrentModel();
            if (model != null) {
                addInfoText("Mesh count: " + model.getMeshCount());
            } else {
                addInfoText("No meshes");
            }
        } else {
            addInfoText("No model provider");
        }
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

        // Click listener
        layout.setClickable(true);
        layout.setFocusable(true);
        layout.setOnClickListener(v -> {
            if (materialProvider != null) {
                materialProvider.selectMaterial(index);
                updateModel(); // Refresh to show selection
            }
        });

        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(layout, params);
    }

    private void addSpacer(int dp) {
        var spacer = new View(requireContext());
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, contentLayout.dp(dp));
        contentLayout.addView(spacer, params);
    }
}
