package cn.minerealms.assimploader.lookdev.nodeeditor.serialization;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.output.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility.*;
import com.google.gson.*;

import java.util.*;

/**
 * Deserializes node graphs from JSON format.
 *
 * Handles:
 * - Version checking
 * - Node creation from type strings
 * - Parameter restoration
 * - Connection rebuilding
 * - Error handling for invalid data
 */
public class MaterialDeserializer {

    private static final String CURRENT_VERSION = "1.0";
    private static final Gson gson = new Gson();

    /**
     * Deserialize result
     */
    public static class DeserializeResult {
        public String materialName;
        public List<Node> nodes;
        public List<NodeConnection> connections;
        public String error;

        public boolean isSuccess() {
            return error == null;
        }
    }

    /**
     * Deserialize node graph from JSON string
     */
    public static DeserializeResult deserialize(String json) {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[MaterialDeserializer] Starting deserialization, JSON length: {}", json.length());

        DeserializeResult result = new DeserializeResult();
        result.nodes = new ArrayList<>();
        result.connections = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Check version
            String version = root.get("version").getAsString();
            if (!version.equals(CURRENT_VERSION)) {
                result.error = "Unsupported version: " + version + " (expected " + CURRENT_VERSION + ")";
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[MaterialDeserializer] {}", result.error);
                return result;
            }

            // Get material name
            result.materialName = root.get("name").getAsString();
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialDeserializer] Material name: {}", result.materialName);

            // Deserialize nodes
            Map<String, Node> nodeMap = deserializeNodes(root.getAsJsonArray("nodes"), result);

            // Deserialize connections
            deserializeConnections(root.getAsJsonArray("connections"), nodeMap, result);

            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialDeserializer] Successfully deserialized: {} ({} nodes, {} connections)",
                result.materialName, result.nodes.size(), result.connections.size());

        } catch (Exception e) {
            result.error = "Deserialization error: " + e.getMessage();
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialDeserializer] {}", result.error, e);
        }

        return result;
    }

    /**
     * Deserialize nodes array
     */
    private static Map<String, Node> deserializeNodes(JsonArray nodesArray, DeserializeResult result) {
        Map<String, Node> nodeMap = new HashMap<>();

        for (JsonElement element : nodesArray) {
            JsonObject nodeObj = element.getAsJsonObject();

            String id = nodeObj.get("id").getAsString();
            String type = nodeObj.get("type").getAsString();

            // Create node
            Node node = createNode(type);
            if (node == null) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[MaterialDeserializer] Unknown node type: " + type);
                continue;
            }

            // Set position
            JsonObject position = nodeObj.getAsJsonObject("position");
            node.setPosition(
                position.get("x").getAsFloat(),
                position.get("y").getAsFloat()
            );

            // Restore parameters
            if (nodeObj.has("parameters")) {
                restoreNodeParameters(node, nodeObj.getAsJsonObject("parameters"));
            }

            nodeMap.put(id, node);
            result.nodes.add(node);
        }

        return nodeMap;
    }

    /**
     * Create node from type string
     */
    private static Node createNode(String type) {
        return switch (type) {
            case "TEXTURE_SAMPLE" -> new TextureSampleNode();
            case "VALUE" -> new ValueNode();
            case "COLOR" -> new ColorNode();
            case "UV_COORDINATE" -> new UVCoordinateNode();
            case "MULTIPLY" -> new MultiplyNode();
            case "ADD" -> new AddNode();
            case "LERP" -> new LerpNode();
            case "NORMAL_MAP" -> new NormalMapNode();
            case "MATERIAL_OUTPUT" -> new MaterialOutputNode();
            default -> null;
        };
    }

    /**
     * Restore node-specific parameters
     */
    private static void restoreNodeParameters(Node node, JsonObject params) {
        if (node instanceof TextureSampleNode) {
            TextureSampleNode n = (TextureSampleNode) node;
            if (params.has("texturePath")) {
                n.setTexturePath(params.get("texturePath").getAsString());
            }
            if (params.has("filterMode")) {
                n.setFilterMode(TextureSampleNode.FilterMode.valueOf(params.get("filterMode").getAsString()));
            }
            if (params.has("wrapMode")) {
                n.setWrapMode(TextureSampleNode.WrapMode.valueOf(params.get("wrapMode").getAsString()));
            }

        } else if (node instanceof ValueNode) {
            ValueNode n = (ValueNode) node;
            if (params.has("value")) {
                n.setValue(params.get("value").getAsFloat());
            }

        } else if (node instanceof ColorNode) {
            ColorNode n = (ColorNode) node;
            if (params.has("r") && params.has("g") && params.has("b") && params.has("a")) {
                n.setColor(
                    params.get("r").getAsFloat(),
                    params.get("g").getAsFloat(),
                    params.get("b").getAsFloat(),
                    params.get("a").getAsFloat()
                );
            }

        } else if (node instanceof MaterialOutputNode) {
            MaterialOutputNode n = (MaterialOutputNode) node;
            if (params.has("blendMode")) {
                n.setBlendMode(MaterialOutputNode.BlendMode.valueOf(params.get("blendMode").getAsString()));
            }
        }
    }

    /**
     * Deserialize connections array
     */
    private static void deserializeConnections(JsonArray connectionsArray, Map<String, Node> nodeMap, DeserializeResult result) {
        for (JsonElement element : connectionsArray) {
            JsonObject connObj = element.getAsJsonObject();

            // Get from socket
            JsonObject fromObj = connObj.getAsJsonObject("from");
            String fromNodeId = fromObj.get("node").getAsString();
            String fromSocketName = fromObj.get("socket").getAsString();

            // Get to socket
            JsonObject toObj = connObj.getAsJsonObject("to");
            String toNodeId = toObj.get("node").getAsString();
            String toSocketName = toObj.get("socket").getAsString();

            // Find nodes
            Node fromNode = nodeMap.get(fromNodeId);
            Node toNode = nodeMap.get(toNodeId);

            if (fromNode == null || toNode == null) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[MaterialDeserializer] Connection references missing node");
                continue;
            }

            // Find sockets
            NodeSocket fromSocket = fromNode.getOutputByName(fromSocketName);
            NodeSocket toSocket = toNode.getInputByName(toSocketName);

            if (fromSocket == null || toSocket == null) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[MaterialDeserializer] Connection references missing socket");
                continue;
            }

            // Create connection
            NodeConnection connection = new NodeConnection(fromSocket, toSocket);
            result.connections.add(connection);

            // Connect sockets
            fromSocket.connectTo(toSocket);
        }
    }

    /**
     * Validate JSON format
     */
    public static boolean validate(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Check required fields
            if (!root.has("version") || !root.has("name") ||
                !root.has("nodes") || !root.has("connections")) {
                return false;
            }

            // Check version
            String version = root.get("version").getAsString();
            if (!version.equals(CURRENT_VERSION)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get material name from JSON without full deserialization
     */
    public static String getMaterialName(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.get("name").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get version from JSON
     */
    public static String getVersion(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.get("version").getAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
