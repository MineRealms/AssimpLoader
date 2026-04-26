package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Subtract Node
 *
 * Subtracts one value from another.
 *
 * Inputs:
 * - A (float): Minuend (default: 0.0)
 * - B (float): Subtrahend (default: 0.0)
 *
 * Outputs:
 * - Result (float): A - B
 */
public class SubtractNode extends Node {

    public SubtractNode() {
        super("Subtract");

        addInput(new EnhancedNodeSocket("A", EnhancedSocketType.FLOAT, 0.0f));
        addInput(new EnhancedNodeSocket("B", EnhancedSocketType.FLOAT, 0.0f));
        addOutput(new EnhancedNodeSocket("Result", EnhancedSocketType.FLOAT));

        this.width = 140.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        paint.setColor(0xFFCCCCCC);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.5f);

        // Draw minus symbol (-)
        float symbolSize = 15;
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY,
                centerX + symbolSize / 2, bodyY,
                paint
        );
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        float a = ((Number) inputs.get(0).getValue()).floatValue();
        float b = ((Number) inputs.get(1).getValue()).floatValue();
        float result = a - b;

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
