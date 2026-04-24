package cn.minerealms.assimploader.lookdev.comparison;

import cn.minerealms.assimploader.lookdev.material.MaterialOverride;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager for before/after comparison snapshots.
 *
 * Allows capturing material states and comparing them side-by-side.
 */
public class ComparisonManager {

    private final List<ComparisonSnapshot> snapshots = new ArrayList<>();
    private int maxSnapshots = 10;

    /**
     * Capture current material state as a snapshot
     *
     * @param label Snapshot label
     * @param materialState Current material override state
     * @return Created snapshot
     */
    public ComparisonSnapshot captureSnapshot(String label, MaterialOverride materialState) {
        ComparisonSnapshot snapshot = new ComparisonSnapshot(label, materialState);
        snapshots.add(snapshot);

        // Remove oldest if exceeding limit
        while (snapshots.size() > maxSnapshots) {
            ComparisonSnapshot removed = snapshots.remove(0);
            removed.close();
        }

        return snapshot;
    }

    /**
     * Get all snapshots
     */
    public List<ComparisonSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    /**
     * Get snapshot by index
     */
    @Nullable
    public ComparisonSnapshot getSnapshot(int index) {
        if (index >= 0 && index < snapshots.size()) {
            return snapshots.get(index);
        }
        return null;
    }

    /**
     * Get most recent snapshot
     */
    @Nullable
    public ComparisonSnapshot getLatestSnapshot() {
        if (snapshots.isEmpty()) {
            return null;
        }
        return snapshots.get(snapshots.size() - 1);
    }

    /**
     * Clear all snapshots
     */
    public void clearSnapshots() {
        for (ComparisonSnapshot snapshot : snapshots) {
            snapshot.close();
        }
        snapshots.clear();
    }

    /**
     * Remove a specific snapshot
     */
    public void removeSnapshot(int index) {
        if (index >= 0 && index < snapshots.size()) {
            ComparisonSnapshot removed = snapshots.remove(index);
            removed.close();
        }
    }

    /**
     * Get number of snapshots
     */
    public int getSnapshotCount() {
        return snapshots.size();
    }

    /**
     * Set maximum number of snapshots to keep
     */
    public void setMaxSnapshots(int max) {
        this.maxSnapshots = Math.max(1, max);

        // Trim if necessary
        while (snapshots.size() > maxSnapshots) {
            ComparisonSnapshot removed = snapshots.remove(0);
            removed.close();
        }
    }

    /**
     * Release all resources
     */
    public void close() {
        clearSnapshots();
    }
}
