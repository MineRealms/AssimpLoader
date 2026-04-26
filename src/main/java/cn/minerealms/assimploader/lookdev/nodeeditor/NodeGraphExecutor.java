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
        // Clear previous evaluation state
        context.clear();

        List<OutputNode> outputNodes = new ArrayList<>();

        // Find all output nodes
        for (Node node : nodes) {
            if (node instanceof OutputNode) {
                outputNodes.add((OutputNode) node);
            }
        }

        if (outputNodes.isEmpty()) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] No output nodes found in graph");
            return outputNodes;
        }

        // Check for cycles before execution
        if (hasCycles()) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphExecutor] Graph contains cycles, cannot execute");
            return outputNodes;
        }

        // Execute graph for each output node
        for (OutputNode outputNode : outputNodes) {
            try {
                outputNode.evaluate(context);
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[NodeGraphExecutor] Executed graph, cached " +
                    context.getCachedNodeCount() + " nodes, " +
                    context.getCachedSocketCount() + " socket values");
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[NodeGraphExecutor] Failed to execute graph: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return outputNodes;
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

        // Perform DFS from each output node
        for (Node outputNode : outputNodes) {
            if (!visited.contains(outputNode)) {
                topologicalSort(outputNode, visited, visiting, sorted);
            }
        }

        // Reverse to get correct execution order
        Collections.reverse(sorted);
        return sorted;
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
        Set<Node> visited = new HashSet<>();
        Set<Node> visiting = new HashSet<>();

        for (Node node : nodes) {
            if (!visited.contains(node)) {
                if (hasCyclesDFS(node, visited, visiting)) {
                    return true;
                }
            }
        }

        return false;
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
