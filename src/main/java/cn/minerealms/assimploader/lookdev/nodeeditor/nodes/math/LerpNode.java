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
 * Lerp Node (Linear Interpolation)
 *
 * Linearly interpolates between two values based on a factor.
 * Result = A * (1 - T) + B * T
 *
 * Inputs:
 * - A (float/vec3/vec4): Start value
 * - B (float/vec3/vec4): End value
 * - T (float): Interpolation factor (0.0 = A, 1.0 = B)
 *
 * Outputs:
 * - Result (same type as A/B): Interpolated value
 *
 * Common uses:
 * - Blending between two colors
 * - Smooth transitions
 * - Masking operations
 */
public class LerpNode extends Node {

    public LerpNode() {
        super("Lerp");

        // Add input sockets
        addInput(new EnhancedNodeSocket("A", EnhancedSocketType.VEC3));
        addInput(new EnhancedNodeSocket("B", EnhancedSocketType.VEC3));
        addInput(new EnhancedNodeSocket("T", EnhancedSocketType.FLOAT));

        // Add output socket
        addOutput(new EnhancedNodeSocket("Result", EnhancedSocketType.VEC3));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 15;
        float bodyX = x + 15;
        float bodyWidth = width - 30;

        // Draw gradient visualization (A -> B)
        paint.setStyle(Paint.FILL);

        // Simple gradient approximation with multiple rectangles
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float t = (float) i / steps;
            int gray = (int) (255 * t);
            int color = 0xFF000000 | (gray << 16) | (gray << 8) | gray;

            paint.setColor(color);
            float rectX = bodyX + (bodyWidth / steps) * i;
            float rectWidth = bodyWidth / steps + 1; // +1 to avoid gaps
            canvas.drawRect(rectX, bodyY, rectX + rectWidth, bodyY + 20, paint);
        }

        // Draw border
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(bodyX, bodyY, bodyX + bodyWidth, bodyY + 20, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        NodeSocket inputA = inputs.get(0);
        NodeSocket inputB = inputs.get(1);
        NodeSocket inputT = inputs.get(2);
        NodeSocket output = outputs.get(0);

        Object valueA = inputA.getValue();
        Object valueB = inputB.getValue();
        Object valueT = inputT.getValue();

        // Get interpolation factor
        float t = valueT instanceof Number ? ((Number) valueT).floatValue() : 0.0f;
        t = Math.max(0.0f, Math.min(1.0f, t)); // Clamp to [0, 1]

        // Handle different types
        Object result = null;

        if (valueA instanceof Float && valueB instanceof Float) {
            // Float lerp
            float a = (Float) valueA;
            float b = (Float) valueB;
            result = a * (1.0f - t) + b * t;
        } else if (valueA instanceof Vector3f && valueB instanceof Vector3f) {
            // Vec3 lerp (component-wise)
            Vector3f a = (Vector3f) valueA;
            Vector3f b = (Vector3f) valueB;
            result = new Vector3f(
                a.x * (1.0f - t) + b.x * t,
                a.y * (1.0f - t) + b.y * t,
                a.z * (1.0f - t) + b.z * t
            );
        } else if (valueA instanceof Vector4f && valueB instanceof Vector4f) {
            // Vec4 lerp (component-wise)
            Vector4f a = (Vector4f) valueA;
            Vector4f b = (Vector4f) valueB;
            result = new Vector4f(
                a.x * (1.0f - t) + b.x * t,
                a.y * (1.0f - t) + b.y * t,
                a.z * (1.0f - t) + b.z * t,
                a.w * (1.0f - t) + b.w * t
            );
        } else {
            // Default: treat as float
            float a = valueA instanceof Number ? ((Number) valueA).floatValue() : 0.0f;
            float b = valueB instanceof Number ? ((Number) valueB).floatValue() : 0.0f;
            result = a * (1.0f - t) + b * t;
        }

        output.setValue(result);
        context.setSocketValue(output, result);
    }
}
