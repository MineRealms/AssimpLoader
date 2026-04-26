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
 * Serializes node graphs to JSON format.
 *
 * JSON format:
 * {
 *   "version": "1.0",
 *   "name": "Material Name",
 *   "nodes": [
 *     {
 *       "id": "node_uuid",
 *       "type": "TEXTURE_SAMPLE",
 *       "position": {"x": 100, "y": 200},
 *       "parameters": {...}
 *     }
 *   ],
 *   "connections": [
 *     {
 *       "from": {"node": "node_id", "socket": "Color"},
 *       "to": {"node": "node_id", "socket": "Albedo"}
 *     }
 *   ]
 * }
 */
public class MaterialSerializer {

    private static final String VERSION = "1.0";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Serialize node graph to JSON string
     */
    public static String serialize(String materialName, List<Node> nodes, List<NodeConnection> connections) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("name", materialName);
            root.add("nodes", serializeNodes(nodes));
            root.add("connections", serializeConnections(connections));

            String json = gson.toJson(root);
            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialSerializer] Serialized material: " + materialName + " (" + nodes.size() + " nodes)");
            return json;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialSerializer] Failed to serialize: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Serialize nodes array
     */
    private static JsonArray serializeNodes(List<Node> nodes) {
        JsonArray array = new JsonArray();

        for (Node node : nodes) {
            JsonObject nodeObj = new JsonObject();
            nodeObj.addProperty("id", node.getId());
            nodeObj.addProperty("type", getNodeType(node));

            // Position
            JsonObject position = new JsonObject();
            position.addProperty("x", node.getX());
            position.addProperty("y", node.getY());
            nodeObj.add("position", position);

            // Parameters (node-specific data)
            JsonObject parameters = serializeNodeParameters(node);
            if (parameters.size() > 0) {
                nodeObj.add("parameters", parameters);
            }

            array.add(nodeObj);
        }

        return array;
    }

    /**
     * Serialize node-specific parameters
     */
    private static JsonObject serializeNodeParameters(Node node) {
        JsonObject params = new JsonObject();

        if (node instanceof TextureSampleNode) {
            TextureSampleNode n = (TextureSampleNode) node;
            params.addProperty("texturePath", n.getTexturePath());
            params.addProperty("filterMode", n.getFilterMode().name());
            params.addProperty("wrapMode", n.getWrapMode().name());

        } else if (node instanceof ValueNode) {
            ValueNode n = (ValueNode) node;
            params.addProperty("value", n.getValue());

        } else if (node instanceof ColorNode) {
            ColorNode n = (ColorNode) node;
            params.addProperty("r", n.getR());
            params.addProperty("g", n.getG());
            params.addProperty("b", n.getB());
            params.addProperty("a", n.getA());

        } else if (node instanceof MaterialOutputNode) {
            MaterialOutputNode n = (MaterialOutputNode) node;
            params.addProperty("blendMode", n.getBlendMode().name());
        }

        // Other node types have no parameters

        return params;
    }

    /**
     * Serialize connections array
     */
    private static JsonArray serializeConnections(List<NodeConnection> connections) {
        JsonArray array = new JsonArray();

        for (NodeConnection conn : connections) {
            JsonObject connObj = new JsonObject();

            // From socket (output)
            JsonObject from = new JsonObject();
            from.addProperty("node", conn.getOutputSocket().getNode().getId());
            from.addProperty("socket", conn.getOutputSocket().getName());
            connObj.add("from", from);

            // To socket (input)
            JsonObject to = new JsonObject();
            to.addProperty("node", conn.getInputSocket().getNode().getId());
            to.addProperty("socket", conn.getInputSocket().getName());
            connObj.add("to", to);

            array.add(connObj);
        }

        return array;
    }

    /**
     * Get node type string
     */
    private static String getNodeType(Node node) {
        if (node instanceof TextureSampleNode) return "TEXTURE_SAMPLE";
        if (node instanceof ValueNode) return "VALUE";
        if (node instanceof ColorNode) return "COLOR";
        if (node instanceof UVCoordinateNode) return "UV_COORDINATE";
        if (node instanceof MultiplyNode) return "MULTIPLY";
        if (node instanceof AddNode) return "ADD";
        if (node instanceof LerpNode) return "LERP";
        if (node instanceof NormalMapNode) return "NORMAL_MAP";
        if (node instanceof MaterialOutputNode) return "MATERIAL_OUTPUT";
        return "UNKNOWN";
    }

    /**
     * Serialize to pretty-printed JSON
     */
    public static String serializePretty(String materialName, List<Node> nodes, List<NodeConnection> connections) {
        return serialize(materialName, nodes, connections);
    }

    /**
     * Serialize to compact JSON
     */
    public static String serializeCompact(String materialName, List<Node> nodes, List<NodeConnection> connections) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("name", materialName);
            root.add("nodes", serializeNodes(nodes));
            root.add("connections", serializeConnections(connections));

            Gson compactGson = new Gson();
            return compactGson.toJson(root);

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialSerializer] Failed to serialize compact: " + e.getMessage());
            return null;
        }
    }
}
