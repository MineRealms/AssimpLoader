package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.FeatureTier;
import cn.minerealms.assimploader.lookdev.PhotonIntegration;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.ui.NodePalettePanel;
import cn.minerealms.assimploader.lookdev.nodeeditor.ui.NodePropertiesPanel;
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
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node Editor Fragment - ComfyUI-style visual node editor.
 *
 * Features:
 * - Visual node graph editor
 * - Drag-and-drop connections
 * - Material/shader graph editing
 * - Real-time preview integration (when Photon is available)
 *
 * Supports two feature tiers:
 * - STANDALONE: Basic node editor without shader integration
 * - FULL_INTEGRATION: Complete system with Photon shader support
 */
public class NodeEditorFragment extends Fragment {

    private NodeEditorCanvas canvas;
    private NodePalettePanel palettePanel;
    private NodePropertiesPanel propertiesPanel;
    private Button resetViewButton;
    private Button generateMaterialButton;
    private Button togglePaletteButton;
    private Button togglePropertiesButton;
    private TextView statusText;
    private FeatureTier featureTier;
    private boolean paletteVisible = true;
    private boolean propertiesVisible = true;

    // State preservation
    private Map<String, Boolean> savedCategoryStates = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onCreateView START");

        // Detect feature tier
        featureTier = PhotonIntegration.getFeatureTier();
        DebugManager.info(DebugCategory.LOOKDEV,
            "[NodeEditorFragment] Feature tier: " + featureTier.getDisplayName());

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

        // Main content area (horizontal: palette + canvas)
        LinearLayout mainContent = new LinearLayout(context);
        mainContent.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        );
        mainParams.weight = 1.0f;
        mainContent.setLayoutParams(mainParams);
        root.addView(mainContent);

        // Node palette panel (left side)
        palettePanel = new NodePalettePanel(context, this::onNodeTypeSelected);
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(
            palettePanel.dp(200),
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        palettePanel.setLayoutParams(paletteParams);
        mainContent.addView(palettePanel);

        // Restore saved category states if available
        if (savedCategoryStates != null) {
            palettePanel.restoreCategoryStates(savedCategoryStates);
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Restored category states");
        }

        // Canvas (main area)
        canvas = new NodeEditorCanvas(context);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        canvasParams.weight = 1.0f;
        canvas.setLayoutParams(canvasParams);
        mainContent.addView(canvas);

        // Properties panel (right side)
        propertiesPanel = new NodePropertiesPanel(context);
        LinearLayout.LayoutParams propertiesParams = new LinearLayout.LayoutParams(
            propertiesPanel.dp(250),
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        propertiesPanel.setLayoutParams(propertiesParams);
        propertiesPanel.setBackground(new ColorDrawable(0xFF1E1E1E));
        mainContent.addView(propertiesPanel);

        // Set up canvas selection listener to update properties panel
        canvas.setOnNodeSelectionListener(node -> {
            propertiesPanel.setSelectedNode(node);
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Node selection changed: {}",
                node != null ? node.getTitle() : "none");
        });

        // Bottom status bar
        LinearLayout statusBar = createStatusBar(context);
        root.addView(statusBar);

        // Start with empty canvas - user can add nodes via toolbar
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] Canvas initialized (empty)");

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

        // Toggle Palette button
        togglePaletteButton = new Button(context);
        togglePaletteButton.setText("Hide Palette");
        togglePaletteButton.setOnClickListener(v -> togglePalette());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        toggleParams.setMargins(toolbar.dp(4), 0, 0, 0);
        toolbar.addView(togglePaletteButton, toggleParams);

        // Toggle Properties button
        togglePropertiesButton = new Button(context);
        togglePropertiesButton.setText("Hide Properties");
        togglePropertiesButton.setOnClickListener(v -> toggleProperties());
        LinearLayout.LayoutParams togglePropsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        togglePropsParams.setMargins(toolbar.dp(4), 0, 0, 0);
        toolbar.addView(togglePropertiesButton, togglePropsParams);

        // Add Node button
        Button addNodeButton = new Button(context);
        addNodeButton.setText("Add Node");
        addNodeButton.setOnClickListener(v -> showAddNodeMenu());
        LinearLayout.LayoutParams addNodeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        addNodeParams.setMargins(toolbar.dp(4), 0, 0, 0);
        toolbar.addView(addNodeButton, addNodeParams);

        // Generate Material button
        generateMaterialButton = new Button(context);
        generateMaterialButton.setText("Generate Material");
        generateMaterialButton.setOnClickListener(v -> onGenerateMaterial());
        LinearLayout.LayoutParams generateParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        generateParams.setMargins(toolbar.dp(4), 0, 0, 0);
        toolbar.addView(generateMaterialButton, generateParams);

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
        String initialStatus = "Ready - Pan: Click+Drag | Zoom: Mouse Wheel | " +
                               PhotonIntegration.getStatusMessage();
        statusText.setText(initialStatus);
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
     * Toggle palette panel visibility
     */
    private void togglePalette() {
        paletteVisible = !paletteVisible;
        if (palettePanel != null) {
            palettePanel.setVisibility(paletteVisible ? View.VISIBLE : View.GONE);
            togglePaletteButton.setText(paletteVisible ? "Hide Palette" : "Show Palette");
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Palette visibility: {}", paletteVisible);
        }
    }

    /**
     * Toggle properties panel visibility
     */
    private void toggleProperties() {
        propertiesVisible = !propertiesVisible;
        if (propertiesPanel != null) {
            propertiesPanel.setVisibility(propertiesVisible ? View.VISIBLE : View.GONE);
            togglePropertiesButton.setText(propertiesVisible ? "Hide Properties" : "Show Properties");
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Properties visibility: {}", propertiesVisible);
        }
    }

    /**
     * Called when user selects a node type from the palette
     */
    private void onNodeTypeSelected(NodeType nodeType) {
        // Add node at center of viewport
        float centerX = 400;
        float centerY = 300;

        // TODO: Get actual viewport center from canvas
        addNodeByType(nodeType, centerX, centerY);
    }

    /**
     * Show menu to add nodes
     */
    private void showAddNodeMenu() {
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] Opening Add Node menu");

        // Get all registered node types from NodeRegistry
        List<NodeType> nodeTypes = NodeRegistry.getAllNodeTypes();

        if (nodeTypes.isEmpty()) {
            setStatus("No node types registered!");
            DebugManager.warn(DebugCategory.LOOKDEV, "[NodeEditorFragment] NodeRegistry is empty!");
            return;
        }

        // Show palette if hidden
        if (!paletteVisible) {
            togglePalette();
        }

        setStatus("Select a node from the palette on the left");
        DebugManager.info(DebugCategory.LOOKDEV,
            "[NodeEditorFragment] Available node types: " + nodeTypes.size());
    }

    /**
     * Add a node of the specified type at the given position
     */
    public void addNodeByType(NodeType nodeType, float x, float y) {
        if (canvas == null) return;

        try {
            Node node = NodeFactory.createNode(nodeType);
            if (node != null) {
                node.setPosition(x, y);
                canvas.addNode(node);
                setStatus("Added node: " + nodeType.name());
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[NodeEditorFragment] Added node: " + nodeType + " at (" + x + ", " + y + ")");
            } else {
                setStatus("Failed to create node: " + nodeType);
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[NodeEditorFragment] NodeFactory returned null for type: " + nodeType);
            }
        } catch (Exception e) {
            setStatus("Error creating node: " + e.getMessage());
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Error creating node: " + nodeType, e);
        }
    }

    /**
     * Add a node of the specified type at the given position (legacy string-based)
     */
    public void addNode(String nodeType, float x, float y) {
        if (canvas == null) return;

        try {
            // Convert string to NodeType enum if needed
            NodeType type = NodeType.valueOf(nodeType);
            addNodeByType(type, x, y);
        } catch (IllegalArgumentException e) {
            setStatus("Unknown node type: " + nodeType);
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Unknown node type: " + nodeType);
        }
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
     * Generate material from node graph (for testing)
     */
    private void onGenerateMaterial() {
        PBRMaterial material = generateMaterial();

        // For now, just log the generated material
        DebugManager.info(DebugCategory.LOOKDEV,
            "[NodeEditorFragment] Material generated: " + material.getName());

        // Show material details in status
        String statusMsg = String.format("Generated: %s (Metallic: %.2f, Roughness: %.2f)",
            material.getName(),
            material.getMetallicFactor(),
            material.getRoughnessFactor());

        // Add feature tier info
        if (featureTier == FeatureTier.STANDALONE) {
            statusMsg += " [Standalone mode - shader generation not available]";
        } else {
            statusMsg += " [Full integration - shader generation available]";
        }

        setStatus(statusMsg);

        // TODO: BACKEND INTEGRATION (Phase 10)
        // 1. Get parent LookDevScreen via getParentFragment().getParentFragment()
        // 2. Call lookDevScreen.applyNodeGeneratedMaterial(material)
        // 3. This will:
        //    - Add material to materials list
        //    - Select the material
        //    - Update PreviewFragment with new material
        //    - Update ParameterPanelFragment
        //    - Trigger 3D preview refresh
        // 4. Need to implement LookDevScreen.applyNodeGeneratedMaterial() method
        // 5. Consider real-time updates when nodes change (not just on button click)
        // 6. In FULL_INTEGRATION mode: also generate and compile shader code
    }

    /**
     * Update status text
     */
    public void setStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    /**
     * Get the canvas
     */
    public NodeEditorCanvas getCanvas() {
        return canvas;
    }

    /**
     * Execute node graph and generate PBR material
     */
    public PBRMaterial generateMaterial() {
        if (canvas == null) {
            return new PBRMaterial("Default");
        }

        // Create executor
        NodeGraphExecutor executor = new NodeGraphExecutor(
            canvas.getNodes(),
            canvas.getConnections()
        );

        // Check for cycles
        if (executor.hasCycles()) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Cannot generate material: graph has cycles");
            setStatus("Error: Graph has cycles");
            return new PBRMaterial("Default");
        }

        // Execute graph
        List<OutputNode> outputNodes = executor.execute();

        if (outputNodes.isEmpty()) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] No output nodes found");
            setStatus("Warning: No output nodes");
            return new PBRMaterial("Default");
        }

        // Get first output node
        OutputNode outputNode = outputNodes.get(0);
        OutputNode.MaterialOutput output = outputNode.getMaterialOutput();

        // Build PBR material
        PBRMaterial material = new PBRMaterial("Node Generated");

        // Base color
        if (output.baseColor != null && output.baseColor instanceof Integer) {
            int color = (Integer) output.baseColor;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = ((color >> 24) & 0xFF) / 255.0f;
            material.setBaseColor(r, g, b, a);
        }

        // Metallic
        if (output.metallic != null && output.metallic instanceof Float) {
            material.setMetallicFactor((Float) output.metallic);
        }

        // Roughness
        if (output.roughness != null && output.roughness instanceof Float) {
            material.setRoughnessFactor((Float) output.roughness);
        }

        // Normal map
        if (output.normal != null && output.normal instanceof ResourceLocation) {
            material.setNormalTexture((ResourceLocation) output.normal);
        }

        // Emissive
        if (output.emissive != null && output.emissive instanceof Integer) {
            int color = (Integer) output.emissive;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            material.setEmissiveFactor(r, g, b);
        }

        DebugManager.info(DebugCategory.LOOKDEV,
            "[NodeEditorFragment] Generated material from node graph");
        setStatus("Material generated successfully");

        return material;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onViewCreated");
    }

    @Override
    public void onDestroyView() {
        // Save palette state before destroying view
        if (palettePanel != null) {
            savedCategoryStates = palettePanel.getCategoryStates();
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeEditorFragment] Saved category states: {}", savedCategoryStates.size());
        }

        super.onDestroyView();
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onDestroyView");
    }
}
