package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.vector;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import org.joml.Vector3f;

/**
 * Combine Node
 *
 * Combines individual components into a vector.
 *
 * Inputs:
 * - X (float): X component (default: 0.0)
 * - Y (float): Y component (default: 0.0)
 * - Z (float): Z component (default: 0.0)
 *
 * Outputs:
 * - Vector (vec3): Combined vector
 *
 * Common uses:
 * - Build color from separate channels
 * - Construct position/normal vectors
 * - Combine processed components
 */
public class CombineNode extends Node {

    public CombineNode() {
        super("Combine");

        // Add input sockets
        addInput(new EnhancedNodeSocket("X", EnhancedSocketType.FLOAT, 0.0f));
        addInput(new EnhancedNodeSocket("Y", EnhancedSocketType.FLOAT, 0.0f));
        addInput(new EnhancedNodeSocket("Z", EnhancedSocketType.FLOAT, 0.0f));

        // Add output socket
        addOutput(new EnhancedNodeSocket("Vector", EnhancedSocketType.VEC3));

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

        // Draw combine arrow
        canvas.drawLine(centerX - 20, bodyY, centerX + 20, bodyY, paint);
        canvas.drawLine(centerX - 20, bodyY, centerX - 15, bodyY - 5, paint);
        canvas.drawLine(centerX - 20, bodyY, centerX - 15, bodyY + 5, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        float x = ((Number) inputs.get(0).getValue()).floatValue();
        float y = ((Number) inputs.get(1).getValue()).floatValue();
        float z = ((Number) inputs.get(2).getValue()).floatValue();

        Vector3f vector = new Vector3f(x, y, z);

        outputs.get(0).setValue(vector);
        context.setSocketValue(outputs.get(0), vector);
    }
}
