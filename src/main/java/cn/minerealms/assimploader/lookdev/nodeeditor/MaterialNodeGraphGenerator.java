package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.TextureSampleNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.ColorNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.ValueNode;
import cn.minerealms.assimploader.material.PBRMaterial;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates default node graphs from PBR materials.
 *
 * Creates a basic material setup:
 * - Texture sample nodes for each texture channel
 * - Color/value nodes for factors
 * - Connections to material output node
 */
public class MaterialNodeGraphGenerator {

    private static final float NODE_SPACING_X = 300.0f;
    private static final float NODE_SPACING_Y = 150.0f;
    private static final float START_X = 100.0f;
    private static final float START_Y = 100.0f;

    /**
     * Generate a default node graph from a PBR material.
     *
     * @param material The PBR material to convert
     * @return List of nodes and connections
     */
    public static NodeGraphData generateFromMaterial(PBRMaterial material) {
        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphGenerator] Generating node graph for material: {}", material.getName());

        List<Node> nodes = new ArrayList<>();
        List<NodeConnection> connections = new ArrayList<>();

        float currentY = START_Y;

        // Create material output node (rightmost)
        OutputNode outputNode = new OutputNode();
        outputNode.setPosition(START_X + NODE_SPACING_X * 3, START_Y + NODE_SPACING_Y * 2);
        nodes.add(outputNode);

        // Base Color
        if (material.getBaseColorTexture() != null) {
            TextureSampleNode texNode = createTextureNode(
                material.getBaseColorTexture(),
                START_X,
                currentY
            );
            nodes.add(texNode);

            // Connect to output
            connections.add(new NodeConnection(
                texNode.getOutputByName("Color"),
                outputNode.getInputByName("Base Color")
            ));

            currentY += NODE_SPACING_Y;
        } else {
            // Use color node for base color factor
            ColorNode colorNode = new ColorNode();
            colorNode.setPosition(START_X, currentY);
            colorNode.setColor(
                material.getBaseColor().x,
                material.getBaseColor().y,
                material.getBaseColor().z,
                material.getBaseColor().w
            );
            nodes.add(colorNode);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialNodeGraphGenerator] Created ColorNode with baseColor: ({}, {}, {}, {})",
                material.getBaseColor().x,
                material.getBaseColor().y,
                material.getBaseColor().z,
                material.getBaseColor().w);

            connections.add(new NodeConnection(
                colorNode.getOutputByName("Color"),
                outputNode.getInputByName("Base Color")
            ));

            currentY += NODE_SPACING_Y;
        }

        // Normal Map
        if (material.getNormalTexture() != null) {
            TextureSampleNode normalNode = createTextureNode(
                material.getNormalTexture(),
                START_X,
                currentY
            );
            nodes.add(normalNode);

            connections.add(new NodeConnection(
                normalNode.getOutputByName("Color"),
                outputNode.getInputByName("Normal")
            ));

            currentY += NODE_SPACING_Y;
        }

        // Metallic & Roughness
        if (material.getMetallicRoughnessTexture() != null) {
            // Single texture contains both metallic (B) and roughness (G)
            TextureSampleNode mrNode = createTextureNode(
                material.getMetallicRoughnessTexture(),
                START_X,
                currentY
            );
            nodes.add(mrNode);

            // TODO: Split channels - for now connect color to both
            connections.add(new NodeConnection(
                mrNode.getOutputByName("Color"),
                outputNode.getInputByName("Metallic")
            ));

            currentY += NODE_SPACING_Y;
        } else {
            // Use value nodes for factors
            ValueNode metallicNode = new ValueNode();
            metallicNode.setPosition(START_X, currentY);
            metallicNode.setValue(material.getMetallicFactor());
            nodes.add(metallicNode);

            connections.add(new NodeConnection(
                metallicNode.getOutputByName("Value"),
                outputNode.getInputByName("Metallic")
            ));

            currentY += NODE_SPACING_Y;

            ValueNode roughnessNode = new ValueNode();
            roughnessNode.setPosition(START_X, currentY);
            roughnessNode.setValue(material.getRoughnessFactor());
            nodes.add(roughnessNode);

            connections.add(new NodeConnection(
                roughnessNode.getOutputByName("Value"),
                outputNode.getInputByName("Roughness")
            ));

            currentY += NODE_SPACING_Y;
        }

        // Emissive
        if (material.getEmissiveTexture() != null) {
            TextureSampleNode emissiveNode = createTextureNode(
                material.getEmissiveTexture(),
                START_X,
                currentY
            );
            nodes.add(emissiveNode);

            connections.add(new NodeConnection(
                emissiveNode.getOutputByName("Color"),
                outputNode.getInputByName("Emission")
            ));

            currentY += NODE_SPACING_Y;
        } else if (material.getEmissiveFactor().lengthSquared() > 0.001f) {
            // Use color node for emissive factor
            ColorNode emissiveColor = new ColorNode();
            emissiveColor.setPosition(START_X, currentY);
            emissiveColor.setColor(
                material.getEmissiveFactor().x,
                material.getEmissiveFactor().y,
                material.getEmissiveFactor().z,
                1.0f  // Alpha
            );
            nodes.add(emissiveColor);

            connections.add(new NodeConnection(
                emissiveColor.getOutputByName("Color"),
                outputNode.getInputByName("Emission")
            ));

            currentY += NODE_SPACING_Y;
        }

        // Ambient Occlusion
        if (material.getAoTexture() != null) {
            TextureSampleNode aoNode = createTextureNode(
                material.getAoTexture(),
                START_X,
                currentY
            );
            nodes.add(aoNode);

            // AO is typically grayscale, use color output
            connections.add(new NodeConnection(
                aoNode.getOutputByName("Color"),
                outputNode.getInputByName("Ambient Occlusion")
            ));

            currentY += NODE_SPACING_Y;
        }

        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialNodeGraphGenerator] Generated {} nodes and {} connections",
            nodes.size(), connections.size());

        return new NodeGraphData(nodes, connections);
    }

    /**
     * Create a texture sample node at the given position
     */
    private static TextureSampleNode createTextureNode(ResourceLocation texture, float x, float y) {
        TextureSampleNode node = new TextureSampleNode();
        node.setPosition(x, y);
        node.setTexturePath(texture.toString());
        return node;
    }

    /**
     * Data class to hold generated node graph
     */
    public static class NodeGraphData {
        public final List<Node> nodes;
        public final List<NodeConnection> connections;

        public NodeGraphData(List<Node> nodes, List<NodeConnection> connections) {
            this.nodes = nodes;
            this.connections = connections;
        }
    }
}
