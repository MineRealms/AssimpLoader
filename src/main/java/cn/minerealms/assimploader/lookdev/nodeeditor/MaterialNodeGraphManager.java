package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.serialization.MaterialDeserializer;
import cn.minerealms.assimploader.lookdev.nodeeditor.serialization.MaterialFileManager;
import cn.minerealms.assimploader.material.PBRMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Material-Node Graph Manager
 *
 * Responsibilities:
 * 1. Manage node graph state for each material (in-memory cache)
 * 2. Auto-load/save node graphs
 * 3. Handle node graph switching when material changes
 * 4. Interact with MaterialFileManager for persistence
 *
 * Workflow:
 * - User selects material A → Save current graph to cache → Load material A's graph
 * - If not in cache → Try load from file → If file doesn't exist → Generate default
 * - User presses Ctrl+S → Save current graph to file
 * - User switches material → Auto-save current graph to cache
 */
public class MaterialNodeGraphManager {

    // In-memory cache: materialName → node graph state
    private final Map<String, NodeGraphState> nodeGraphCache = new HashMap<>();

    // Currently editing material name
    private String currentMaterialName = null;

    /**
     * Switch to specified material's node graph
     *
     * @param material Material to edit
     * @param canvas Node editor canvas
     * @return Success status
     */
    public boolean switchToMaterial(PBRMaterial material, NodeEditorCanvas canvas) {
        if (material == null) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Cannot switch to null material");
            return false;
        }

        String materialName = material.getName();

        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphManager] Switching from '{}' to '{}'",
            currentMaterialName, materialName);

        // 1. Save current graph to cache
        if (currentMaterialName != null && !currentMaterialName.equals(materialName)) {
            saveCurrentToCache(canvas);
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Saved current material '{}' to cache",
                currentMaterialName);
        }

        // 2. Load new material's graph
        NodeGraphState state = nodeGraphCache.get(materialName);

        if (state == null) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Material '{}' not in cache, loading from file or generating",
                materialName);

            // 2a. Try load from file
            state = loadFromFile(materialName);

            if (state == null) {
                // 2b. Generate default node graph
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[MaterialNodeGraphManager] Generating default node graph for '{}'",
                    materialName);
                state = generateDefaultNodeGraph(material);
            } else {
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[MaterialNodeGraphManager] Loaded material '{}' from file",
                    materialName);
            }

            // Put in cache
            nodeGraphCache.put(materialName, state);
        } else {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Loaded material '{}' from cache",
                materialName);
        }

        // 3. Apply to canvas
        canvas.clear();
        canvas.loadNodeGraph(state.nodes, state.connections);
        currentMaterialName = materialName;

        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphManager] Successfully switched to material '{}' ({} nodes, {} connections)",
            materialName, state.nodes.size(), state.connections.size());

        return true;
    }

    /**
     * Save current node graph to file (Ctrl+S)
     *
     * @param canvas Node editor canvas
     * @return Success status
     */
    public boolean saveCurrentToFile(NodeEditorCanvas canvas) {
        if (currentMaterialName == null) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] No material loaded, cannot save");
            return false;
        }

        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphManager] Saving material '{}' to file",
            currentMaterialName);

        // 1. Update cache
        saveCurrentToCache(canvas);

        // 2. Save to file
        NodeGraphState state = nodeGraphCache.get(currentMaterialName);
        boolean success = MaterialFileManager.save(
            currentMaterialName,
            state.nodes,
            state.connections
        );

        if (success) {
            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Successfully saved material '{}' to file",
                currentMaterialName);
        } else {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Failed to save material '{}' to file",
                currentMaterialName);
        }

        return success;
    }

    /**
     * Get currently editing material name
     */
    public String getCurrentMaterialName() {
        return currentMaterialName;
    }

    /**
     * Check if specified material is in cache
     */
    public boolean isCached(String materialName) {
        return nodeGraphCache.containsKey(materialName);
    }

    /**
     * Clear cache
     */
    public void clearCache() {
        nodeGraphCache.clear();
        currentMaterialName = null;
        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphManager] Cache cleared");
    }

    /**
     * Generate default node graph (from PBRMaterial)
     */
    private NodeGraphState generateDefaultNodeGraph(PBRMaterial material) {
        MaterialNodeGraphGenerator.NodeGraphData data =
            MaterialNodeGraphGenerator.generateFromMaterial(material);

        return new NodeGraphState(data.nodes, data.connections);
    }

    /**
     * Load node graph from file
     */
    private NodeGraphState loadFromFile(String materialName) {
        if (!MaterialFileManager.exists(materialName)) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Material file does not exist: {}",
                materialName);
            return null;
        }

        MaterialDeserializer.DeserializeResult result =
            MaterialFileManager.load(materialName);

        if (!result.isSuccess()) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphManager] Failed to load material from file: {}",
                result.error);
            return null;
        }

        return new NodeGraphState(result.nodes, result.connections);
    }

    /**
     * Save current node graph to cache
     */
    private void saveCurrentToCache(NodeEditorCanvas canvas) {
        if (currentMaterialName == null) {
            return;
        }

        List<Node> nodes = new ArrayList<>(canvas.getNodes());
        List<NodeConnection> connections = new ArrayList<>(canvas.getConnections());

        nodeGraphCache.put(
            currentMaterialName,
            new NodeGraphState(nodes, connections)
        );

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphManager] Saved material '{}' to cache ({} nodes, {} connections)",
            currentMaterialName, nodes.size(), connections.size());
    }

    /**
     * Node graph state (immutable)
     */
    public static class NodeGraphState {
        public final List<Node> nodes;
        public final List<NodeConnection> connections;

        public NodeGraphState(List<Node> nodes, List<NodeConnection> connections) {
            // Deep copy for immutability
            this.nodes = new ArrayList<>(nodes);
            this.connections = new ArrayList<>(connections);
        }
    }
}
