package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.PhotonIntegration;
import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.output.MaterialOutputNode;

import java.util.*;

/**
 * Generates GLSL shader code from node graphs.
 *
 * This class converts a node graph into vertex and fragment shaders
 * that can be compiled by Photon/Oculus.
 *
 * Only available in FULL_INTEGRATION mode (when Photon is loaded).
 *
 * Features:
 * - Topological traversal of node graph
 * - GLSL code emission for each node type
 * - Automatic variable naming and dependency tracking
 * - PBR material output (albedo, metallic, roughness, normal, emissive)
 */
public class ShaderCodeGenerator {

    private final NodeGraphExecutor executor;
    private final GLSLEmitter emitter;
    private final Map<String, String> nodeVariables; // node ID -> variable name
    private int variableCounter = 0;

    public ShaderCodeGenerator(List<Node> nodes, List<NodeConnection> connections) {
        this.executor = new NodeGraphExecutor(nodes, connections);
        this.emitter = new GLSLEmitter();
        this.nodeVariables = new HashMap<>();
    }

    /**
     * Generate complete shader code (vertex + fragment)
     */
    public ShaderCode generate() {
        if (!PhotonIntegration.hasShaderSupport()) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[ShaderCodeGenerator] Shader generation not available in STANDALONE mode");
            return null;
        }

        // Check for cycles
        if (executor.hasCycles()) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[ShaderCodeGenerator] Cannot generate shader: graph contains cycles");
            return null;
        }

        // Get execution order
        List<Node> executionOrder = executor.getExecutionOrder();
        if (executionOrder.isEmpty()) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[ShaderCodeGenerator] No nodes to generate shader from");
            return null;
        }

        // Find output node
        MaterialOutputNode outputNode = findOutputNode(executionOrder);
        if (outputNode == null) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[ShaderCodeGenerator] No MaterialOutputNode found in graph");
            return null;
        }

        // Reset state
        emitter.reset();
        nodeVariables.clear();
        variableCounter = 0;

        // Generate vertex shader
        String vertexShader = generateVertexShader();

        // Generate fragment shader
        String fragmentShader = generateFragmentShader(executionOrder, outputNode);

        DebugManager.info(DebugCategory.LOOKDEV,
            "[ShaderCodeGenerator] Generated shader code (" +
            executionOrder.size() + " nodes, " +
            fragmentShader.split("\n").length + " lines)");

        return new ShaderCode(vertexShader, fragmentShader);
    }

    /**
     * Generate vertex shader (standard pass-through)
     */
    private String generateVertexShader() {
        StringBuilder sb = new StringBuilder();

        sb.append(ShaderTemplate.VERTEX_HEADER);
        sb.append("\n");
        sb.append(ShaderTemplate.VERTEX_INPUTS);
        sb.append("\n");
        sb.append(ShaderTemplate.VERTEX_OUTPUTS);
        sb.append("\n");
        sb.append(ShaderTemplate.VERTEX_MAIN);

        return sb.toString();
    }

    /**
     * Generate fragment shader from node graph
     */
    private String generateFragmentShader(List<Node> executionOrder, MaterialOutputNode outputNode) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(ShaderTemplate.FRAGMENT_HEADER);
        sb.append("\n");

        // Inputs (from vertex shader)
        sb.append(ShaderTemplate.FRAGMENT_INPUTS);
        sb.append("\n");

        // Uniforms
        sb.append(emitter.emitUniforms());
        sb.append("\n");

        // Utility functions
        sb.append(ShaderTemplate.UTILITY_FUNCTIONS);
        sb.append("\n");

        // Main function start
        sb.append("void main() {\n");

        // Generate code for each node in execution order
        for (Node node : executionOrder) {
            String nodeCode = emitter.emitNode(node, this);
            if (nodeCode != null && !nodeCode.isEmpty()) {
                sb.append(emitter.indent(nodeCode, 1));
                sb.append("\n");
            }
        }

        // Get final output values from MaterialOutputNode
        sb.append(emitter.indent("// Final material output", 1));
        sb.append("\n");
        sb.append(emitter.indent(generateMaterialOutput(outputNode), 1));
        sb.append("\n");

        // Main function end
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Generate final material output code
     */
    private String generateMaterialOutput(MaterialOutputNode outputNode) {
        StringBuilder sb = new StringBuilder();

        // Get input socket values
        String baseColor = getSocketVariable(outputNode, "Base Color", "vec3(0.8, 0.8, 0.8)");
        String metallic = getSocketVariable(outputNode, "Metallic", "0.0");
        String roughness = getSocketVariable(outputNode, "Roughness", "0.5");
        String normal = getSocketVariable(outputNode, "Normal", "vNormal");
        String emissive = getSocketVariable(outputNode, "Emissive", "vec3(0.0)");
        String alpha = getSocketVariable(outputNode, "Alpha", "1.0");

        // Simple PBR lighting calculation
        sb.append("// Simple PBR calculation\n");
        sb.append("vec3 N = normalize(").append(normal).append(");\n");
        sb.append("vec3 V = normalize(vViewDir);\n");
        sb.append("vec3 L = normalize(vec3(0.5, 1.0, 0.3)); // Directional light\n");
        sb.append("vec3 H = normalize(L + V);\n");
        sb.append("\n");
        sb.append("float NdotL = max(dot(N, L), 0.0);\n");
        sb.append("float NdotH = max(dot(N, H), 0.0);\n");
        sb.append("\n");
        sb.append("// Diffuse\n");
        sb.append("vec3 diffuse = ").append(baseColor).append(" * NdotL;\n");
        sb.append("\n");
        sb.append("// Specular (simplified)\n");
        sb.append("float spec = pow(NdotH, (1.0 - ").append(roughness).append(") * 128.0);\n");
        sb.append("vec3 specular = vec3(spec) * (1.0 - ").append(roughness).append(");\n");
        sb.append("\n");
        sb.append("// Metallic blend\n");
        sb.append("vec3 finalColor = mix(diffuse + specular * 0.3, ").append(baseColor).append(" * (diffuse + specular), ").append(metallic).append(");\n");
        sb.append("\n");
        sb.append("// Add emissive\n");
        sb.append("finalColor += ").append(emissive).append(";\n");
        sb.append("\n");
        sb.append("// Output\n");
        sb.append("fragColor = vec4(finalColor, ").append(alpha).append(");\n");

        return sb.toString();
    }

    /**
     * Get variable name for a socket, or default value if not connected
     */
    private String getSocketVariable(Node node, String socketName, String defaultValue) {
        NodeSocket socket = node.getInputByName(socketName);
        if (socket == null || !socket.isConnected()) {
            return defaultValue;
        }

        NodeSocket connectedSocket = socket.getConnectedTo();
        if (connectedSocket == null) {
            return defaultValue;
        }

        Node sourceNode = connectedSocket.getNode();
        if (sourceNode == null) {
            return defaultValue;
        }

        // Get variable name for the source node's output
        String varName = getNodeVariable(sourceNode, connectedSocket.getName());
        return varName != null ? varName : defaultValue;
    }

    /**
     * Find the MaterialOutputNode in the graph
     */
    private MaterialOutputNode findOutputNode(List<Node> nodes) {
        for (Node node : nodes) {
            if (node instanceof MaterialOutputNode) {
                return (MaterialOutputNode) node;
            }
        }
        return null;
    }

    /**
     * Get or create a variable name for a node's output
     */
    public String getNodeVariable(Node node, String outputName) {
        String key = node.getId() + "_" + outputName;
        if (!nodeVariables.containsKey(key)) {
            String varName = "node_" + variableCounter++;
            nodeVariables.put(key, varName);
        }
        return nodeVariables.get(key);
    }

    /**
     * Get variable name for a node (default output)
     */
    public String getNodeVariable(Node node) {
        if (node.getOutputs().isEmpty()) {
            return null;
        }
        return getNodeVariable(node, node.getOutputs().get(0).getName());
    }

    /**
     * Result of shader generation
     */
    public static class ShaderCode {
        public final String vertexShader;
        public final String fragmentShader;

        public ShaderCode(String vertexShader, String fragmentShader) {
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
        }

        @Override
        public String toString() {
            return "=== VERTEX SHADER ===\n" + vertexShader +
                   "\n\n=== FRAGMENT SHADER ===\n" + fragmentShader;
        }
    }
}
