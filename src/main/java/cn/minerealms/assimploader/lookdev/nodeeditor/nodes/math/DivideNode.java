package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Divide Node
 *
 * Divides one value by another.
 *
 * Inputs:
 * - A (float): Dividend (default: 1.0)
 * - B (float): Divisor (default: 1.0)
 *
 * Outputs:
 * - Result (float): A / B (returns 0 if B is 0)
 */
public class DivideNode extends Node {

    public DivideNode() {
        super("Divide");

        addInput(new EnhancedNodeSocket("A", EnhancedSocketType.FLOAT, 1.0f));
        addInput(new EnhancedNodeSocket("B", EnhancedSocketType.FLOAT, 1.0f));
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

        // Draw division symbol (÷)
        float symbolSize = 15;
        // Top dot
        canvas.drawCircle(centerX, bodyY - 5, 2, paint);
        // Middle line
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY,
                centerX + symbolSize / 2, bodyY,
                paint
        );
        // Bottom dot
        canvas.drawCircle(centerX, bodyY + 5, 2, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        float a = ((Number) inputs.get(0).getValue()).floatValue();
        float b = ((Number) inputs.get(1).getValue()).floatValue();
        float result = (b != 0) ? a / b : 0.0f;

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
