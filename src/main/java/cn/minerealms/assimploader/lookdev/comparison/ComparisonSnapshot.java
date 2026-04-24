package cn.minerealms.assimploader.lookdev.comparison;

import cn.minerealms.assimploader.lookdev.material.MaterialOverride;
import com.mojang.blaze3d.platform.NativeImage;

import javax.annotation.Nullable;

/**
 * Snapshot of material state for before/after comparison.
 *
 * Stores:
 * - Material override state
 * - Screenshot image (optional)
 * - Timestamp
 */
public class ComparisonSnapshot {

    private final MaterialOverride materialState;
    private final long timestamp;
    private final String label;

    @Nullable
    private NativeImage screenshot;

    /**
     * Create a snapshot with material state
     *
     * @param label Snapshot label (e.g., "Before", "After")
     * @param materialState Material override state
     */
    public ComparisonSnapshot(String label, MaterialOverride materialState) {
        this.label = label;
        this.materialState = materialState.copy();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Set screenshot image
     */
    public void setScreenshot(NativeImage screenshot) {
        this.screenshot = screenshot;
    }

    /**
     * Get screenshot image
     */
    @Nullable
    public NativeImage getScreenshot() {
        return screenshot;
    }

    /**
     * Get material state
     */
    public MaterialOverride getMaterialState() {
        return materialState;
    }

    /**
     * Get snapshot label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Get timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Release resources
     */
    public void close() {
        if (screenshot != null) {
            screenshot.close();
            screenshot = null;
        }
    }
}
