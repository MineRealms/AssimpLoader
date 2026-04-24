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
 * Fragment for material parameter controls.
 *
 * Right panel of the LookDev viewer.
 * Shows:
 * - Texture channel toggles (BaseColor, Normal, AO, etc.)
 * - Parameter sliders (Roughness, Metallic, Emission, etc.)
 * - RenderType dropdown
 * - Reset button
 */
public class ParameterPanelFragment extends Fragment {

    private TextView placeholderText;

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        // Create root view
        TextView textView = new TextView(requireContext());
        textView.setText("Parameters\n(Phase 3)");
        textView.setTextSize(16);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        this.placeholderText = textView;

        return textView;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // TODO Phase 3: Implement parameter controls
        // - Add channel toggles (CheckBox)
        // - Add parameter sliders (Slider)
        // - Add RenderType dropdown (Spinner)
        // - Add Reset button
        // - Wire up to MaterialOverrideManager
    }

    /**
     * Update UI with material parameters
     */
    public void updateMaterial() {
        // TODO Phase 3: Load material parameters into UI
    }

    /**
     * Reset all parameters to defaults
     */
    public void resetParameters() {
        // TODO Phase 3: Reset UI and notify manager
    }
}
