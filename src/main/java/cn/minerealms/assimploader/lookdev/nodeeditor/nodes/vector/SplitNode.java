package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.vector;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import org.joml.Vector3f;

/**
 * Split Node
 *
 * Separates a vector into individual components.
 *
 * Inputs:
 * - Vector (vec3): Input vector
 *
 * Outputs:
 * - X (float): X component
 * - Y (float): Y component
 * - Z (float): Z component
 *
 * Common uses:
 * - Extract individual color channels
 * - Separate position/normal components
 * - Component-wise operations
 */
public class SplitNode extends Node {

    public SplitNode() {
        super("Split");

        // Add input socket
        addInput(new EnhancedNodeSocket("Vector", EnhancedSocketType.VEC3));

        // Add output sockets
        addOutput(new EnhancedNodeSocket("X", EnhancedSocketType.FLOAT));
        addOutput(new EnhancedNodeSocket("Y", EnhancedSocketType.FLOAT));
        addOutput(new EnhancedNodeSocket("Z", EnhancedSocketType.FLOAT));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Simple visual indicator
        float bodyY = y + TITLE_BAR_HEIGHT + 15;
        float centerX = x + width / 2;

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        // Draw split arrow
        canvas.drawLine(centerX - 20, bodyY, centerX + 20, bodyY, paint);
        canvas.drawLine(centerX + 20, bodyY, centerX + 15, bodyY - 5, paint);
        canvas.drawLine(centerX + 20, bodyY, centerX + 15, bodyY + 5, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        Object vectorObj = inputs.get(0).getValue();

        float x = 0, y = 0, z = 0;
        if (vectorObj instanceof Vector3f) {
            Vector3f vec = (Vector3f) vectorObj;
            x = vec.x;
            y = vec.y;
            z = vec.z;
        } else if (vectorObj instanceof float[]) {
            float[] vec = (float[]) vectorObj;
            x = vec.length > 0 ? vec[0] : 0;
            y = vec.length > 1 ? vec[1] : 0;
            z = vec.length > 2 ? vec[2] : 0;
        }

        outputs.get(0).setValue(x);
        outputs.get(1).setValue(y);
        outputs.get(2).setValue(z);

        context.setSocketValue(outputs.get(0), x);
        context.setSocketValue(outputs.get(1), y);
        context.setSocketValue(outputs.get(2), z);
    }
}
