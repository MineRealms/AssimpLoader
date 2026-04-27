package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.OutputNode;

import java.util.*;

/**
 * Generates injectable GLSL code fragments from node graphs.
 *
 * Unlike ShaderCodeGenerator which produces complete shaders,
 * this generates fragments that can be injected into existing
 * shader pipelines (e.g., Iris/Oculus).
 *
 * Key differences from ShaderCodeGenerator:
 * - No #version directive
 * - No main() function wrapper
 * - Only declarations and computations
 * - Includes injection metadata
 */
public class GLSLFragmentGenerator {

    private final NodeGraphExecutor executor;
    private final GLSLFragmentEmitter emitter;
    private final Map<String, String> nodeVariables;
    private int variableCounter = 0;

    public GLSLFragmentGenerator(List<Node> nodes, List<NodeConnection> connections) {
        this.executor = new NodeGraphExecutor(nodes, connections);
        this.emitter = new GLSLFragmentEmitter();
        this.nodeVariables = new HashMap<>();
    }

    /**
     * Generate GLSL fragment for injection
     *
     * @param outputConfig Material output configuration
     * @param injectionConfig Injection point configuration
     * @return GLSLFragment containing code and metadata, or null if generation fails
     */
    public GLSLFragment generate(GLSLFragment.MaterialOutputConfig outputConfig,
                                  GLSLFragment.InjectionConfig injectionConfig) {
        // Check for cycles
        if (executor.hasCycles()) {
            return null;
        }

        List<Node> executionOrder = executor.getExecutionOrder();
        if (executionOrder.isEmpty()) {
            return null;
        }

        // Reset state
        emitter.reset();
        nodeVariables.clear();
        variableCounter = 0;

        // Generate uniform declarations
        String uniforms = emitter.emitUniforms();

        // Generate node computation code
        StringBuilder code = new StringBuilder();
        for (Node node : executionOrder) {
            // Skip output node - we handle it separately
            if (node instanceof OutputNode) {
                continue;
            }

            String nodeCode = emitter.emitNode(node, new ShaderCodeGeneratorAdapter(this));
            if (nodeCode != null && !nodeCode.isEmpty()) {
                code.append(nodeCode);
            }
        }

        // Generate output assignments
        String outputAssignment = generateOutputAssignment(outputConfig, executionOrder);

        // Combine all parts
        String finalCode = uniforms + code.toString() + outputAssignment;

        return new GLSLFragment(
            finalCode,
            emitter.getUsedUniforms(),
            injectionConfig != null ? injectionConfig : new GLSLFragment.InjectionConfig()
        );
    }

    /**
     * Generate output assignment statements
     */
    private String generateOutputAssignment(GLSLFragment.MaterialOutputConfig config,
                                            List<Node> executionOrder) {
        if (config == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Material output assignments\n");

        if (config.getBaseColor() != null) {
            sb.append("vec3 material_baseColor = ").append(config.getBaseColor()).append(";\n");
        }
        if (config.getMetallic() != null) {
            sb.append("float material_metallic = ").append(config.getMetallic()).append(";\n");
        }
        if (config.getRoughness() != null) {
            sb.append("float material_roughness = ").append(config.getRoughness()).append(";\n");
        }
        if (config.getNormal() != null) {
            sb.append("vec3 material_normal = ").append(config.getNormal()).append(";\n");
        }
        if (config.getEmissive() != null) {
            sb.append("vec3 material_emissive = ").append(config.getEmissive()).append(";\n");
        }
        if (config.getAlpha() != null) {
            sb.append("float material_alpha = ").append(config.getAlpha()).append(";\n");
        }

        return sb.toString();
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
     * Adapter to make GLSLFragmentGenerator compatible with GLSLEmitter
     * which expects a ShaderCodeGenerator
     */
    private static class ShaderCodeGeneratorAdapter extends ShaderCodeGenerator {
        private final GLSLFragmentGenerator fragmentGenerator;

        public ShaderCodeGeneratorAdapter(GLSLFragmentGenerator fragmentGenerator) {
            super(Collections.emptyList(), Collections.emptyList());
            this.fragmentGenerator = fragmentGenerator;
        }

        @Override
        public String getNodeVariable(Node node, String outputName) {
            return fragmentGenerator.getNodeVariable(node, outputName);
        }

        @Override
        public String getNodeVariable(Node node) {
            return fragmentGenerator.getNodeVariable(node);
        }
    }
}
