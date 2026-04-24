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
 * Fragment for 3D model preview rendering.
 *
 * Center panel of the LookDev viewer.
 * Shows:
 * - Real-time 3D model rendering
 * - Camera controls (rotate, zoom)
 * - Material preview with overrides applied
 */
public class PreviewFragment extends Fragment {

    private TextView placeholderText;

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        // Create root view
        TextView textView = new TextView(requireContext());
        textView.setText("3D Preview\n(Phase 4)");
        textView.setTextSize(16);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        this.placeholderText = textView;

        return textView;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // TODO Phase 4: Implement 3D rendering
        // - Create PreviewRenderer
        // - Setup camera controls
        // - Handle mouse input (drag to rotate, scroll to zoom)
        // - Render model with effective material
    }

    /**
     * Refresh the preview (called when material parameters change)
     */
    public void refresh() {
        // TODO Phase 4: Re-render with updated material
    }
}
