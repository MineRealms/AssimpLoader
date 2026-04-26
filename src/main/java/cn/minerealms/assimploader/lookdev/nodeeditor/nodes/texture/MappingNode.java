package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.texture;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import org.joml.Vector3f;

/**
 * Mapping Node
 *
 * Advanced UV mapping transformations.
 *
 * Inputs:
 * - Vector (vec3): Input coordinates (default: UV)
 * - Location (vec3): Translation (default: 0, 0, 0)
 * - Rotation (vec3): Rotation in radians (default: 0, 0, 0)
 * - Scale (vec3): Scale factor (default: 1, 1, 1)
 *
 * Outputs:
 * - Vector (vec3): Transformed coordinates
 *
 * Mapping types:
 * - Point: Standard point transformation
 * - Texture: UV texture mapping
 * - Vector: Direction vector transformation
 * - Normal: Normal vector transformation
 *
 * Common uses:
 * - Complex texture transformations
 * - Procedural texture generation
 * - Multi-layer texture effects
 */
public class MappingNode extends Node {

    public enum MappingType {
        POINT("Point"),
        TEXTURE("Texture"),
        VECTOR("Vector"),
        NORMAL("Normal");

        private final String displayName;

        MappingType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private MappingType mappingType = MappingType.TEXTURE;

    public MappingNode() {
        super("Mapping");

        addInput(new EnhancedNodeSocket("Vector", EnhancedSocketType.VEC3, new Vector3f(0, 0, 0)));
        addInput(new EnhancedNodeSocket("Location", EnhancedSocketType.VEC3, new Vector3f(0, 0, 0)));
        addInput(new EnhancedNodeSocket("Rotation", EnhancedSocketType.VEC3, new Vector3f(0, 0, 0)));
        addInput(new EnhancedNodeSocket("Scale", EnhancedSocketType.VEC3, new Vector3f(1, 1, 1)));
        addOutput(new EnhancedNodeSocket("Vector", EnhancedSocketType.VEC3));

        this.width = 180.0f;
    }

    public MappingType getMappingType() {
        return mappingType;
    }

    public void setMappingType(MappingType mappingType) {
        this.mappingType = mappingType;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        // Draw mapping symbol (grid with transform)
        float symbolSize = 15;
        canvas.drawRect(centerX - symbolSize / 2, bodyY - symbolSize / 2,
                       centerX + symbolSize / 2, bodyY + symbolSize / 2, paint);
        canvas.drawLine(centerX - symbolSize / 2, bodyY, centerX + symbolSize / 2, bodyY, paint);
        canvas.drawLine(centerX, bodyY - symbolSize / 2, centerX, bodyY + symbolSize / 2, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        Object vectorObj = inputs.get(0).getValue();
        Object locationObj = inputs.get(1).getValue();
        Object rotationObj = inputs.get(2).getValue();
        Object scaleObj = inputs.get(3).getValue();

        Vector3f vector = vectorObj instanceof Vector3f ? (Vector3f) vectorObj : new Vector3f(0, 0, 0);
        Vector3f location = locationObj instanceof Vector3f ? (Vector3f) locationObj : new Vector3f(0, 0, 0);
        Vector3f rotation = rotationObj instanceof Vector3f ? (Vector3f) rotationObj : new Vector3f(0, 0, 0);
        Vector3f scale = scaleObj instanceof Vector3f ? (Vector3f) scaleObj : new Vector3f(1, 1, 1);

        // Apply transformations
        Vector3f result = new Vector3f();

        // Scale
        result.x = vector.x * scale.x;
        result.y = vector.y * scale.y;
        result.z = vector.z * scale.z;

        // Rotation (simplified - only Z rotation for 2D UV)
        if (rotation.z != 0) {
            float cos = (float) Math.cos(rotation.z);
            float sin = (float) Math.sin(rotation.z);
            float x = result.x;
            float y = result.y;
            result.x = x * cos - y * sin;
            result.y = x * sin + y * cos;
        }

        // Translation
        result.x += location.x;
        result.y += location.y;
        result.z += location.z;

        // Apply mapping type specific transformations
        switch (mappingType) {
            case TEXTURE:
                // Wrap UV coordinates to [0, 1]
                result.x = result.x - (float) Math.floor(result.x);
                result.y = result.y - (float) Math.floor(result.y);
                break;

            case NORMAL:
                // Normalize the vector
                float length = (float) Math.sqrt(result.x * result.x + result.y * result.y + result.z * result.z);
                if (length > 0.0001f) {
                    result.x /= length;
                    result.y /= length;
                    result.z /= length;
                }
                break;

            case POINT:
            case VECTOR:
            default:
                // No additional transformation
                break;
        }

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
