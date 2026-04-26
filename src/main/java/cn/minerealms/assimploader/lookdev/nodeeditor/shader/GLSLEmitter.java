package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.output.*;

import java.util.*;

/**
 * Emits GLSL code for individual nodes.
 *
 * This class handles the conversion of each node type into
 * corresponding GLSL code.
 */
public class GLSLEmitter {

    private final Set<String> declaredUniforms;
    private final Set<String> declaredTextures;
    private int textureUnitCounter = 0;

    public GLSLEmitter() {
        this.declaredUniforms = new HashSet<>();
        this.declaredTextures = new HashSet<>();
    }

    /**
     * Reset emitter state
     */
    public void reset() {
        declaredUniforms.clear();
        declaredTextures.clear();
        textureUnitCounter = 0;
    }

    /**
     * Emit GLSL code for a node
     */
    public String emitNode(Node node, ShaderCodeGenerator generator) {
        if (node instanceof TextureSampleNode) {
            return emitTextureSample((TextureSampleNode) node, generator);
        } else if (node instanceof ValueNode) {
            return emitValue((ValueNode) node, generator);
        } else if (node instanceof ColorNode) {
            return emitColor((ColorNode) node, generator);
        } else if (node instanceof UVCoordinateNode) {
            return emitUVCoordinate((UVCoordinateNode) node, generator);
        } else if (node instanceof MultiplyNode) {
            return emitMultiply((MultiplyNode) node, generator);
        } else if (node instanceof AddNode) {
            return emitAdd((AddNode) node, generator);
        } else if (node instanceof LerpNode) {
            return emitLerp((LerpNode) node, generator);
        } else if (node instanceof NormalMapNode) {
            return emitNormalMap((NormalMapNode) node, generator);
        } else if (node instanceof MaterialOutputNode) {
            // Output node is handled separately
            return "";
        }

        return "// Unknown node type: " + node.getClass().getSimpleName() + "\n";
    }

    /**
     * Emit texture sample node
     */
    private String emitTextureSample(TextureSampleNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        // Get texture uniform name
        String textureName = "texture_" + node.getId().replace("-", "_");
        if (!declaredTextures.contains(textureName)) {
            declaredTextures.add(textureName);
        }

        // Get UV input
        String uvVar = getInputVariable(node, "UV", generator, "vTexCoord");

        // Sample texture
        String colorVar = generator.getNodeVariable(node, "Color");
        String rgbVar = generator.getNodeVariable(node, "RGB");
        String alphaVar = generator.getNodeVariable(node, "Alpha");

        sb.append("// Texture Sample: ").append(node.getTitle()).append("\n");
        sb.append("vec4 ").append(colorVar).append(" = texture(").append(textureName).append(", ").append(uvVar).append(");\n");
        sb.append("vec3 ").append(rgbVar).append(" = ").append(colorVar).append(".rgb;\n");
        sb.append("float ").append(alphaVar).append(" = ").append(colorVar).append(".a;\n");

        return sb.toString();
    }

    /**
     * Emit value node
     */
    private String emitValue(ValueNode node, ShaderCodeGenerator generator) {
        String varName = generator.getNodeVariable(node);
        float value = node.getValue();
        return "float " + varName + " = " + formatFloat(value) + ";\n";
    }

    /**
     * Emit color node
     */
    private String emitColor(ColorNode node, ShaderCodeGenerator generator) {
        String varName = generator.getNodeVariable(node);
        float[] color = node.getColor();
        return "vec3 " + varName + " = vec3(" +
               formatFloat(color[0]) + ", " +
               formatFloat(color[1]) + ", " +
               formatFloat(color[2]) + ");\n";
    }

    /**
     * Emit UV coordinate node
     */
    private String emitUVCoordinate(UVCoordinateNode node, ShaderCodeGenerator generator) {
        String varName = generator.getNodeVariable(node);
        return "vec2 " + varName + " = vTexCoord;\n";
    }

    /**
     * Emit multiply node
     */
    private String emitMultiply(MultiplyNode node, ShaderCodeGenerator generator) {
        String aVar = getInputVariable(node, "A", generator, "1.0");
        String bVar = getInputVariable(node, "B", generator, "1.0");
        String resultVar = generator.getNodeVariable(node);

        // Determine output type based on inputs
        String type = determineType(node, "A", "B", generator);

        return type + " " + resultVar + " = " + aVar + " * " + bVar + ";\n";
    }

    /**
     * Emit add node
     */
    private String emitAdd(AddNode node, ShaderCodeGenerator generator) {
        String aVar = getInputVariable(node, "A", generator, "0.0");
        String bVar = getInputVariable(node, "B", generator, "0.0");
        String resultVar = generator.getNodeVariable(node);

        String type = determineType(node, "A", "B", generator);

        return type + " " + resultVar + " = " + aVar + " + " + bVar + ";\n";
    }

    /**
     * Emit lerp node
     */
    private String emitLerp(LerpNode node, ShaderCodeGenerator generator) {
        String aVar = getInputVariable(node, "A", generator, "0.0");
        String bVar = getInputVariable(node, "B", generator, "1.0");
        String tVar = getInputVariable(node, "T", generator, "0.5");
        String resultVar = generator.getNodeVariable(node);

        String type = determineType(node, "A", "B", generator);

        return type + " " + resultVar + " = mix(" + aVar + ", " + bVar + ", " + tVar + ");\n";
    }

    /**
     * Emit normal map node
     */
    private String emitNormalMap(NormalMapNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        String normalMapVar = getInputVariable(node, "Normal Map", generator, "vec3(0.5, 0.5, 1.0)");
        String strengthVar = getInputVariable(node, "Strength", generator, "1.0");
        String resultVar = generator.getNodeVariable(node);

        sb.append("// Normal Map\n");
        sb.append("vec3 ").append(resultVar).append("_raw = ").append(normalMapVar).append(" * 2.0 - 1.0;\n");
        sb.append("vec3 ").append(resultVar).append(" = normalize(mix(vec3(0.0, 0.0, 1.0), ")
          .append(resultVar).append("_raw, ").append(strengthVar).append("));\n");

        return sb.toString();
    }

    /**
     * Get input variable name or default value
     */
    private String getInputVariable(Node node, String inputName, ShaderCodeGenerator generator, String defaultValue) {
        NodeSocket input = node.getInputByName(inputName);
        if (input == null || !input.isConnected()) {
            return defaultValue;
        }

        NodeSocket connectedSocket = input.getConnectedTo();
        if (connectedSocket == null) {
            return defaultValue;
        }

        Node sourceNode = connectedSocket.getNode();
        if (sourceNode == null) {
            return defaultValue;
        }

        return generator.getNodeVariable(sourceNode, connectedSocket.getName());
    }

    /**
     * Determine GLSL type for operation result
     */
    private String determineType(Node node, String inputA, String inputB, ShaderCodeGenerator generator) {
        // Simple heuristic: check if inputs are connected and infer type
        NodeSocket socketA = node.getInputByName(inputA);
        NodeSocket socketB = node.getInputByName(inputB);

        // Default to float
        if (socketA == null || socketB == null) {
            return "float";
        }

        // If either is connected to a vec3 source, result is vec3
        if (isVectorSocket(socketA) || isVectorSocket(socketB)) {
            return "vec3";
        }

        return "float";
    }

    /**
     * Check if socket is connected to a vector source
     */
    private boolean isVectorSocket(NodeSocket socket) {
        if (!socket.isConnected()) {
            return false;
        }

        NodeSocket connected = socket.getConnectedTo();
        if (connected == null) {
            return false;
        }

        Node sourceNode = connected.getNode();
        if (sourceNode == null) {
            return false;
        }

        // Check node type
        return sourceNode instanceof ColorNode ||
               sourceNode instanceof TextureSampleNode && connected.getName().equals("RGB") ||
               sourceNode instanceof NormalMapNode;
    }

    /**
     * Emit uniform declarations
     */
    public String emitUniforms() {
        StringBuilder sb = new StringBuilder();

        sb.append("// Uniforms\n");
        sb.append("uniform mat4 uModelMatrix;\n");
        sb.append("uniform mat4 uViewMatrix;\n");
        sb.append("uniform mat4 uProjectionMatrix;\n");
        sb.append("\n");

        // Texture uniforms
        if (!declaredTextures.isEmpty()) {
            sb.append("// Textures\n");
            for (String textureName : declaredTextures) {
                sb.append("uniform sampler2D ").append(textureName).append(";\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Indent code by given level
     */
    public String indent(String code, int level) {
        String indentation = "    ".repeat(level);
        String[] lines = code.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                sb.append(indentation).append(line).append("\n");
            } else {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Format float value for GLSL
     */
    private String formatFloat(float value) {
        String str = String.format(Locale.US, "%.6f", value);
        // Remove trailing zeros
        str = str.replaceAll("0+$", "");
        // Ensure at least one decimal place
        if (!str.contains(".")) {
            str += ".0";
        } else if (str.endsWith(".")) {
            str += "0";
        }
        return str;
    }
}
