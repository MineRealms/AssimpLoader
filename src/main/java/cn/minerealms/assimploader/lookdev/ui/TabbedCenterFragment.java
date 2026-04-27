package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEditorFragment;
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

import javax.annotation.Nullable;

/**
 * Tabbed center panel containing Preview and Node Editor.
 *
 * Allows switching between:
 * - Preview: 3D model preview
 * - Node Editor: Visual node graph editor
 */
public class TabbedCenterFragment extends Fragment {

    private PreviewFragment previewFragment;
    private NodeEditorFragment nodeEditorFragment;

    private Button previewTabButton;
    private Button nodeEditorTabButton;

    private icyllis.modernui.fragment.FragmentContainerView contentContainer;

    private enum Tab {
        PREVIEW,
        NODE_EDITOR
    }

    private Tab currentTab = Tab.PREVIEW;

    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize child fragments
        previewFragment = new PreviewFragment();
        nodeEditorFragment = new NodeEditorFragment();

        DebugManager.info(DebugCategory.LOOKDEV, "[TabbedCenterFragment] Created with Preview and NodeEditor");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "[TabbedCenterFragment] onCreateView START");

        var context = requireContext();

        // Root layout
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Tab bar
        LinearLayout tabBar = createTabBar(context);
        root.addView(tabBar);

        // Content container
        contentContainer = new icyllis.modernui.fragment.FragmentContainerView(context);
        contentContainer.setId(View.generateViewId());
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        );
        contentParams.weight = 1.0f;
        contentContainer.setLayoutParams(contentParams);
        root.addView(contentContainer);

        DebugManager.info(DebugCategory.LOOKDEV, "[TabbedCenterFragment] onCreateView COMPLETE");
        return root;
    }

    /**
     * Create tab bar with Preview and Node Editor buttons
     */
    private LinearLayout createTabBar(icyllis.modernui.core.Context context) {
        LinearLayout tabBar = new LinearLayout(context);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackground(new ColorDrawable(0xFF2B2B2B));
        tabBar.setPadding(tabBar.dp(4), tabBar.dp(4), tabBar.dp(4), tabBar.dp(4));
        LinearLayout.LayoutParams tabBarParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tabBar.setLayoutParams(tabBarParams);

        // Preview tab button
        previewTabButton = new Button(context);
        previewTabButton.setText("Preview");
        previewTabButton.setOnClickListener(v -> switchToTab(Tab.PREVIEW));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        previewParams.setMargins(0, 0, tabBar.dp(4), 0);
        tabBar.addView(previewTabButton, previewParams);

        // Node Editor tab button
        nodeEditorTabButton = new Button(context);
        nodeEditorTabButton.setText("Node Editor");
        nodeEditorTabButton.setOnClickListener(v -> switchToTab(Tab.NODE_EDITOR));
        LinearLayout.LayoutParams nodeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tabBar.addView(nodeEditorTabButton, nodeParams);

        return tabBar;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Show preview tab by default
        switchToTab(Tab.PREVIEW);

        DebugManager.info(DebugCategory.LOOKDEV, "[TabbedCenterFragment] onViewCreated - showing Preview tab");
    }

    /**
     * Switch to specified tab
     */
    private void switchToTab(Tab tab) {
        // Always perform the switch, even if it's the same tab
        // This ensures the fragment is added on first load
        currentTab = tab;

        // Update button states
        updateTabButtonStates();

        // Switch fragment
        Fragment targetFragment = (tab == Tab.PREVIEW) ? previewFragment : nodeEditorFragment;
        String tag = (tab == Tab.PREVIEW) ? "preview" : "node_editor";

        getChildFragmentManager().beginTransaction()
            .replace(contentContainer.getId(), targetFragment, tag)
            .commit();

        // Notify parent LookDevScreen about tab switch
        Fragment parent = getParentFragment();
        if (parent instanceof LookDevScreen) {
            ((LookDevScreen) parent).onTabSwitched(tab == Tab.NODE_EDITOR);
        }

        DebugManager.info(DebugCategory.LOOKDEV, "[TabbedCenterFragment] Switched to tab: " + tab);
    }

    /**
     * Update tab button visual states
     */
    private void updateTabButtonStates() {
        if (previewTabButton != null && nodeEditorTabButton != null) {
            // Active tab: lighter background
            // Inactive tab: darker background
            int activeColor = 0xFF3A3A3A;
            int inactiveColor = 0xFF2B2B2B;

            previewTabButton.setBackground(new ColorDrawable(
                currentTab == Tab.PREVIEW ? activeColor : inactiveColor
            ));
            nodeEditorTabButton.setBackground(new ColorDrawable(
                currentTab == Tab.NODE_EDITOR ? activeColor : inactiveColor
            ));
        }
    }

    /**
     * Get the preview fragment (for external access)
     */
    public PreviewFragment getPreviewFragment() {
        return previewFragment;
    }

    /**
     * Get the node editor fragment (for external access)
     */
    public NodeEditorFragment getNodeEditorFragment() {
        return nodeEditorFragment;
    }

    /**
     * Switch to the Node Editor tab (public method for external access)
     */
    public void switchToNodeEditor() {
        switchToTab(Tab.NODE_EDITOR);
    }

    /**
     * Switch to the Preview tab (public method for external access)
     */
    public void switchToPreview() {
        switchToTab(Tab.PREVIEW);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DebugManager.info(DebugCategory.LOOKDEV, "[TabbedCenterFragment] onDestroyView");
    }
}
