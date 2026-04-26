package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.vector;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import org.joml.Vector3f;

/**
 * Vector Math Node
 *
 * Performs mathematical operations on vectors.
 *
 * Inputs:
 * - A (vec3): First vector
 * - B (vec3): Second vector
 *
 * Outputs:
 * - Result (vec3): Operation result
 *
 * Operations:
 * - Add: A + B
 * - Subtract: A - B
 * - Multiply: A * B (component-wise)
 * - Divide: A / B (component-wise)
 * - Dot Product: dot(A, B) (scalar result)
 * - Cross Product: cross(A, B)
 * - Normalize: normalize(A)
 * - Length: length(A) (scalar result)
 */
public class VectorMathNode extends Node {

    public enum Operation {
        ADD("Add"),
        SUBTRACT("Subtract"),
        MULTIPLY("Multiply"),
        DIVIDE("Divide"),
        DOT_PRODUCT("Dot Product"),
        CROSS_PRODUCT("Cross Product"),
        NORMALIZE("Normalize"),
        LENGTH("Length");

        private final String displayName;

        Operation(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Operation operation = Operation.ADD;

    public VectorMathNode() {
        super("Vector Math");

        // Add input sockets
        addInput(new EnhancedNodeSocket("A", EnhancedSocketType.VEC3, new Vector3f(0, 0, 0)));
        addInput(new EnhancedNodeSocket("B", EnhancedSocketType.VEC3, new Vector3f(0, 0, 0)));

        // Add output socket
        addOutput(new EnhancedNodeSocket("Result", EnhancedSocketType.VEC3));

        this.width = 160.0f;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        // Draw vector symbol (arrow)
        float symbolSize = 15;
        canvas.drawLine(centerX - symbolSize, bodyY, centerX + symbolSize, bodyY, paint);
        canvas.drawLine(centerX + symbolSize, bodyY, centerX + symbolSize - 5, bodyY - 5, paint);
        canvas.drawLine(centerX + symbolSize, bodyY, centerX + symbolSize - 5, bodyY + 5, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        Object aObj = inputs.get(0).getValue();
        Object bObj = inputs.get(1).getValue();

        Vector3f a = aObj instanceof Vector3f ? (Vector3f) aObj : new Vector3f(0, 0, 0);
        Vector3f b = bObj instanceof Vector3f ? (Vector3f) bObj : new Vector3f(0, 0, 0);

        Vector3f result;

        switch (operation) {
            case ADD:
                result = new Vector3f(a.x + b.x, a.y + b.y, a.z + b.z);
                break;

            case SUBTRACT:
                result = new Vector3f(a.x - b.x, a.y - b.y, a.z - b.z);
                break;

            case MULTIPLY:
                result = new Vector3f(a.x * b.x, a.y * b.y, a.z * b.z);
                break;

            case DIVIDE:
                result = new Vector3f(
                    b.x != 0 ? a.x / b.x : 0,
                    b.y != 0 ? a.y / b.y : 0,
                    b.z != 0 ? a.z / b.z : 0
                );
                break;

            case DOT_PRODUCT:
                float dot = a.x * b.x + a.y * b.y + a.z * b.z;
                result = new Vector3f(dot, dot, dot);
                break;

            case CROSS_PRODUCT:
                result = new Vector3f(
                    a.y * b.z - a.z * b.y,
                    a.z * b.x - a.x * b.z,
                    a.x * b.y - a.y * b.x
                );
                break;

            case NORMALIZE:
                float length = (float) Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
                if (length > 0.0001f) {
                    result = new Vector3f(a.x / length, a.y / length, a.z / length);
                } else {
                    result = new Vector3f(0, 0, 0);
                }
                break;

            case LENGTH:
                float len = (float) Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
                result = new Vector3f(len, len, len);
                break;

            default:
                result = new Vector3f(0, 0, 0);
        }

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
