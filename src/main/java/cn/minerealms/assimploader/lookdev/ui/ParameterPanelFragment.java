package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.lookdev.material.MaterialOverrideManager;
import cn.minerealms.assimploader.material.PBRMaterial;
import icyllis.modernui.R;
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

import javax.annotation.Nonnull;
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
    private ScrollView scrollView;
    private LinearLayout contentLayout;

    // Channel toggles
    private Switch baseColorToggle;
    private Switch normalToggle;
    private Switch metallicToggle;
    private Switch roughnessToggle;
    private Switch emissiveToggle;
    private Switch aoToggle;
    private Switch heightToggle;

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

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();

        // Root scroll view
        scrollView = new ScrollView(context);
        scrollView.setBackground(new ColorDrawable(0xFF2B2B2B));

        // Content layout
        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(contentLayout.dp(8), contentLayout.dp(8),
                contentLayout.dp(8), contentLayout.dp(8));

        // Title
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

        // Parameter sliders section
        addSectionTitle("Parameters");
        roughnessSlider = addParameterSlider("Roughness", 0, 100);
        roughnessValue = getLastValueLabel();
        metallicSlider = addParameterSlider("Metallic", 0, 100);
        metallicValue = getLastValueLabel();

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

        // Emissive color section
        addSectionTitle("Emissive Color");
        emissiveR = addColorSlider("R", 0, 255);
        emissiveRValue = getLastValueLabel();
        emissiveG = addColorSlider("G", 0, 255);
        emissiveGValue = getLastValueLabel();
        emissiveB = addColorSlider("B", 0, 255);
        emissiveBValue = getLastValueLabel();

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

        scrollView.addView(contentLayout);
        return scrollView;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupListeners();
    }

    private void setupListeners() {
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
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(value / 255f, color.y, color.z, color.w);
                notifyMaterialChanged();
            }
        });

        setupColorSlider(baseColorG, baseColorGValue, (value) -> {
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(color.x, value / 255f, color.z, color.w);
                notifyMaterialChanged();
            }
        });

        setupColorSlider(baseColorB, baseColorBValue, (value) -> {
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(color.x, color.y, value / 255f, color.w);
                notifyMaterialChanged();
            }
        });

        setupColorSlider(baseColorA, baseColorAValue, (value) -> {
            if (overrideManager != null) {
                Vector4f color = overrideManager.getOriginalMaterial().getBaseColor();
                overrideManager.setBaseColorOverride(color.x, color.y, color.z, value / 255f);
                notifyMaterialChanged();
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
    }

    private void setupColorSlider(SeekBar slider, TextView valueLabel, ColorSliderCallback callback) {
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    callback.onValueChanged(progress);
                    valueLabel.setText(String.valueOf(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
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

    private Switch addChannelToggle(String label) {
        var layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        var textView = new TextView(requireContext());
        textView.setText(label);
        textView.setTextSize(12);
        var textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.gravity = Gravity.CENTER_VERTICAL;
        layout.addView(textView, textParams);

        var toggle = new Switch(requireContext());
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

        var slider = new SeekBar(requireContext(), null, null, R.style.Widget_Material3_SeekBar_Slider);
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

        var slider = new SeekBar(requireContext(), null, null, R.style.Widget_Material3_SeekBar_Slider);
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
     * Update UI with current material parameters
     */
    private void updateUIFromMaterial() {
        if (overrideManager == null) return;

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
        // TODO: Notify PreviewFragment to re-render
    }
}
