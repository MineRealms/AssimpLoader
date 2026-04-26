package cn.minerealms.assimploader.lookdev.nodeeditor.serialization;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import com.google.gson.*;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.*;
import java.util.*;

/**
 * Node Graph Serializer
 *
 * Serializes and deserializes node graphs to/from JSON.
 *
 * Features:
 * - Save/load complete node graphs
 * - Preserve node positions and properties
 * - Maintain connections between nodes
 * - Support for all node types
 * - Human-readable JSON format
 *
 * JSON Format:
 * {
 *   "version": "1.0",
 *   "nodes": [...],
 *   "connections": [...]
 * }
 */
public class NodeGraphSerializer {

    private static final String VERSION = "1.0";
    private final Gson gson;

    public NodeGraphSerializer() {
        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        builder.registerTypeAdapter(Vector3f.class, new Vector3fAdapter());
        builder.registerTypeAdapter(Vector4f.class, new Vector4fAdapter());
        this.gson = builder.create();
    }

    /**
     * Serialize node graph to JSON string
     */
    public String serialize(List<Node> nodes, List<NodeConnection> connections) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);

            // Serialize nodes
            JsonArray nodesArray = new JsonArray();
            Map<Node, String> nodeIdMap = new HashMap<>();
            int nodeId = 0;

            for (Node node : nodes) {
                String id = "node_" + nodeId++;
                nodeIdMap.put(node, id);

                JsonObject nodeObj = serializeNode(node, id);
                nodesArray.add(nodeObj);
            }
            root.add("nodes", nodesArray);

            // Serialize connections
            JsonArray connectionsArray = new JsonArray();
            for (NodeConnection conn : connections) {
                JsonObject connObj = serializeConnection(conn, nodeIdMap);
                if (connObj != null) {
                    connectionsArray.add(connObj);
                }
            }
            root.add("connections", connectionsArray);

            String json = gson.toJson(root);
            DebugManager.info(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Serialized {} nodes, {} connections",
                nodes.size(), connections.size());

            return json;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Serialization failed", e);
            return null;
        }
    }

    /**
     * Deserialize node graph from JSON string
     */
    public DeserializationResult deserialize(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            // Check version
            String version = root.get("version").getAsString();
            if (!VERSION.equals(version)) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[NodeGraphSerializer] Version mismatch: {} vs {}", version, VERSION);
            }

            // Deserialize nodes
            JsonArray nodesArray = root.getAsJsonArray("nodes");
            List<Node> nodes = new ArrayList<>();
            Map<String, Node> nodeMap = new HashMap<>();

            for (JsonElement elem : nodesArray) {
                JsonObject nodeObj = elem.getAsJsonObject();
                Node node = deserializeNode(nodeObj);
                if (node != null) {
                    nodes.add(node);
                    String id = nodeObj.get("id").getAsString();
                    nodeMap.put(id, node);
                }
            }

            // Deserialize connections
            JsonArray connectionsArray = root.getAsJsonArray("connections");
            List<NodeConnection> connections = new ArrayList<>();

            for (JsonElement elem : connectionsArray) {
                JsonObject connObj = elem.getAsJsonObject();
                NodeConnection conn = deserializeConnection(connObj, nodeMap);
                if (conn != null) {
                    connections.add(conn);
                }
            }

            DebugManager.info(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Deserialized {} nodes, {} connections",
                nodes.size(), connections.size());

            return new DeserializationResult(nodes, connections);

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Deserialization failed", e);
            return null;
        }
    }

    /**
     * Save to file
     */
    public boolean saveToFile(List<Node> nodes, List<NodeConnection> connections, File file) {
        try {
            String json = serialize(nodes, connections);
            if (json == null) return false;

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
            }

            DebugManager.info(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Saved to file: {}", file.getAbsolutePath());
            return true;

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Failed to save file", e);
            return false;
        }
    }

    /**
     * Load from file
     */
    public DeserializationResult loadFromFile(File file) {
        try {
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line).append("\n");
                }
            }

            DebugManager.info(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Loaded from file: {}", file.getAbsolutePath());

            return deserialize(json.toString());

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Failed to load file", e);
            return null;
        }
    }

    /**
     * Serialize a single node
     */
    private JsonObject serializeNode(Node node, String id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);

        // Infer type from class name
        String className = node.getClass().getSimpleName();
        String typeName = inferNodeType(className);
        obj.addProperty("type", typeName);

        obj.addProperty("title", node.getTitle());
        obj.addProperty("x", node.getX());
        obj.addProperty("y", node.getY());

        // Serialize socket values
        JsonArray inputsArray = new JsonArray();
        for (NodeSocket socket : node.getInputs()) {
            JsonObject socketObj = new JsonObject();
            socketObj.addProperty("name", socket.getName());
            socketObj.add("value", serializeValue(socket.getValue()));
            inputsArray.add(socketObj);
        }
        obj.add("inputs", inputsArray);

        return obj;
    }

    /**
     * Infer NodeType from class name
     */
    private String inferNodeType(String className) {
        // Remove "Node" suffix if present
        if (className.endsWith("Node")) {
            className = className.substring(0, className.length() - 4);
        }

        // Convert to uppercase with underscores
        // e.g., "ColorNode" -> "COLOR", "TextureSample" -> "TEXTURE_SAMPLE"
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append('_');
            }
            result.append(Character.toUpperCase(c));
        }

        return result.toString();
    }

    /**
     * Deserialize a single node
     */
    private Node deserializeNode(JsonObject obj) {
        try {
            String typeName = obj.get("type").getAsString();
            NodeType type = NodeType.valueOf(typeName);

            Node node = NodeFactory.createNode(type);
            if (node == null) return null;

            node.setTitle(obj.get("title").getAsString());
            node.setPosition(obj.get("x").getAsFloat(), obj.get("y").getAsFloat());

            // Restore socket values
            if (obj.has("inputs")) {
                JsonArray inputsArray = obj.getAsJsonArray("inputs");
                for (JsonElement elem : inputsArray) {
                    JsonObject socketObj = elem.getAsJsonObject();
                    String name = socketObj.get("name").getAsString();
                    NodeSocket socket = node.getInputByName(name);
                    if (socket != null && socketObj.has("value")) {
                        Object value = deserializeValue(socketObj.get("value"));
                        socket.setValue(value);
                    }
                }
            }

            return node;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Failed to deserialize node", e);
            return null;
        }
    }

    /**
     * Serialize a connection
     */
    private JsonObject serializeConnection(NodeConnection conn, Map<Node, String> nodeIdMap) {
        try {
            NodeSocket output = conn.getOutputSocket();
            NodeSocket input = conn.getInputSocket();

            String outputNodeId = nodeIdMap.get(output.getNode());
            String inputNodeId = nodeIdMap.get(input.getNode());

            if (outputNodeId == null || inputNodeId == null) return null;

            JsonObject obj = new JsonObject();
            obj.addProperty("outputNode", outputNodeId);
            obj.addProperty("outputSocket", output.getName());
            obj.addProperty("inputNode", inputNodeId);
            obj.addProperty("inputSocket", input.getName());

            return obj;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Failed to serialize connection", e);
            return null;
        }
    }

    /**
     * Deserialize a connection
     */
    private NodeConnection deserializeConnection(JsonObject obj, Map<String, Node> nodeMap) {
        try {
            String outputNodeId = obj.get("outputNode").getAsString();
            String outputSocketName = obj.get("outputSocket").getAsString();
            String inputNodeId = obj.get("inputNode").getAsString();
            String inputSocketName = obj.get("inputSocket").getAsString();

            Node outputNode = nodeMap.get(outputNodeId);
            Node inputNode = nodeMap.get(inputNodeId);

            if (outputNode == null || inputNode == null) return null;

            NodeSocket outputSocket = outputNode.getOutputByName(outputSocketName);
            NodeSocket inputSocket = inputNode.getInputByName(inputSocketName);

            if (outputSocket == null || inputSocket == null) return null;

            return new NodeConnection(outputSocket, inputSocket);

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeGraphSerializer] Failed to deserialize connection", e);
            return null;
        }
    }

    /**
     * Serialize a value to JSON
     */
    private JsonElement serializeValue(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Number) return new JsonPrimitive((Number) value);
        if (value instanceof String) return new JsonPrimitive((String) value);
        if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
        if (value instanceof Vector3f || value instanceof Vector4f) {
            return gson.toJsonTree(value);
        }
        return JsonNull.INSTANCE;
    }

    /**
     * Deserialize a value from JSON
     */
    private Object deserializeValue(JsonElement elem) {
        if (elem.isJsonNull()) return null;
        if (elem.isJsonPrimitive()) {
            JsonPrimitive prim = elem.getAsJsonPrimitive();
            if (prim.isNumber()) return prim.getAsFloat();
            if (prim.isString()) return prim.getAsString();
            if (prim.isBoolean()) return prim.getAsBoolean();
        }
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("x") && obj.has("y") && obj.has("z")) {
                if (obj.has("w")) {
                    return gson.fromJson(elem, Vector4f.class);
                } else {
                    return gson.fromJson(elem, Vector3f.class);
                }
            }
        }
        return null;
    }

    /**
     * Deserialization result
     */
    public static class DeserializationResult {
        public final List<Node> nodes;
        public final List<NodeConnection> connections;

        public DeserializationResult(List<Node> nodes, List<NodeConnection> connections) {
            this.nodes = nodes;
            this.connections = connections;
        }
    }

    /**
     * Vector3f JSON adapter
     */
    private static class Vector3fAdapter implements JsonSerializer<Vector3f>, JsonDeserializer<Vector3f> {
        @Override
        public JsonElement serialize(Vector3f src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.x);
            obj.addProperty("y", src.y);
            obj.addProperty("z", src.z);
            return obj;
        }

        @Override
        public Vector3f deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) {
            JsonObject obj = json.getAsJsonObject();
            return new Vector3f(
                obj.get("x").getAsFloat(),
                obj.get("y").getAsFloat(),
                obj.get("z").getAsFloat()
            );
        }
    }

    /**
     * Vector4f JSON adapter
     */
    private static class Vector4fAdapter implements JsonSerializer<Vector4f>, JsonDeserializer<Vector4f> {
        @Override
        public JsonElement serialize(Vector4f src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.x);
            obj.addProperty("y", src.y);
            obj.addProperty("z", src.z);
            obj.addProperty("w", src.w);
            return obj;
        }

        @Override
        public Vector4f deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) {
            JsonObject obj = json.getAsJsonObject();
            return new Vector4f(
                obj.get("x").getAsFloat(),
                obj.get("y").getAsFloat(),
                obj.get("z").getAsFloat(),
                obj.get("w").getAsFloat()
            );
        }
    }
}
