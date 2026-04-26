package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeRegistry;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeType;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node Palette Panel - displays available node types organized by category.
 *
 * Features:
 * - Categorized node list (Input, Math, Utility, Output)
 * - Click to add node to canvas
 * - Collapsible categories (with state preservation)
 * - Search/filter support (future)
 */
public class NodePalettePanel extends LinearLayout {

    private final NodeSelectionListener listener;
    private final Map<String, LinearLayout> categoryContainers = new HashMap<>();
    private final Map<String, Boolean> categoryExpandedState = new HashMap<>();
    private final Map<String, TextView> categoryHeaders = new HashMap<>();

    public interface NodeSelectionListener {
        void onNodeSelected(NodeType nodeType);
    }

    public NodePalettePanel(Context context, NodeSelectionListener listener) {
        super(context);
        this.listener = listener;

        setOrientation(VERTICAL);
        setBackground(new ColorDrawable(0xFF252525));
        setPadding(dp(8), dp(8), dp(8), dp(8));

        // Title
        TextView title = new TextView(context);
        title.setText("Node Palette");
        title.setTextSize(14);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(dp(4), dp(4), dp(4), dp(8));
        addView(title);

        // Scrollable content
        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        );
        scrollParams.weight = 1.0f;
        scrollView.setLayoutParams(scrollParams);

        LinearLayout scrollContent = new LinearLayout(context);
        scrollContent.setOrientation(VERTICAL);
        scrollView.addView(scrollContent);
        addView(scrollView);

        // Build node categories
        buildNodeCategories(scrollContent);

        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePalettePanel] Initialized with collapsible categories");
    }

    private void buildNodeCategories(LinearLayout container) {
        // Get all registered node types
        List<NodeType> nodeTypes = NodeRegistry.getAllNodeTypes();

        // Organize by category
        Map<String, java.util.List<NodeType>> categorized = new HashMap<>();
        categorized.put("Input", new java.util.ArrayList<>());
        categorized.put("Math", new java.util.ArrayList<>());
        categorized.put("Utility", new java.util.ArrayList<>());
        categorized.put("Output", new java.util.ArrayList<>());

        for (NodeType type : nodeTypes) {
            String category = getCategoryForNodeType(type);
            categorized.get(category).add(type);
        }

        // Create category sections (all expanded by default)
        for (Map.Entry<String, java.util.List<NodeType>> entry : categorized.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                categoryExpandedState.put(entry.getKey(), true);
                createCategorySection(container, entry.getKey(), entry.getValue());
            }
        }
    }

    private void createCategorySection(LinearLayout container, String categoryName, List<NodeType> nodes) {
        Context context = getContext();

        // Category header (clickable to collapse/expand)
        TextView categoryHeader = new TextView(context);
        updateCategoryHeaderText(categoryHeader, categoryName, true);
        categoryHeader.setTextSize(12);
        categoryHeader.setTextColor(0xFFCCCCCC);
        categoryHeader.setPadding(dp(4), dp(8), dp(4), dp(8));
        categoryHeader.setBackground(new ColorDrawable(0xFF1E1E1E));
        categoryHeader.setClickable(true);
        categoryHeader.setFocusable(true);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, dp(4), 0, 0);
        container.addView(categoryHeader, headerParams);

        categoryHeaders.put(categoryName, categoryHeader);

        // Category content
        LinearLayout categoryContent = new LinearLayout(context);
        categoryContent.setOrientation(VERTICAL);
        categoryContent.setPadding(dp(8), dp(4), dp(4), dp(4));
        container.addView(categoryContent);

        categoryContainers.put(categoryName, categoryContent);

        // Add node buttons
        for (NodeType nodeType : nodes) {
            createNodeButton(categoryContent, nodeType);
        }

        // Set up click listener for collapse/expand
        categoryHeader.setOnClickListener(v -> toggleCategory(categoryName));
    }

    /**
     * Toggle category expanded/collapsed state
     */
    private void toggleCategory(String categoryName) {
        Boolean currentState = categoryExpandedState.get(categoryName);
        if (currentState == null) return;

        boolean newState = !currentState;
        categoryExpandedState.put(categoryName, newState);

        // Update visibility
        LinearLayout content = categoryContainers.get(categoryName);
        if (content != null) {
            content.setVisibility(newState ? View.VISIBLE : View.GONE);
        }

        // Update header text
        TextView header = categoryHeaders.get(categoryName);
        if (header != null) {
            updateCategoryHeaderText(header, categoryName, newState);
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodePalettePanel] Category '{}' {}", categoryName, newState ? "expanded" : "collapsed");
    }

    /**
     * Update category header text with expand/collapse indicator
     */
    private void updateCategoryHeaderText(TextView header, String categoryName, boolean expanded) {
        String arrow = expanded ? "▼" : "▶";
        header.setText(arrow + " " + categoryName);
    }

    private void createNodeButton(LinearLayout container, NodeType nodeType) {
        Context context = getContext();

        Button nodeButton = new Button(context);
        nodeButton.setText(getDisplayName(nodeType));
        nodeButton.setTextSize(11);
        nodeButton.setBackground(new ColorDrawable(0xFF3A3A3A));
        nodeButton.setTextColor(0xFFFFFFFF);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, dp(2), 0, dp(2));
        nodeButton.setLayoutParams(buttonParams);

        nodeButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNodeSelected(nodeType);
                DebugManager.debug(DebugCategory.LOOKDEV,
                    "[NodePalettePanel] Node selected: {}", nodeType);
            }
        });

        container.addView(nodeButton);
    }

    /**
     * Get the expanded/collapsed state for all categories (for state preservation)
     */
    public Map<String, Boolean> getCategoryStates() {
        return new HashMap<>(categoryExpandedState);
    }

    /**
     * Restore category expanded/collapsed states
     */
    public void restoreCategoryStates(Map<String, Boolean> states) {
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            String categoryName = entry.getKey();
            Boolean expanded = entry.getValue();

            if (categoryExpandedState.containsKey(categoryName) &&
                !categoryExpandedState.get(categoryName).equals(expanded)) {
                toggleCategory(categoryName);
            }
        }
    }

    private String getCategoryForNodeType(NodeType type) {
        switch (type) {
            case TEXTURE_SAMPLE:
            case VALUE:
            case COLOR:
            case UV_COORDINATE:
                return "Input";

            case MULTIPLY:
            case ADD:
            case LERP:
                return "Math";

            case NORMAL_MAP:
                return "Utility";

            case MATERIAL_OUTPUT:
                return "Output";

            default:
                return "Other";
        }
    }

    private String getDisplayName(NodeType type) {
        switch (type) {
            case TEXTURE_SAMPLE: return "Texture Sample";
            case VALUE: return "Value";
            case COLOR: return "Color";
            case UV_COORDINATE: return "UV Coordinate";
            case MULTIPLY: return "Multiply";
            case ADD: return "Add";
            case LERP: return "Mix (Lerp)";
            case NORMAL_MAP: return "Normal Map";
            case MATERIAL_OUTPUT: return "Material Output";
            default: return type.name();
        }
    }
}
