package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Clamp Node
 *
 * Restricts a value to a specified range.
 *
 * Inputs:
 * - Value (float): Input value (default: 0.5)
 * - Min (float): Minimum value (default: 0.0)
 * - Max (float): Maximum value (default: 1.0)
 *
 * Outputs:
 * - Result (float): Clamped value
 *
 * Common uses:
 * - Ensure values stay within valid ranges
 * - Prevent overflow in calculations
 * - Normalize data
 */
public class ClampNode extends Node {

    public ClampNode() {
        super("Clamp");

        addInput(new EnhancedNodeSocket("Value", EnhancedSocketType.FLOAT, 0.5f));
        addInput(new EnhancedNodeSocket("Min", EnhancedSocketType.FLOAT, 0.0f));
        addInput(new EnhancedNodeSocket("Max", EnhancedSocketType.FLOAT, 1.0f));
        addOutput(new EnhancedNodeSocket("Result", EnhancedSocketType.FLOAT));

        this.width = 140.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        // Draw clamp brackets
        float bracketY = bodyY - 5;
        canvas.drawLine(centerX - 15, bracketY, centerX - 15, bracketY + 10, paint);
        canvas.drawLine(centerX - 15, bracketY, centerX - 10, bracketY, paint);
        canvas.drawLine(centerX - 15, bracketY + 10, centerX - 10, bracketY + 10, paint);

        canvas.drawLine(centerX + 15, bracketY, centerX + 15, bracketY + 10, paint);
        canvas.drawLine(centerX + 15, bracketY, centerX + 10, bracketY, paint);
        canvas.drawLine(centerX + 15, bracketY + 10, centerX + 10, bracketY + 10, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        float value = ((Number) inputs.get(0).getValue()).floatValue();
        float min = ((Number) inputs.get(1).getValue()).floatValue();
        float max = ((Number) inputs.get(2).getValue()).floatValue();

        float result = Math.max(min, Math.min(max, value));

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
