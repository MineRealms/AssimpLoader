package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import javax.annotation.Nullable;

/**
 * Node Editor Fragment - ComfyUI-style visual node editor.
 *
 * Features:
 * - Visual node graph editor
 * - Drag-and-drop connections
 * - Material/shader graph editing
 * - Real-time preview integration
 */
public class NodeEditorFragment extends Fragment {

    private NodeEditorCanvas canvas;
    private Button resetViewButton;
    private TextView statusText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onCreateView START");

        var context = requireContext();

        // Root layout
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackground(new ColorDrawable(0xFF2B2B2B));

        // Top toolbar
        LinearLayout toolbar = createToolbar(context);
        root.addView(toolbar);

        // Canvas (main area)
        canvas = new NodeEditorCanvas(context);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        );
        canvasParams.weight = 1.0f;
        canvas.setLayoutParams(canvasParams);
        root.addView(canvas);

        // Bottom status bar
        LinearLayout statusBar = createStatusBar(context);
        root.addView(statusBar);

        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onCreateView COMPLETE");
        return root;
    }

    /**
     * Create top toolbar with controls
     */
    private LinearLayout createToolbar(icyllis.modernui.core.Context context) {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackground(new ColorDrawable(0xFF1E1E1E));
        toolbar.setPadding(toolbar.dp(8), toolbar.dp(4), toolbar.dp(8), toolbar.dp(4));
        LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        toolbar.setLayoutParams(toolbarParams);

        // Title
        TextView title = new TextView(context);
        title.setText("Node Editor");
        title.setTextSize(14);
        title.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.weight = 1.0f;
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        toolbar.addView(title, titleParams);

        // Reset View button
        resetViewButton = new Button(context);
        resetViewButton.setText("Reset View");
        resetViewButton.setOnClickListener(v -> resetView());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resetParams.setMargins(toolbar.dp(4), 0, 0, 0);
        toolbar.addView(resetViewButton, resetParams);

        return toolbar;
    }

    /**
     * Create bottom status bar
     */
    private LinearLayout createStatusBar(icyllis.modernui.core.Context context) {
        LinearLayout statusBar = new LinearLayout(context);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setBackground(new ColorDrawable(0xFF1E1E1E));
        statusBar.setPadding(statusBar.dp(8), statusBar.dp(4), statusBar.dp(8), statusBar.dp(4));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusBar.setLayoutParams(statusParams);

        // Status text
        statusText = new TextView(context);
        statusText.setText("Ready - Pan: Click+Drag | Zoom: Mouse Wheel");
        statusText.setTextSize(11);
        statusText.setTextColor(0xFFAAAAAA);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusBar.addView(statusText, textParams);

        return statusBar;
    }

    /**
     * Reset canvas view to default
     */
    private void resetView() {
        if (canvas != null) {
            canvas.resetView();
            DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] View reset");
        }
    }

    /**
     * Update status text
     */
    public void setStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onViewCreated");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onDestroyView");
    }
}
