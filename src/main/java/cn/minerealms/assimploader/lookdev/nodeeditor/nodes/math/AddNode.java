package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Add Node
 *
 * Adds two values component-wise.
 *
 * Inputs:
 * - A (float/vec3/vec4): First value
 * - B (float/vec3/vec4): Second value
 *
 * Outputs:
 * - Result (same type as inputs): A + B
 *
 * Common uses:
 * - Combining colors
 * - Adding emissive contribution
 * - Offsetting values
 */
public class AddNode extends Node {

    public AddNode() {
        super("Add");

        // Add input sockets (default to VEC3 for color operations)
        addInput(new EnhancedNodeSocket("A", EnhancedSocketType.VEC3));
        addInput(new EnhancedNodeSocket("B", EnhancedSocketType.VEC3));

        // Add output socket
        addOutput(new EnhancedNodeSocket("Result", EnhancedSocketType.VEC3));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        // Draw plus symbol (+)
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.5f);

        float symbolSize = 15;
        // Horizontal line
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY,
                centerX + symbolSize / 2, bodyY,
                paint
        );
        // Vertical line
        canvas.drawLine(
                centerX, bodyY - symbolSize / 2,
                centerX, bodyY + symbolSize / 2,
                paint
        );
    }
}
