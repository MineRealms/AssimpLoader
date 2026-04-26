package cn.minerealms.assimploader.lookdev.nodeeditor;

import java.util.HashMap;
import java.util.Map;

/**
 * Context for node graph evaluation.
 *
 * Stores intermediate values during graph execution to:
 * - Cache computed values (avoid re-computation)
 * - Pass data between nodes
 * - Track evaluation state
 *
 * Each execution creates a fresh context to ensure clean state.
 */
public class NodeEvaluationContext {

    // Cache of computed socket values: socket -> value
    private final Map<NodeSocket, Object> socketValues = new HashMap<>();

    // Cache of computed node results: node -> result
    private final Map<Node, Object> nodeResults = new HashMap<>();

    // Track which nodes are currently being evaluated (for cycle detection)
    private final Map<Node, Boolean> evaluating = new HashMap<>();

    /**
     * Get cached value for a socket, or null if not computed yet
     */
    public Object getSocketValue(NodeSocket socket) {
        return socketValues.get(socket);
    }

    /**
     * Cache a computed socket value
     */
    public void setSocketValue(NodeSocket socket, Object value) {
        socketValues.put(socket, value);
    }

    /**
     * Check if socket value has been computed
     */
    public boolean hasSocketValue(NodeSocket socket) {
        return socketValues.containsKey(socket);
    }

    /**
     * Get cached result for a node, or null if not computed yet
     */
    public Object getNodeResult(Node node) {
        return nodeResults.get(node);
    }

    /**
     * Cache a computed node result
     */
    public void setNodeResult(Node node, Object result) {
        nodeResults.put(node, result);
    }

    /**
     * Check if node has been evaluated
     */
    public boolean hasNodeResult(Node node) {
        return nodeResults.containsKey(node);
    }

    /**
     * Mark node as currently being evaluated
     */
    public void beginEvaluating(Node node) {
        evaluating.put(node, true);
    }

    /**
     * Mark node evaluation as complete
     */
    public void endEvaluating(Node node) {
        evaluating.remove(node);
    }

    /**
     * Check if node is currently being evaluated (cycle detection)
     */
    public boolean isEvaluating(Node node) {
        return evaluating.containsKey(node);
    }

    /**
     * Clear all cached values
     */
    public void clear() {
        socketValues.clear();
        nodeResults.clear();
        evaluating.clear();
    }

    /**
     * Get number of cached socket values
     */
    public int getCachedSocketCount() {
        return socketValues.size();
    }

    /**
     * Get number of cached node results
     */
    public int getCachedNodeCount() {
        return nodeResults.size();
    }
}
