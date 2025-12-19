package cn.minerealms.iic.difficulty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for smoothing difficulty values and calculating weighted medians.
 * <p>
 * This class provides two main functions:
 * <ol>
 *   <li>Exponential moving average (EMA) smoothing with rate limiting to prevent sudden difficulty spikes</li>
 *   <li>Weighted median calculation for machine tier analysis</li>
 * </ol>
 * <p>
 * The smoothing algorithm combines EMA for trend following with rate limiting to ensure
 * difficulty changes are gradual and predictable for players.
 *
 * @see IndustrialDifficultyManager
 * @see MachineScanner
 * @author ImprovedMobs Team
 */
public class DifficultySmoother {

    /**
     * Exponential moving average smoothing factor (0.0-1.0).
     * Higher values respond faster to changes.
     */
    private final float alpha;

    /**
     * Maximum difficulty change allowed per tick.
     * Prevents sudden difficulty spikes.
     */
    private final float maxDeltaPerTick;

    /**
     * Last smoothed value for tracking.
     */
    private float lastValue;

    /**
     * Creates a new difficulty smoother with the specified parameters.
     *
     * @param alpha the EMA smoothing factor (0.1-0.2 recommended)
     * @param maxDeltaPerTick the maximum change per tick (prevents spikes)
     */
    public DifficultySmoother(float alpha, float maxDeltaPerTick) {
        this.alpha = alpha;
        this.maxDeltaPerTick = maxDeltaPerTick;
    }

    /**
     * Smooths a difficulty value using EMA and rate limiting.
     * <p>
     * The algorithm:
     * <ol>
     *   <li>Apply exponential moving average to get trend value</li>
     *   <li>Limit the change to maxDeltaPerTick to prevent spikes</li>
     *   <li>Ensure result is non-negative</li>
     * </ol>
     *
     * @param prevDifficulty the previous difficulty value
     * @param targetDifficulty the target difficulty value
     * @return the smoothed difficulty value
     */
    public float smooth(float prevDifficulty, float targetDifficulty) {
        // Step 1: EMA for internal trend
        float emaValue = alpha * targetDifficulty + (1 - alpha) * prevDifficulty;

        // Step 2: Rate limiting (maxDelta)
        float diff = emaValue - prevDifficulty;
        if (Math.abs(diff) > maxDeltaPerTick) {
            emaValue = prevDifficulty + Math.signum(diff) * maxDeltaPerTick;
        }

        return Math.max(0, emaValue);
    }

    /**
     * Calculates the weighted median of a list of values.
     * <p>
     * The weighted median is the value where the cumulative weight reaches 50% of the total weight.
     * This is more robust than mean for handling outliers in machine tier distributions.
     * <p>
     * Example: If you have 10 LV machines (weight 1.0 each) and 1 IV machine (weight 1.0),
     * the weighted median will be LV, not skewed by the single high-tier machine.
     *
     * @param values the list of values (e.g., machine tiers)
     * @param weights the corresponding weights for each value
     * @return the weighted median value, or 0 if the list is empty
     */
    public static float weightedMedian(List<Integer> values, List<Double> weights) {
        if (values.isEmpty()) return 0;

        // Create nodes pairing values with weights
        List<Node> nodes = new ArrayList<>();
        double totalWeight = 0;
        for (int i = 0; i < values.size(); i++) {
            nodes.add(new Node(values.get(i), weights.get(i)));
            totalWeight += weights.get(i);
        }
        Collections.sort(nodes);

        // Find the value where cumulative weight reaches 50%
        double cumulative = 0;
        for (Node node : nodes) {
            cumulative += node.weight;
            if (cumulative >= totalWeight / 2.0) {
                return node.value;
            }
        }
        return nodes.get(nodes.size() - 1).value;
    }

    /**
     * Internal record for pairing values with weights for weighted median calculation.
     */
    private record Node(int value, double weight) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.value, o.value);
        }
    }
}
