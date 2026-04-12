package io.github.flemmli97.improvedmobs.industrial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DifficultySmoother {

    private final float alpha; // EMA smoothing factor (0.1 - 0.2)
    private final float maxDeltaPerTick;
    private float lastValue;

    public DifficultySmoother(float alpha, float maxDeltaPerTick) {
        this.alpha = alpha;
        this.maxDeltaPerTick = maxDeltaPerTick;
    }

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

    public static float weightedMedian(List<Integer> values, List<Double> weights) {
        if (values.isEmpty()) return 0;
        
        // Simple median implementation for now as a baseline
        // For weighted median, we sort values and find the cumulative weight point
        List<Node> nodes = new ArrayList<>();
        double totalWeight = 0;
        for (int i = 0; i < values.size(); i++) {
            nodes.add(new Node(values.get(i), weights.get(i)));
            totalWeight += weights.get(i);
        }
        Collections.sort(nodes);
        
        double cumulative = 0;
        for (Node node : nodes) {
            cumulative += node.weight;
            if (cumulative >= totalWeight / 2.0) {
                return node.value;
            }
        }
        return nodes.get(nodes.size() - 1).value;
    }

    private record Node(int value, double weight) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.value, o.value);
        }
    }
}
