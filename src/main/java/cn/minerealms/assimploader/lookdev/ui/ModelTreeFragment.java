package cn.minerealms.assimploader.lookdev.ui;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.TextView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fragment for displaying the model/material tree structure.
 *
 * Left panel of the LookDev viewer.
 * Shows:
 * - Model hierarchy
 * - Mesh list
 * - Material list
 */
public class ModelTreeFragment extends Fragment {

    private TextView placeholderText;

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        // Create root view
        TextView textView = new TextView(requireContext());
        textView.setText("Model Tree\n(Phase 5)");
        textView.setTextSize(16);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        this.placeholderText = textView;

        return textView;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // TODO Phase 5: Implement tree view
        // - Parse model structure
        // - Create expandable list
        // - Handle material selection
    }

    /**
     * Update the tree with a new model
     */
    public void updateModel() {
        // TODO Phase 5: Refresh tree view
    }
}
