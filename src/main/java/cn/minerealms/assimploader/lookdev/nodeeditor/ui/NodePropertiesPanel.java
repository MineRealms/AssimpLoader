package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.ColorNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.ValueNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.TextureSampleNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.MathFunctionNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.vector.VectorMathNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.texture.MappingNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.MaterialPreviewView;

import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.core.Context;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Node Properties Panel
 *
 * Displays and allows editing of properties for the currently selected node.
 * Different property editors are shown based on the node type:
 * - ColorNode: RGBA color sliders
 * - ValueNode: Single value slider
 * - TextureSampleNode: Texture path input, filter/wrap mode dropdowns
 *
 * Layout: Right side panel (250dp width)
 */
public class NodePropertiesPanel extends LinearLayout {

    private Node selectedNode;
    private TextView titleLabel;
    private LinearLayout propertiesContainer;
    private MaterialPreviewView materialPreviewView;
    private NodePreviewPanel previewPanel;
    private OnPropertyChangeListener propertyChangeListener;

    public NodePropertiesPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);

        DebugManager.info(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Initializing properties panel");

        initializeUI();
    }

    private void initializeUI() {
        DebugManager.info(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Starting UI initialization");

        // Material preview at top
        materialPreviewView = new MaterialPreviewView(getContext());
        LayoutParams materialPreviewParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(200));
        materialPreviewParams.setMargins(0, 0, 0, 10);
        addView(materialPreviewView, materialPreviewParams);

        DebugManager.info(DebugCategory.LOOKDEV, "[NodePropertiesPanel] MaterialPreviewView added with height={}dp", 200);

        // Preview panel at top
        previewPanel = new NodePreviewPanel(getContext());
        LayoutParams previewParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(200));
        previewParams.setMargins(0, 0, 0, 10);
        addView(previewPanel, previewParams);

        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] NodePreviewPanel added with height={}dp", 200);

        // Title section
        titleLabel = new TextView(getContext());
        titleLabel.setText("Properties");
        titleLabel.setTextSize(16);
        titleLabel.setTextColor(0xFFFFFFFF);
        LayoutParams titleParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(10, 10, 10, 10);
        addView(titleLabel, titleParams);

        // Separator
        View separator = new View(getContext());
        separator.setBackground(createColorDrawable(0xFF444444));
        LayoutParams sepParams = new LayoutParams(LayoutParams.MATCH_PARENT, 2);
        sepParams.setMargins(0, 0, 0, 10);
        addView(separator, sepParams);

        // Properties container (scrollable)
        ScrollView scrollView = new ScrollView(getContext());
        propertiesContainer = new LinearLayout(getContext());
        propertiesContainer.setOrientation(VERTICAL);
        scrollView.addView(propertiesContainer);

        LayoutParams scrollParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f);
        addView(scrollView, scrollParams);

        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] UI initialization complete");

        // Initial state: no node selected
        showNoSelection();
    }

    /**
     * Set the currently selected node and display its properties
     */
    public void setSelectedNode(Node node) {
        if (selectedNode == node) {
            return; // No change
        }

        selectedNode = node;
        clearProperties();

        // Update preview panel
        previewPanel.setPreviewNode(node);

        if (node == null) {
            showNoSelection();
            return;
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodePropertiesPanel] Displaying properties for node: {} (type: {})",
            node.getTitle(), node.getClass().getSimpleName());

        titleLabel.setText("Properties: " + node.getTitle());

        // Display properties based on node type
        if (node instanceof ColorNode) {
            addColorNodeProperties((ColorNode) node);
        } else if (node instanceof ValueNode) {
            addValueNodeProperties((ValueNode) node);
        } else if (node instanceof TextureSampleNode) {
            addTextureSampleNodeProperties((TextureSampleNode) node);
        } else if (node instanceof MathFunctionNode) {
            addMathFunctionNodeProperties((MathFunctionNode) node);
        } else if (node instanceof VectorMathNode) {
            addVectorMathNodeProperties((VectorMathNode) node);
        } else if (node instanceof MappingNode) {
            addMappingNodeProperties((MappingNode) node);
        } else {
            showNoEditableProperties();
        }
    }

    /**
     * Update material preview with current node graph state
     */
    public void updateMaterialPreview(String materialName) {
        if (materialPreviewView != null) {
            materialPreviewView.setMaterialName(materialName);
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodePropertiesPanel] Material preview updated: {}", materialName);
        }
    }

    /**
     * Update material preview with actual material data for rendering
     */
    public void updateMaterialPreviewWithMaterial(cn.minerealms.assimploader.material.PBRMaterial material) {
        if (materialPreviewView != null) {
            materialPreviewView.setMaterial(material);
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodePropertiesPanel] Material preview updated with material data: {}", material.getName());
        }
    }

    /**
     * Clear all property editors
     */
    private void clearProperties() {
        propertiesContainer.removeAllViews();
    }

    /**
     * Show "No node selected" message
     */
    private void showNoSelection() {
        titleLabel.setText("Properties");
        TextView noSelectionLabel = new TextView(getContext());
        noSelectionLabel.setText("No node selected");
        noSelectionLabel.setTextColor(0xFF888888);
        noSelectionLabel.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(10, 20, 10, 10);
        propertiesContainer.addView(noSelectionLabel, params);
    }

    /**
     * Show "No editable properties" message
     */
    private void showNoEditableProperties() {
        TextView noPropsLabel = new TextView(getContext());
        noPropsLabel.setText("This node has no editable properties");
        noPropsLabel.setTextColor(0xFF888888);
        noPropsLabel.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(10, 20, 10, 10);
        propertiesContainer.addView(noPropsLabel, params);
    }

    // ========== ColorNode Properties ==========

    private void addColorNodeProperties(ColorNode node) {
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Adding ColorNode properties");

        addColorSlider("Red", node.getR(), value -> {
            node.setR(value);
            notifyPropertyChanged();
            DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] ColorNode R updated: {}", value);
        });

        addColorSlider("Green", node.getG(), value -> {
            node.setG(value);
            notifyPropertyChanged();
            DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] ColorNode G updated: {}", value);
        });

        addColorSlider("Blue", node.getB(), value -> {
            node.setB(value);
            notifyPropertyChanged();
            DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] ColorNode B updated: {}", value);
        });

        addColorSlider("Alpha", node.getA(), value -> {
            node.setA(value);
            notifyPropertyChanged();
            DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] ColorNode A updated: {}", value);
        });
    }

    private void addColorSlider(String label, float initialValue, ValueChangeListener listener) {
        // Label
        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextColor(0xFFCCCCCC);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(10, 10, 10, 5);
        propertiesContainer.addView(labelView, labelParams);

        // Slider
        SeekBar slider = new SeekBar(getContext());
        slider.setMax(100);
        slider.setProgress((int)(initialValue * 100));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float value = progress / 100.0f;
                    listener.onValueChanged(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        LayoutParams sliderParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sliderParams.setMargins(10, 0, 10, 5);
        propertiesContainer.addView(slider, sliderParams);

        // Value display
        TextView valueView = new TextView(getContext());
        valueView.setText(String.format("%.2f", initialValue));
        valueView.setTextColor(0xFFFFAA00);
        valueView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        LayoutParams valueParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        valueParams.setMargins(10, 0, 10, 10);
        propertiesContainer.addView(valueView, valueParams);

        // Update value display when slider changes
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                valueView.setText(String.format("%.2f", value));
                if (fromUser) {
                    listener.onValueChanged(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ========== ValueNode Properties ==========

    private void addValueNodeProperties(ValueNode node) {
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Adding ValueNode properties");

        // Label
        TextView labelView = new TextView(getContext());
        labelView.setText("Value");
        labelView.setTextColor(0xFFCCCCCC);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(10, 10, 10, 5);
        propertiesContainer.addView(labelView, labelParams);

        // Slider
        SeekBar slider = new SeekBar(getContext());
        slider.setMax(100);
        slider.setProgress((int)(node.getValue() * 100));

        LayoutParams sliderParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sliderParams.setMargins(10, 0, 10, 5);
        propertiesContainer.addView(slider, sliderParams);

        // Value display
        TextView valueView = new TextView(getContext());
        valueView.setText(String.format("%.2f", node.getValue()));
        valueView.setTextColor(0xFFFFAA00);
        valueView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        LayoutParams valueParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        valueParams.setMargins(10, 0, 10, 10);
        propertiesContainer.addView(valueView, valueParams);

        // Update value when slider changes
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                valueView.setText(String.format("%.2f", value));
                if (fromUser) {
                    node.setValue(value);
                    notifyPropertyChanged();
                    DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] ValueNode updated: {}", value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ========== TextureSampleNode Properties ==========

    private void addTextureSampleNodeProperties(TextureSampleNode node) {
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Adding TextureSampleNode properties");

        // Texture path input
        addTextInput("Texture Path", node.getTexturePath(), text -> {
            node.setTexturePath(text);
            notifyPropertyChanged();
            DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] TextureSampleNode path updated: {}", text);
        });

        // Filter mode dropdown
        addDropdown("Filter Mode",
            new String[]{"LINEAR", "NEAREST"},
            node.getFilterMode().ordinal(),
            index -> {
                TextureSampleNode.FilterMode mode = TextureSampleNode.FilterMode.values()[index];
                node.setFilterMode(mode);
                notifyPropertyChanged();
                DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] TextureSampleNode filter mode updated: {}", mode);
            });

        // Wrap mode dropdown
        addDropdown("Wrap Mode",
            new String[]{"REPEAT", "CLAMP"},
            node.getWrapMode().ordinal(),
            index -> {
                TextureSampleNode.WrapMode mode = TextureSampleNode.WrapMode.values()[index];
                node.setWrapMode(mode);
                notifyPropertyChanged();
                DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] TextureSampleNode wrap mode updated: {}", mode);
            });
    }

    private void addTextInput(String label, String initialValue, TextChangeListener listener) {
        // Label
        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextColor(0xFFCCCCCC);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(10, 10, 10, 5);
        propertiesContainer.addView(labelView, labelParams);

        // Text input display (clickable to open editor)
        TextView textView = new TextView(getContext());
        textView.setText(initialValue.isEmpty() ? "(Click to edit)" : initialValue);
        textView.setTextColor(initialValue.isEmpty() ? 0xFF666666 : 0xFFFFFFFF);
        textView.setBackground(createColorDrawable(0xFF2A2A2A));
        textView.setPadding(10, 10, 10, 10);

        // Open native text input screen on click
        textView.setOnClickListener(v -> {
            DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Opening text input dialog for: {}", label);

            // Get current screen (ModernUI screen)
            Minecraft mc = Minecraft.getInstance();

            // Open text input screen
            mc.execute(() -> {
                TextInputScreen inputScreen = new TextInputScreen(
                    Component.literal("Edit " + label),
                    mc.screen,
                    initialValue,
                    newValue -> {
                        // Update the display
                        textView.setText(newValue.isEmpty() ? "(Click to edit)" : newValue);
                        textView.setTextColor(newValue.isEmpty() ? 0xFF666666 : 0xFFFFFFFF);

                        // Notify listener
                        listener.onTextChanged(newValue);

                        DebugManager.debug(DebugCategory.LOOKDEV,
                            "[NodePropertiesPanel] Text updated: {} = {}", label, newValue);
                    }
                );
                mc.setScreen(inputScreen);
            });
        });

        LayoutParams inputParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(10, 0, 10, 10);
        propertiesContainer.addView(textView, inputParams);
    }

    private void addDropdown(String label, String[] options, int selectedIndex, IndexChangeListener listener) {
        // Label
        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextColor(0xFFCCCCCC);
        LayoutParams labelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(10, 10, 10, 5);
        propertiesContainer.addView(labelView, labelParams);

        // Button group for options (cycle through on click)
        Button optionButton = new Button(getContext());
        optionButton.setText(options[selectedIndex]);
        optionButton.setTextColor(0xFFFFFFFF);
        optionButton.setBackground(createColorDrawable(0xFF3A3A3A));

        final int[] currentIndex = {selectedIndex};
        optionButton.setOnClickListener(v -> {
            currentIndex[0] = (currentIndex[0] + 1) % options.length;
            optionButton.setText(options[currentIndex[0]]);
            listener.onIndexChanged(currentIndex[0]);
        });

        LayoutParams buttonParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(10, 0, 10, 10);
        propertiesContainer.addView(optionButton, buttonParams);
    }

    // ========== MathFunctionNode Properties ==========

    private void addMathFunctionNodeProperties(MathFunctionNode node) {
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Adding MathFunctionNode properties");

        String[] functionNames = new String[MathFunctionNode.Function.values().length];
        for (int i = 0; i < functionNames.length; i++) {
            functionNames[i] = MathFunctionNode.Function.values()[i].getDisplayName();
        }

        addDropdown("Function",
            functionNames,
            node.getFunction().ordinal(),
            index -> {
                MathFunctionNode.Function function = MathFunctionNode.Function.values()[index];
                node.setFunction(function);
                notifyPropertyChanged();
                DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] MathFunctionNode function updated: {}", function);
            });
    }

    // ========== VectorMathNode Properties ==========

    private void addVectorMathNodeProperties(VectorMathNode node) {
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Adding VectorMathNode properties");

        String[] operationNames = new String[VectorMathNode.Operation.values().length];
        for (int i = 0; i < operationNames.length; i++) {
            operationNames[i] = VectorMathNode.Operation.values()[i].getDisplayName();
        }

        addDropdown("Operation",
            operationNames,
            node.getOperation().ordinal(),
            index -> {
                VectorMathNode.Operation operation = VectorMathNode.Operation.values()[index];
                node.setOperation(operation);
                notifyPropertyChanged();
                DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] VectorMathNode operation updated: {}", operation);
            });
    }

    // ========== MappingNode Properties ==========

    private void addMappingNodeProperties(MappingNode node) {
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] Adding MappingNode properties");

        String[] typeNames = new String[MappingNode.MappingType.values().length];
        for (int i = 0; i < typeNames.length; i++) {
            typeNames[i] = MappingNode.MappingType.values()[i].getDisplayName();
        }

        addDropdown("Mapping Type",
            typeNames,
            node.getMappingType().ordinal(),
            index -> {
                MappingNode.MappingType type = MappingNode.MappingType.values()[index];
                node.setMappingType(type);
                notifyPropertyChanged();
                DebugManager.debug(DebugCategory.LOOKDEV, "[NodePropertiesPanel] MappingNode type updated: {}", type);
            });
    }

    // ========== Helper Methods ==========

    private Drawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    // ========== Listener Interfaces ==========

    @FunctionalInterface
    private interface ValueChangeListener {
        void onValueChanged(float value);
    }

    @FunctionalInterface
    private interface TextChangeListener {
        void onTextChanged(String text);
    }

    @FunctionalInterface
    private interface IndexChangeListener {
        void onIndexChanged(int index);
    }

    /**
     * Property change listener for backend integration
     */
    @FunctionalInterface
    public interface OnPropertyChangeListener {
        void onPropertyChanged();
    }

    /**
     * Set property change listener
     */
    public void setOnPropertyChangeListener(OnPropertyChangeListener listener) {
        this.propertyChangeListener = listener;
    }

    /**
     * Notify property change
     */
    private void notifyPropertyChanged() {
        if (propertyChangeListener != null) {
            propertyChangeListener.onPropertyChanged();
        }
    }

}
