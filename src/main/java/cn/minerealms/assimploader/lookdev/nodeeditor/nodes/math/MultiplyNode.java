package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Multiply Node
 *
 * Multiplies two values component-wise.
 *
 * Inputs:
 * - A (float/vec3/vec4): First value
 * - B (float/vec3/vec4): Second value
 *
 * Outputs:
 * - Result (same type as inputs): A * B
 *
 * Common uses:
 * - Color tinting (multiply texture by color)
 * - Scaling values
 * - Masking operations
 */
public class MultiplyNode extends Node {

    public MultiplyNode() {
        super("Multiply");

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

        // Draw multiply symbol (×)
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.5f);

        float symbolSize = 15;
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY - symbolSize / 2,
                centerX + symbolSize / 2, bodyY + symbolSize / 2,
                paint
        );
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY + symbolSize / 2,
                centerX + symbolSize / 2, bodyY - symbolSize / 2,
                paint
        );
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        NodeSocket inputA = inputs.get(0);
        NodeSocket inputB = inputs.get(1);
        NodeSocket output = outputs.get(0);

        Object valueA = inputA.getValue();
        Object valueB = inputB.getValue();

        // Handle different types
        Object result = null;

        if (valueA instanceof Float && valueB instanceof Float) {
            // Float * Float
            result = (Float) valueA * (Float) valueB;
        } else if (valueA instanceof Vector3f && valueB instanceof Vector3f) {
            // Vec3 * Vec3 (component-wise)
            Vector3f a = (Vector3f) valueA;
            Vector3f b = (Vector3f) valueB;
            result = new Vector3f(a.x * b.x, a.y * b.y, a.z * b.z);
        } else if (valueA instanceof Vector3f && valueB instanceof Float) {
            // Vec3 * Float (scalar multiply)
            Vector3f a = (Vector3f) valueA;
            float b = (Float) valueB;
            result = new Vector3f(a.x * b, a.y * b, a.z * b);
        } else if (valueA instanceof Float && valueB instanceof Vector3f) {
            // Float * Vec3 (scalar multiply)
            float a = (Float) valueA;
            Vector3f b = (Vector3f) valueB;
            result = new Vector3f(a * b.x, a * b.y, a * b.z);
        } else if (valueA instanceof Vector4f && valueB instanceof Vector4f) {
            // Vec4 * Vec4 (component-wise)
            Vector4f a = (Vector4f) valueA;
            Vector4f b = (Vector4f) valueB;
            result = new Vector4f(a.x * b.x, a.y * b.y, a.z * b.z, a.w * b.w);
        } else {
            // Default: treat as float
            float a = valueA instanceof Number ? ((Number) valueA).floatValue() : 0.0f;
            float b = valueB instanceof Number ? ((Number) valueB).floatValue() : 0.0f;
            result = a * b;
        }

        output.setValue(result);
        context.setSocketValue(output, result);
    }
}
