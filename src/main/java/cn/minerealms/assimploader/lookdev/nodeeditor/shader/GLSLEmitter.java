package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.*;

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
        } else if (node instanceof MathFunctionNode) {
            return emitMathFunction((MathFunctionNode) node, generator);
        } else if (node instanceof TimeNode) {
            return emitTime((TimeNode) node, generator);
        } else if (node instanceof NoiseNode) {
            return emitNoise((NoiseNode) node, generator);
        } else if (node instanceof WorldPosNode) {
            return emitWorldPos((WorldPosNode) node, generator);
        } else if (node instanceof ViewDirNode) {
            return emitViewDir((ViewDirNode) node, generator);
        } else if (node instanceof LightDirNode) {
            return emitLightDir((LightDirNode) node, generator);
        } else if (node instanceof OutputNode) {
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
     * Emit time node
     */
    private String emitTime(TimeNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        String timeVar = generator.getNodeVariable(node, "Time");
        String sinTimeVar = generator.getNodeVariable(node, "Sin Time");
        String cosTimeVar = generator.getNodeVariable(node, "Cos Time");

        // Declare uniform if not already declared
        if (!declaredUniforms.contains("uTime")) {
            declaredUniforms.add("uTime");
        }

        sb.append("// Time Node\n");
        sb.append("float ").append(timeVar).append(" = uTime;\n");
        sb.append("float ").append(sinTimeVar).append(" = sin(uTime);\n");
        sb.append("float ").append(cosTimeVar).append(" = cos(uTime);\n");

        return sb.toString();
    }

    /**
     * Emit noise node
     */
    private String emitNoise(NoiseNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        String posVar = getInputVariable(node, "Position", generator, "vec3(vTexCoord, 0.0)");
        String scaleVar = getInputVariable(node, "Scale", generator, "1.0");
        String valueVar = generator.getNodeVariable(node, "Value");
        String colorVar = generator.getNodeVariable(node, "Color");

        sb.append("// Noise Node (").append(node.getNoiseType().getDisplayName()).append(")\n");

        // Generate noise function call based on type
        String noiseFunc = switch (node.getNoiseType()) {
            case PERLIN -> "perlinNoise";
            case SIMPLEX -> "simplexNoise";
            case WORLEY -> "worleyNoise";
        };

        sb.append("float ").append(valueVar).append(" = ").append(noiseFunc)
          .append("(").append(posVar).append(" * ").append(scaleVar).append(");\n");
        sb.append("vec3 ").append(colorVar).append(" = vec3(").append(valueVar).append(");\n");

        return sb.toString();
    }

    /**
     * Emit world position node
     */
    private String emitWorldPos(WorldPosNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        String posVar = generator.getNodeVariable(node, "Position");
        String xVar = generator.getNodeVariable(node, "X");
        String yVar = generator.getNodeVariable(node, "Y");
        String zVar = generator.getNodeVariable(node, "Z");

        // Declare uniform if not already declared
        if (!declaredUniforms.contains("uWorldPos")) {
            declaredUniforms.add("uWorldPos");
        }

        sb.append("// World Position Node\n");
        sb.append("vec3 ").append(posVar).append(" = vWorldPos;\n");
        sb.append("float ").append(xVar).append(" = vWorldPos.x;\n");
        sb.append("float ").append(yVar).append(" = vWorldPos.y;\n");
        sb.append("float ").append(zVar).append(" = vWorldPos.z;\n");

        return sb.toString();
    }

    /**
     * Emit view direction node
     */
    private String emitViewDir(ViewDirNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        String dirVar = generator.getNodeVariable(node, "Direction");
        String xVar = generator.getNodeVariable(node, "X");
        String yVar = generator.getNodeVariable(node, "Y");
        String zVar = generator.getNodeVariable(node, "Z");

        sb.append("// View Direction Node\n");
        sb.append("vec3 ").append(dirVar).append(" = normalize(vViewDir);\n");
        sb.append("float ").append(xVar).append(" = ").append(dirVar).append(".x;\n");
        sb.append("float ").append(yVar).append(" = ").append(dirVar).append(".y;\n");
        sb.append("float ").append(zVar).append(" = ").append(dirVar).append(".z;\n");

        return sb.toString();
    }

    /**
     * Emit light direction node
     */
    private String emitLightDir(LightDirNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();

        String dirVar = generator.getNodeVariable(node, "Direction");
        String xVar = generator.getNodeVariable(node, "X");
        String yVar = generator.getNodeVariable(node, "Y");
        String zVar = generator.getNodeVariable(node, "Z");

        // Declare uniform if not already declared
        if (!declaredUniforms.contains("uLightDir")) {
            declaredUniforms.add("uLightDir");
        }

        sb.append("// Light Direction Node\n");
        sb.append("vec3 ").append(dirVar).append(" = normalize(uLightDir);\n");
        sb.append("float ").append(xVar).append(" = ").append(dirVar).append(".x;\n");
        sb.append("float ").append(yVar).append(" = ").append(dirVar).append(".y;\n");
        sb.append("float ").append(zVar).append(" = ").append(dirVar).append(".z;\n");

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

        // Shader-specific uniforms
        if (declaredUniforms.contains("uTime")) {
            sb.append("uniform float uTime;\n");
        }
        if (declaredUniforms.contains("uWorldPos")) {
            sb.append("uniform vec3 uWorldPos;\n");
        }
        if (declaredUniforms.contains("uLightDir")) {
            sb.append("uniform vec3 uLightDir;\n");
        }
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
     * Emit math function node
     */
    private String emitMathFunction(MathFunctionNode node, ShaderCodeGenerator generator) {
        StringBuilder sb = new StringBuilder();
        String resultVar = generator.getNodeVariable(node);
        MathFunctionNode.Function function = node.getFunction();

        sb.append("// Math Function: ").append(function.getDisplayName()).append("\n");

        switch (function) {
            // Trigonometric
            case SIN:
                String sinInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = sin(").append(sinInput).append(");\n");
                break;

            case COS:
                String cosInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = cos(").append(cosInput).append(");\n");
                break;

            case TAN:
                String tanInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = tan(").append(tanInput).append(");\n");
                break;

            case ASIN:
                String asinInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = asin(").append(asinInput).append(");\n");
                break;

            case ACOS:
                String acosInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = acos(").append(acosInput).append(");\n");
                break;

            case ATAN:
                String atanInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = atan(").append(atanInput).append(");\n");
                break;

            // Exponential & Logarithmic
            case SQRT:
                String sqrtInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = sqrt(").append(sqrtInput).append(");\n");
                break;

            case EXP:
                String expInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = exp(").append(expInput).append(");\n");
                break;

            case LOG:
                String logInput = getInputVariable(node, "Value", generator, "1.0");
                sb.append("float ").append(resultVar).append(" = log(").append(logInput).append(");\n");
                break;

            case POW:
                String powA = getInputVariable(node, "Value", generator, "1.0");
                String powB = getInputVariable(node, "B", generator, "1.0");
                sb.append("float ").append(resultVar).append(" = pow(").append(powA).append(", ").append(powB).append(");\n");
                break;

            // Rounding
            case ABS:
                String absInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = abs(").append(absInput).append(");\n");
                break;

            case SIGN:
                String signInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = sign(").append(signInput).append(");\n");
                break;

            case FLOOR:
                String floorInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = floor(").append(floorInput).append(");\n");
                break;

            case CEIL:
                String ceilInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = ceil(").append(ceilInput).append(");\n");
                break;

            case ROUND:
                String roundInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = round(").append(roundInput).append(");\n");
                break;

            case FRACT:
                String fractInput = getInputVariable(node, "Value", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = fract(").append(fractInput).append(");\n");
                break;

            // Comparison
            case MIN:
                String minA = getInputVariable(node, "Value", generator, "0.0");
                String minB = getInputVariable(node, "B", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = min(").append(minA).append(", ").append(minB).append(");\n");
                break;

            case MAX:
                String maxA = getInputVariable(node, "Value", generator, "0.0");
                String maxB = getInputVariable(node, "B", generator, "0.0");
                sb.append("float ").append(resultVar).append(" = max(").append(maxA).append(", ").append(maxB).append(");\n");
                break;

            case CLAMP:
                String clampValue = getInputVariable(node, "Value", generator, "0.0");
                String clampMin = getInputVariable(node, "Min", generator, "0.0");
                String clampMax = getInputVariable(node, "Max", generator, "1.0");
                sb.append("float ").append(resultVar).append(" = clamp(").append(clampValue).append(", ").append(clampMin).append(", ").append(clampMax).append(");\n");
                break;

            // Interpolation
            case LERP:
                String lerpA = getInputVariable(node, "Value", generator, "0.0");
                String lerpB = getInputVariable(node, "B", generator, "1.0");
                String lerpT = getInputVariable(node, "Max", generator, "0.5");
                sb.append("float ").append(resultVar).append(" = mix(").append(lerpA).append(", ").append(lerpB).append(", ").append(lerpT).append(");\n");
                break;

            case SMOOTHSTEP:
                String smoothEdge0 = getInputVariable(node, "Min", generator, "0.0");
                String smoothEdge1 = getInputVariable(node, "Max", generator, "1.0");
                String smoothX = getInputVariable(node, "Value", generator, "0.5");
                sb.append("float ").append(resultVar).append(" = smoothstep(").append(smoothEdge0).append(", ").append(smoothEdge1).append(", ").append(smoothX).append(");\n");
                break;

            // Modulo
            case MOD:
                String modA = getInputVariable(node, "Value", generator, "1.0");
                String modB = getInputVariable(node, "B", generator, "1.0");
                sb.append("float ").append(resultVar).append(" = mod(").append(modA).append(", ").append(modB).append(");\n");
                break;

            default:
                sb.append("float ").append(resultVar).append(" = 0.0; // Unsupported function: ").append(function).append("\n");
                break;
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
