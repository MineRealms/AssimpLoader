package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.OutputNode;

import java.util.*;

/**
 * Executes a node graph to compute output values.
 *
 * Features:
 * - Topological sorting for correct execution order
 * - Cycle detection
 * - Lazy evaluation (only compute what's needed)
 * - Caching of computed values via NodeEvaluationContext
 */
public class NodeGraphExecutor {

    private List<Node> nodes;
    private List<NodeConnection> connections;
    private NodeEvaluationContext context;

    public NodeGraphExecutor(List<Node> nodes, List<NodeConnection> connections) {
        this.nodes = new ArrayList<>(nodes);
        this.connections = new ArrayList<>(connections);
        this.context = new NodeEvaluationContext();
    }

    /**
     * Execute the node graph and return all output nodes
     */
    public List<OutputNode> execute() {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeGraphExecutor] Starting graph execution with {} nodes, {} connections",
            nodes.size(), connections.size());

        return DebugManager.tryCatch("[NodeGraphExecutor] execute", () -> {
            // Clear previous evaluation state
            context.clear();
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Cleared evaluation context");

            List<OutputNode> outputNodes = new ArrayList<>();

            // Find all output nodes
            for (Node node : nodes) {
                if (node instanceof OutputNode) {
                    outputNodes.add((OutputNode) node);
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[NodeGraphExecutor] Found output node: {}", node.getClass().getSimpleName());
                }
            }

            if (outputNodes.isEmpty()) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[NodeGraphExecutor] No output nodes found in graph");
                return outputNodes;
            }

            // Check for cycles before execution
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Checking for cycles...");
            if (hasCycles()) {
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[NodeGraphExecutor] Graph contains cycles, cannot execute");
                return outputNodes;
            }
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] No cycles detected");

            // Execute graph for each output node
            for (int i = 0; i < outputNodes.size(); i++) {
                OutputNode outputNode = outputNodes.get(i);
                DebugManager.debug(DebugCategory.LOOKDEV,
                    "[NodeGraphExecutor] Evaluating output node {}/{}: {}",
                    i + 1, outputNodes.size(), outputNode.getClass().getSimpleName());

                try {
                    outputNode.evaluate(context);
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[NodeGraphExecutor] Successfully evaluated output node {}/{}",
                        i + 1, outputNodes.size());
                } catch (Exception e) {
                    DebugManager.error(DebugCategory.LOOKDEV,
                        "[NodeGraphExecutor] Failed to evaluate output node {}/{}: {}",
                        i + 1, outputNodes.size(), e.getMessage());
                    DebugManager.error(DebugCategory.LOOKDEV,
                        "[NodeGraphExecutor] Output node evaluation error", e);
                }
            }

            DebugManager.info(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Graph execution complete. Cached {} nodes, {} socket values",
                context.getCachedNodeCount(), context.getCachedSocketCount());

            return outputNodes;
        }, new ArrayList<>());
    }

    /**
     * Get the evaluation context (for inspecting cached values)
     */
    public NodeEvaluationContext getContext() {
        return context;
    }

    /**
     * Get sorted execution order (topological sort)
     */
    public List<Node> getExecutionOrder() {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeGraphExecutor] Computing execution order for {} nodes", nodes.size());

        return DebugManager.tryCatch("[NodeGraphExecutor] getExecutionOrder", () -> {
            List<Node> sorted = new ArrayList<>();
            Set<Node> visited = new HashSet<>();
            Set<Node> visiting = new HashSet<>();

            // Find all output nodes as starting points
            List<Node> outputNodes = new ArrayList<>();
            for (Node node : nodes) {
                if (node instanceof OutputNode) {
                    outputNodes.add(node);
                }
            }

            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Found {} output nodes for topological sort", outputNodes.size());

            // Perform DFS from each output node
            for (Node outputNode : outputNodes) {
                if (!visited.contains(outputNode)) {
                    topologicalSort(outputNode, visited, visiting, sorted);
                }
            }

            // Reverse to get correct execution order
            Collections.reverse(sorted);

            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Execution order computed: {} nodes in order", sorted.size());

            return sorted;
        }, new ArrayList<>());
    }

    /**
     * Topological sort using DFS
     */
    private void topologicalSort(Node node, Set<Node> visited,
                                  Set<Node> visiting, List<Node> sorted) {
        if (visiting.contains(node)) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Cycle detected in node graph");
            return;
        }

        if (visited.contains(node)) {
            return;
        }

        visiting.add(node);

        // Visit all upstream nodes
        for (NodeSocket inputSocket : node.getInputs()) {
            if (inputSocket.isConnected()) {
                NodeSocket connectedSocket = inputSocket.getConnectedTo();
                if (connectedSocket != null) {
                    Node upstreamNode = connectedSocket.getNode();
                    if (upstreamNode != null) {
                        topologicalSort(upstreamNode, visited, visiting, sorted);
                    }
                }
            }
        }

        visiting.remove(node);
        visited.add(node);
        sorted.add(node);
    }

    /**
     * Check if graph has cycles
     */
    public boolean hasCycles() {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeGraphExecutor] Checking for cycles in graph with {} nodes", nodes.size());

        return DebugManager.tryCatch("[NodeGraphExecutor] hasCycles", () -> {
            Set<Node> visited = new HashSet<>();
            Set<Node> visiting = new HashSet<>();

            for (Node node : nodes) {
                if (!visited.contains(node)) {
                    if (hasCyclesDFS(node, visited, visiting)) {
                        DebugManager.warn(DebugCategory.LOOKDEV,
                            "[NodeGraphExecutor] Cycle detected starting from node: {}",
                            node.getClass().getSimpleName());
                        return true;
                    }
                }
            }

            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] No cycles found in graph");
            return false;
        }, true); // Default to true (has cycles) on error for safety
    }

    /**
     * DFS cycle detection
     */
    private boolean hasCyclesDFS(Node node, Set<Node> visited, Set<Node> visiting) {
        if (visiting.contains(node)) {
            return true; // Cycle detected
        }

        if (visited.contains(node)) {
            return false;
        }

        visiting.add(node);

        // Check all downstream nodes
        for (NodeSocket outputSocket : node.getOutputs()) {
            if (outputSocket.isConnected()) {
                NodeSocket connectedSocket = outputSocket.getConnectedTo();
                if (connectedSocket != null) {
                    Node downstreamNode = connectedSocket.getNode();
                    if (downstreamNode != null) {
                        if (hasCyclesDFS(downstreamNode, visited, visiting)) {
                            return true;
                        }
                    }
                }
            }
        }

        visiting.remove(node);
        visited.add(node);
        return false;
    }
}
