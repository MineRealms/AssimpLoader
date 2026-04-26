package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.FeatureTier;
import cn.minerealms.assimploader.lookdev.PhotonIntegration;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.*;
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
import java.util.List;

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
    private Button resetViewButton;
    private Button generateMaterialButton;
    private TextView statusText;
    private FeatureTier featureTier;

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

        // Create example nodes for testing
        createExampleNodes();

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
     * Create example nodes for testing
     */
    private void createExampleNodes() {
        if (canvas == null) return;

        // Create a simple material graph:
        // ColorInput -> OutputNode
        // FloatInput (Metallic) -> OutputNode
        // FloatInput (Roughness) -> OutputNode

        // Color input node
        ColorInputNode colorNode = new ColorInputNode();
        colorNode.setPosition(100, 100);
        colorNode.setColor(0.8f, 0.2f, 0.2f); // Red color
        canvas.addNode(colorNode);

        // Metallic input node
        FloatInputNode metallicNode = new FloatInputNode();
        metallicNode.setPosition(100, 250);
        metallicNode.setValue(0.8f);
        canvas.addNode(metallicNode);

        // Roughness input node
        FloatInputNode roughnessNode = new FloatInputNode();
        roughnessNode.setPosition(100, 400);
        roughnessNode.setValue(0.3f);
        canvas.addNode(roughnessNode);

        // Math node (for demonstration)
        MathNode mathNode = new MathNode();
        mathNode.setPosition(350, 300);
        mathNode.setOperation(MathNode.Operation.MULTIPLY);
        canvas.addNode(mathNode);

        // Texture input node
        TextureInputNode textureNode = new TextureInputNode();
        textureNode.setPosition(100, 550);
        canvas.addNode(textureNode);

        // Output node
        OutputNode outputNode = new OutputNode();
        outputNode.setPosition(600, 250);
        canvas.addNode(outputNode);

        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] Created example nodes");
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
        super.onDestroyView();
        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorFragment] onDestroyView");
    }
}
