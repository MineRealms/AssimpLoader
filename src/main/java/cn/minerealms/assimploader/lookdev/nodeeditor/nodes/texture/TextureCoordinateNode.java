package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.texture;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import org.joml.Vector3f;

/**
 * Texture Coordinate Node
 *
 * Provides and transforms texture coordinates.
 *
 * Inputs:
 * - Offset (vec3): UV offset (default: 0, 0, 0)
 * - Scale (vec3): UV scale (default: 1, 1, 1)
 * - Rotation (float): Rotation angle in radians (default: 0.0)
 *
 * Outputs:
 * - UV (vec3): Transformed texture coordinates
 *
 * Common uses:
 * - Tile textures by scaling UVs
 * - Animate textures by offsetting UVs
 * - Rotate texture mapping
 */
public class TextureCoordinateNode extends Node {

    public TextureCoordinateNode() {
        super("Texture Coordinate");

        addInput(new EnhancedNodeSocket("Offset", EnhancedSocketType.VEC3, new Vector3f(0, 0, 0)));
        addInput(new EnhancedNodeSocket("Scale", EnhancedSocketType.VEC3, new Vector3f(1, 1, 1)));
        addInput(new EnhancedNodeSocket("Rotation", EnhancedSocketType.FLOAT, 0.0f));
        addOutput(new EnhancedNodeSocket("UV", EnhancedSocketType.VEC3));

        this.width = 180.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        // Draw UV grid icon
        float gridSize = 20;
        float gridX = centerX - gridSize / 2;
        float gridY = bodyY - 5;

        // Grid lines
        canvas.drawLine(gridX, gridY, gridX + gridSize, gridY, paint);
        canvas.drawLine(gridX, gridY + gridSize / 2, gridX + gridSize, gridY + gridSize / 2, paint);
        canvas.drawLine(gridX, gridY + gridSize, gridX + gridSize, gridY + gridSize, paint);

        canvas.drawLine(gridX, gridY, gridX, gridY + gridSize, paint);
        canvas.drawLine(gridX + gridSize / 2, gridY, gridX + gridSize / 2, gridY + gridSize, paint);
        canvas.drawLine(gridX + gridSize, gridY, gridX + gridSize, gridY + gridSize, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        Object offsetObj = inputs.get(0).getValue();
        Object scaleObj = inputs.get(1).getValue();
        float rotation = ((Number) inputs.get(2).getValue()).floatValue();

        Vector3f offset = offsetObj instanceof Vector3f ? (Vector3f) offsetObj : new Vector3f(0, 0, 0);
        Vector3f scale = scaleObj instanceof Vector3f ? (Vector3f) scaleObj : new Vector3f(1, 1, 1);

        // Base UV coordinates (would come from mesh in real implementation)
        float u = 0.5f;
        float v = 0.5f;

        // Apply rotation
        if (rotation != 0) {
            float cos = (float) Math.cos(rotation);
            float sin = (float) Math.sin(rotation);
            float uCentered = u - 0.5f;
            float vCentered = v - 0.5f;
            u = uCentered * cos - vCentered * sin + 0.5f;
            v = uCentered * sin + vCentered * cos + 0.5f;
        }

        // Apply scale and offset
        u = u * scale.x + offset.x;
        v = v * scale.y + offset.y;

        Vector3f uv = new Vector3f(u, v, 0);

        outputs.get(0).setValue(uv);
        context.setSocketValue(outputs.get(0), uv);
    }
}
