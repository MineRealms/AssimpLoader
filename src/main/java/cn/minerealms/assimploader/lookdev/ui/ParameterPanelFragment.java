package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.comparison.ComparisonManager;
import cn.minerealms.assimploader.lookdev.material.MaterialOverrideManager;
import cn.minerealms.assimploader.lookdev.preset.MaterialPreset;
import cn.minerealms.assimploader.lookdev.preset.PresetManager;
import cn.minerealms.assimploader.material.PBRMaterial;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import org.joml.Vector3f;
import org.joml.Vector4f;

import icyllis.modernui.annotation.NonNull;
import javax.annotation.Nullable;

/**
 * Fragment for material parameter controls.
 *
 * Right panel of the LookDev viewer.
 * Shows:
 * - Texture channel toggles (BaseColor, Normal, AO, etc.)
 * - Parameter sliders (Roughness, Metallic, Emission, etc.)
 * - Reset button
 */
public class ParameterPanelFragment extends Fragment {

    private MaterialOverrideManager overrideManager;
    private PresetManager presetManager;
    private ComparisonManager comparisonManager;
    private ScrollView scrollView;
    private LinearLayout contentLayout;

    // Channel toggles
    private SwitchButton baseColorToggle;
    private SwitchButton normalToggle;
    private SwitchButton metallicToggle;
    private SwitchButton roughnessToggle;
    private SwitchButton emissiveToggle;
    private SwitchButton aoToggle;
    private SwitchButton heightToggle;

    // Parameter sliders
    private SeekBar roughnessSlider;
    private SeekBar metallicSlider;
    private TextView roughnessValue;
    private TextView metallicValue;

    // Color sliders
    private SeekBar baseColorR, baseColorG, baseColorB, baseColorA;
    private SeekBar emissiveR, emissiveG, emissiveB;
    private TextView baseColorRValue, baseColorGValue, baseColorBValue, baseColorAValue;
    private TextView emissiveRValue, emissiveGValue, emissiveBValue;

    private Button resetButton;
    private Button savePresetButton;
    private Button loadPresetButton;
    private EditText presetNameInput;

    // Comparison controls
    private Button captureSnapshotButton;
    private Button compareSnapshotButton;
    private TextView snapshotCountText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "=== ParameterPanelFragment.onCreateView START ===");

        try {
            DebugManager.info(DebugCategory.LOOKDEV, "Getting context...");
            var context = requireContext();
            DebugManager.info(DebugCategory.LOOKDEV, "Context obtained");

            // Initialize preset manager
            DebugManager.info(DebugCategory.LOOKDEV, "Initializing PresetManager...");
            presetManager = new PresetManager();
            DebugManager.info(DebugCategory.LOOKDEV, "PresetManager initialized");

            // Initialize comparison manager
            DebugManager.info(DebugCategory.LOOKDEV, "Initializing ComparisonManager...");
            comparisonManager = new ComparisonManager();
            DebugManager.info(DebugCategory.LOOKDEV, "ComparisonManager initialized");

            // Root scroll view
            DebugManager.info(DebugCategory.LOOKDEV, "Creating ScrollView...");
            scrollView = new ScrollView(context);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            scrollView.setBackground(new ColorDrawable(0xFF2B2B2B));
            DebugManager.info(DebugCategory.LOOKDEV, "ScrollView created");

            // Content layout
            DebugManager.info(DebugCategory.LOOKDEV, "Creating content LinearLayout...");
            contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setPadding(contentLayout.dp(8), contentLayout.dp(8),
                    contentLayout.dp(8), contentLayout.dp(8));
            DebugManager.info(DebugCategory.LOOKDEV, "Content layout created");

            // Title
            DebugManager.info(DebugCategory.LOOKDEV, "Adding UI controls...");
            addSectionTitle("Material Parameters");

            // Channel toggles section
            addSectionTitle("Texture Channels");
            baseColorToggle = addChannelToggle("Base Color");
            normalToggle = addChannelToggle("Normal Map");
            metallicToggle = addChannelToggle("Metallic");
            roughnessToggle = addChannelToggle("Roughness");
            emissiveToggle = addChannelToggle("Emissive");
            aoToggle = addChannelToggle("Ambient Occlusion");
            heightToggle = addChannelToggle("Height Map");
            DebugManager.info(DebugCategory.LOOKDEV, "Channel toggles added");

            // Parameter sliders section
            addSectionTitle("Parameters");
            roughnessSlider = addParameterSlider("Roughness", 0, 100);
            roughnessValue = getLastValueLabel();
            metallicSlider = addParameterSlider("Metallic", 0, 100);
            metallicValue = getLastValueLabel();
            DebugManager.info(DebugCategory.LOOKDEV, "Parameter sliders added");

            // Base color section
            addSectionTitle("Base Color");
            baseColorR = addColorSlider("R", 0, 255);
            baseColorRValue = getLastValueLabel();
            baseColorG = addColorSlider("G", 0, 255);
            baseColorGValue = getLastValueLabel();
            baseColorB = addColorSlider("B", 0, 255);
            baseColorBValue = getLastValueLabel();
            baseColorA = addColorSlider("A", 0, 255);
            baseColorAValue = getLastValueLabel();
            DebugManager.info(DebugCategory.LOOKDEV, "Base color sliders added");

            // Emissive color section
            addSectionTitle("Emissive Color");
            emissiveR = addColorSlider("R", 0, 255);
            emissiveRValue = getLastValueLabel();
            emissiveG = addColorSlider("G", 0, 255);
            emissiveGValue = getLastValueLabel();
            emissiveB = addColorSlider("B", 0, 255);
            emissiveBValue = getLastValueLabel();
            DebugManager.info(DebugCategory.LOOKDEV, "Emissive color sliders added");

            // Preset section
            addSpacer(16);
            addSectionTitle("Presets");
            addPresetControls();
            DebugManager.info(DebugCategory.LOOKDEV, "Preset controls added");

            // Comparison section
            addSpacer(16);
            addSectionTitle("Before/After Comparison");
            addComparisonControls();
            DebugManager.info(DebugCategory.LOOKDEV, "Comparison controls added");

            // Reset button
            addSpacer(16);
            resetButton = new Button(context);
            resetButton.setText("Reset to Original");
            resetButton.setOnClickListener(v -> resetParameters());
            var resetParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            resetParams.setMargins(0, contentLayout.dp(8), 0, 0);
            contentLayout.addView(resetButton, resetParams);
            DebugManager.info(DebugCategory.LOOKDEV, "Reset button added");

            DebugManager.info(DebugCategory.LOOKDEV, "Adding contentLayout to scrollView...");
            scrollView.addView(contentLayout);
            DebugManager.info(DebugCategory.LOOKDEV, "=== ParameterPanelFragment.onCreateView COMPLETE - returning scrollView ===");
            return scrollView;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "FATAL ERROR in ParameterPanelFragment.onCreateView", e);
            throw e;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "=== ParameterPanelFragment.onViewCreated START ===");
        super.onViewCreated(view, savedInstanceState);

        DebugManager.info(DebugCategory.LOOKDEV, "Setting up listeners...");
        setupListeners();
        DebugManager.info(DebugCategory.LOOKDEV, "Listeners setup complete");

        // Update UI if material was set before view creation
        if (overrideManager != null) {
            DebugManager.info(DebugCategory.LOOKDEV, "Updating UI from material (overrideManager exists)");
            updateUIFromMaterial();
        } else {
            DebugManager.info(DebugCategory.LOOKDEV, "No overrideManager yet, skipping UI update");
        }

        DebugManager.info(DebugCategory.LOOKDEV, "=== ParameterPanelFragment.onViewCreated COMPLETE ===");
    }

    private void setupListeners() {
        DebugManager.info(DebugCategory.LOOKDEV, "setupListeners: Setting up channel toggle listeners...");
        // Channel toggle listeners
        baseColorToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableBaseColor();
                else overrideManager.disableBaseColor();
                notifyMaterialChanged();
            }
        });

        normalToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableNormalMap();
                else overrideManager.disableNormalMap();
                notifyMaterialChanged();
            }
        });

        metallicToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableMetallic();
                else overrideManager.disableMetallic();
                notifyMaterialChanged();
            }
        });

        roughnessToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableRoughness();
                else overrideManager.disableRoughness();
                notifyMaterialChanged();
            }
        });

        emissiveToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableEmissive();
                else overrideManager.disableEmissive();
                notifyMaterialChanged();
            }
        });

        aoToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableAO();
                else overrideManager.disableAO();
                notifyMaterialChanged();
            }
        });

        heightToggle.setOnCheckedChangeListener((v, checked) -> {
            if (overrideManager != null) {
                if (checked) overrideManager.enableHeight();
                else overrideManager.disableHeight();
                notifyMaterialChanged();
            }
        });

        // Parameter slider listeners
        roughnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && overrideManager != null) {
                    float value = progress / 100f;
                    overrideManager.setRoughnessOverride(value);
                    roughnessValue.setText(String.format("%.2f", value));
                    notifyMaterialChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        metallicSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && overrideManager != null) {
                    float value = progress / 100f;
                    overrideManager.setMetallicOverride(value);
                    metallicValue.setText(String.format("%.2f", value));
                    notifyMaterialChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Base color sliders
        setupColorSlider(baseColorR, baseColorRValue, (value) -> {
            DebugManager.info(DebugCategory.LOOKDEV, "Base Color R changed to: " + value);
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(value / 255f, color.y, color.z, color.w);
                DebugManager.info(DebugCategory.LOOKDEV, "Base Color override set, notifying change...");
                notifyMaterialChanged();
            } else {
                DebugManager.warn(DebugCategory.LOOKDEV, "overrideManager is null, cannot update Base Color R");
            }
        });

        setupColorSlider(baseColorG, baseColorGValue, (value) -> {
            DebugManager.info(DebugCategory.LOOKDEV, "Base Color G changed to: " + value);
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(color.x, value / 255f, color.z, color.w);
                notifyMaterialChanged();
            } else {
                DebugManager.warn(DebugCategory.LOOKDEV, "overrideManager is null, cannot update Base Color G");
            }
        });

        setupColorSlider(baseColorB, baseColorBValue, (value) -> {
            DebugManager.info(DebugCategory.LOOKDEV, "Base Color B changed to: " + value);
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(color.x, color.y, value / 255f, color.w);
                notifyMaterialChanged();
            } else {
                DebugManager.warn(DebugCategory.LOOKDEV, "overrideManager is null, cannot update Base Color B");
            }
        });

        setupColorSlider(baseColorA, baseColorAValue, (value) -> {
            DebugManager.info(DebugCategory.LOOKDEV, "Base Color A changed to: " + value);
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(color.x, color.y, color.z, value / 255f);
                notifyMaterialChanged();
            } else {
                DebugManager.warn(DebugCategory.LOOKDEV, "overrideManager is null, cannot update Base Color A");
            }
        });

        // Emissive color sliders
        setupColorSlider(emissiveR, emissiveRValue, (value) -> {
            if (overrideManager != null) {
                Vector3f color = overrideManager.getOriginalMaterial().getEmissiveFactor();
                overrideManager.setEmissiveOverride(value / 255f, color.y, color.z);
                notifyMaterialChanged();
            }
        });

        setupColorSlider(emissiveG, emissiveGValue, (value) -> {
            if (overrideManager != null) {
                Vector3f color = overrideManager.getOriginalMaterial().getEmissiveFactor();
                overrideManager.setEmissiveOverride(color.x, value / 255f, color.z);
                notifyMaterialChanged();
            }
        });

        setupColorSlider(emissiveB, emissiveBValue, (value) -> {
            if (overrideManager != null) {
                Vector3f color = overrideManager.getOriginalMaterial().getEmissiveFactor();
                overrideManager.setEmissiveOverride(color.x, color.y, value / 255f);
                notifyMaterialChanged();
            }
        });

        DebugManager.info(DebugCategory.LOOKDEV, "setupListeners: All listeners setup complete");
    }

    private void setupColorSlider(SeekBar slider, TextView valueLabel, ColorSliderCallback callback) {
        DebugManager.info(DebugCategory.LOOKDEV, "setupColorSlider called for slider: " + slider + ", valueLabel: " + valueLabel);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                DebugManager.info(DebugCategory.LOOKDEV, "onProgressChanged: progress=" + progress + ", fromUser=" + fromUser);
                if (fromUser) {
                    DebugManager.info(DebugCategory.LOOKDEV, "Calling callback with value: " + progress);
                    callback.onValueChanged(progress);
                    valueLabel.setText(String.valueOf(progress));
                } else {
                    DebugManager.info(DebugCategory.LOOKDEV, "Ignoring programmatic change");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                DebugManager.info(DebugCategory.LOOKDEV, "onStartTrackingTouch called");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                DebugManager.info(DebugCategory.LOOKDEV, "onStopTrackingTouch called");
            }
        });
        DebugManager.info(DebugCategory.LOOKDEV, "Listener set for slider");
    }

    @FunctionalInterface
    private interface ColorSliderCallback {
        void onValueChanged(int value);
    }

    private void addSectionTitle(String title) {
        var textView = new TextView(requireContext());
        textView.setText(title);
        textView.setTextSize(14);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, contentLayout.dp(12), 0, contentLayout.dp(4));
        contentLayout.addView(textView, params);
    }

    private SwitchButton addChannelToggle(String label) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        var textView = new TextView(requireContext());
        textView.setText(label);
        textView.setTextSize(12);
        var textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(textView, textParams);

        var toggle = new SwitchButton(requireContext());
        toggle.setChecked(true);
        var toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(toggle, toggleParams);

        var layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(layout, layoutParams);

        return toggle;
    }

    private TextView lastValueLabel;

    private SeekBar addParameterSlider(String label, int min, int max) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);

        var textView = new TextView(requireContext());
        textView.setText(label);
        textView.setTextSize(12);
        var textParams = new LinearLayout.LayoutParams(
                contentLayout.dp(80), ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(textView, textParams);

        var slider = new SeekBar(requireContext());
        slider.setMax(max - min);
        slider.setProgress(0);
        var sliderParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sliderParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(slider, sliderParams);

        var valueLabel = new TextView(requireContext());
        valueLabel.setText("0.00");
        valueLabel.setTextSize(12);
        var valueParams = new LinearLayout.LayoutParams(
                contentLayout.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(valueLabel, valueParams);

        var layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(layout, layoutParams);

        lastValueLabel = valueLabel;
        return slider;
    }

    private SeekBar addColorSlider(String label, int min, int max) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);

        var textView = new TextView(requireContext());
        textView.setText(label);
        textView.setTextSize(12);
        var textParams = new LinearLayout.LayoutParams(
                contentLayout.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(textView, textParams);

        var slider = new SeekBar(requireContext());
        slider.setMax(max - min);
        slider.setProgress(0);
        var sliderParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sliderParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(slider, sliderParams);

        var valueLabel = new TextView(requireContext());
        valueLabel.setText("0");
        valueLabel.setTextSize(12);
        var valueParams = new LinearLayout.LayoutParams(
                contentLayout.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(valueLabel, valueParams);

        var layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, contentLayout.dp(2), 0, contentLayout.dp(2));
        contentLayout.addView(layout, layoutParams);

        lastValueLabel = valueLabel;
        return slider;
    }

    private TextView getLastValueLabel() {
        return lastValueLabel;
    }

    private void addSpacer(int dp) {
        var spacer = new View(requireContext());
        var params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, contentLayout.dp(dp));
        contentLayout.addView(spacer, params);
    }

    /**
     * Set the material to edit
     */
    public void setMaterial(PBRMaterial material) {
        if (material == null) {
            this.overrideManager = null;
            return;
        }

        this.overrideManager = new MaterialOverrideManager(material);
        updateUIFromMaterial();
    }

    /**
     * Set the override manager (called by LookDevScreen to share the manager with PreviewFragment)
     */
    public void setOverrideManager(MaterialOverrideManager manager) {
        this.overrideManager = manager;
        updateUIFromMaterial();
    }

    /**
     * Update UI with current material parameters
     */
    private void updateUIFromMaterial() {
        if (overrideManager == null) return;

        // Check if view is created yet
        if (baseColorToggle == null) {
            // View not created yet, will be updated in onViewCreated
            return;
        }

        PBRMaterial material = overrideManager.getOriginalMaterial();

        // Update toggles (all enabled by default)
        baseColorToggle.setChecked(true);
        normalToggle.setChecked(true);
        metallicToggle.setChecked(true);
        roughnessToggle.setChecked(true);
        emissiveToggle.setChecked(true);
        aoToggle.setChecked(true);
        heightToggle.setChecked(true);

        // Update parameter sliders
        roughnessSlider.setProgress((int) (material.getRoughnessFactor() * 100));
        roughnessValue.setText(String.format("%.2f", material.getRoughnessFactor()));

        metallicSlider.setProgress((int) (material.getMetallicFactor() * 100));
        metallicValue.setText(String.format("%.2f", material.getMetallicFactor()));

        // Update base color sliders
        Vector4f baseColor = material.getBaseColor();
        baseColorR.setProgress((int) (baseColor.x * 255));
        baseColorRValue.setText(String.valueOf((int) (baseColor.x * 255)));
        baseColorG.setProgress((int) (baseColor.y * 255));
        baseColorGValue.setText(String.valueOf((int) (baseColor.y * 255)));
        baseColorB.setProgress((int) (baseColor.z * 255));
        baseColorBValue.setText(String.valueOf((int) (baseColor.z * 255)));
        baseColorA.setProgress((int) (baseColor.w * 255));
        baseColorAValue.setText(String.valueOf((int) (baseColor.w * 255)));

        // Update emissive color sliders
        Vector3f emissive = material.getEmissiveFactor();
        emissiveR.setProgress((int) (emissive.x * 255));
        emissiveRValue.setText(String.valueOf((int) (emissive.x * 255)));
        emissiveG.setProgress((int) (emissive.y * 255));
        emissiveGValue.setText(String.valueOf((int) (emissive.y * 255)));
        emissiveB.setProgress((int) (emissive.z * 255));
        emissiveBValue.setText(String.valueOf((int) (emissive.z * 255)));
    }

    /**
     * Reset all parameters to defaults
     */
    public void resetParameters() {
        if (overrideManager != null) {
            overrideManager.resetOverrides();
            updateUIFromMaterial();
            notifyMaterialChanged();
        }
    }

    /**
     * Get the effective material with overrides applied
     */
    public PBRMaterial getEffectiveMaterial() {
        return overrideManager != null ? overrideManager.getEffectiveMaterial() : null;
    }

    /**
     * Notify parent that material has changed
     */
    private void notifyMaterialChanged() {
        DebugManager.info(DebugCategory.LOOKDEV, "notifyMaterialChanged called");
        // Notify parent LookDevScreen to refresh preview
        Fragment parent = getParentFragment();
        if (parent instanceof LookDevScreen) {
            DebugManager.info(DebugCategory.LOOKDEV, "Notifying LookDevScreen.onMaterialParametersChanged()");
            ((LookDevScreen) parent).onMaterialParametersChanged();
        } else {
            DebugManager.warn(DebugCategory.LOOKDEV, "Parent is not LookDevScreen: " + (parent != null ? parent.getClass().getName() : "null"));
        }
    }

    /**
     * Add comparison controls (capture, compare)
     */
    private void addComparisonControls() {
        var context = requireContext();

        // Snapshot count display
        snapshotCountText = new TextView(context);
        snapshotCountText.setText("Snapshots: 0");
        snapshotCountText.setTextColor(0xFFCCCCCC);
        var countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMargins(0, contentLayout.dp(4), 0, contentLayout.dp(4));
        contentLayout.addView(snapshotCountText, countParams);

        // Button row
        var buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        // Capture snapshot button
        captureSnapshotButton = new Button(context);
        captureSnapshotButton.setText("Capture");
        captureSnapshotButton.setOnClickListener(v -> captureSnapshot());
        var captureParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        captureParams.setMargins(0, 0, contentLayout.dp(4), 0);
        buttonRow.addView(captureSnapshotButton, captureParams);

        // Compare button
        compareSnapshotButton = new Button(context);
        compareSnapshotButton.setText("Compare");
        compareSnapshotButton.setOnClickListener(v -> compareWithSnapshot());
        var compareParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        compareParams.setMargins(contentLayout.dp(4), 0, 0, 0);
        buttonRow.addView(compareSnapshotButton, compareParams);

        var rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, contentLayout.dp(4), 0, 0);
        contentLayout.addView(buttonRow, rowParams);
    }

    /**
     * Capture current material state as snapshot
     */
    private void captureSnapshot() {
        if (overrideManager == null) return;

        String label = "Snapshot_" + (comparisonManager.getSnapshotCount() + 1);
        comparisonManager.captureSnapshot(label, overrideManager.getCurrentOverride());
        updateSnapshotCount();
    }

    /**
     * Compare current state with latest snapshot
     */
    private void compareWithSnapshot() {
        var snapshot = comparisonManager.getLatestSnapshot();
        if (snapshot == null) {
            // TODO: Show "No snapshots" message
            return;
        }

        // Notify parent to show comparison view
        var parent = getParentFragment();
        if (parent instanceof LookDevScreen) {
            ((LookDevScreen) parent).showComparison(snapshot);
        }
    }

    /**
     * Update snapshot count display
     */
    private void updateSnapshotCount() {
        if (snapshotCountText != null) {
            snapshotCountText.setText("Snapshots: " + comparisonManager.getSnapshotCount());
        }
    }

    /**
     * Add preset controls (save, load, dropdown)
     */
    private void addPresetControls() {
        var context = requireContext();

        // Preset name input
        presetNameInput = new EditText(context);
        presetNameInput.setHint("Preset name");
        var inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, contentLayout.dp(4), 0, contentLayout.dp(4));
        contentLayout.addView(presetNameInput, inputParams);

        // Button row
        var buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        // Save button
        savePresetButton = new Button(context);
        savePresetButton.setText("Save");
        savePresetButton.setOnClickListener(v -> savePreset());
        var saveParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        saveParams.setMargins(0, 0, contentLayout.dp(4), 0);
        buttonRow.addView(savePresetButton, saveParams);

        // Load button
        loadPresetButton = new Button(context);
        loadPresetButton.setText("Load");
        loadPresetButton.setOnClickListener(v -> loadPreset());
        var loadParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        loadParams.setMargins(contentLayout.dp(4), 0, 0, 0);
        buttonRow.addView(loadPresetButton, loadParams);

        var rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, contentLayout.dp(4), 0, 0);
        contentLayout.addView(buttonRow, rowParams);
    }

    /**
     * Save current parameters as preset
     */
    private void savePreset() {
        if (overrideManager == null) return;

        String presetName = presetNameInput.getText().toString().trim();
        if (presetName.isEmpty()) {
            presetName = "Preset_" + System.currentTimeMillis();
        }

        var preset = new MaterialPreset(presetName, overrideManager.getCurrentOverride());
        if (presetManager.savePreset(preset)) {
            // TODO: Show success message
        }
    }

    /**
     * Load selected preset
     */
    private void loadPreset() {
        if (overrideManager == null) return;

        String presetName = presetNameInput.getText().toString().trim();
        if (presetName.isEmpty()) return;

        var preset = presetManager.loadPreset(presetName);
        if (preset != null) {
            overrideManager.setCurrentOverride(preset.getOverride());
            updateUIFromMaterial();
            notifyMaterialChanged();
            // TODO: Show success message
        }
    }

    /**
     * Toggle a channel by index (for keyboard shortcuts)
     * 0: BaseColor, 1: Normal, 2: Metallic, 3: Roughness, 4: Emissive, 5: AO, 6: Height
     */
    public void toggleChannel(int channelIndex) {
        SwitchButton toggle = null;
        switch (channelIndex) {
            case 0: toggle = baseColorToggle; break;
            case 1: toggle = normalToggle; break;
            case 2: toggle = metallicToggle; break;
            case 3: toggle = roughnessToggle; break;
            case 4: toggle = emissiveToggle; break;
            case 5: toggle = aoToggle; break;
            case 6: toggle = heightToggle; break;
        }

        if (toggle != null) {
            toggle.setChecked(!toggle.isChecked());
        }
    }
}
