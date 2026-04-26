package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Process node that performs math operations on float values.
 *
 * Operations:
 * - Add
 * - Subtract
 * - Multiply
 * - Divide
 * - Power
 * - Min/Max
 */
public class MathNode extends Node {

    public enum Operation {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("×"),
        DIVIDE("÷"),
        POWER("^"),
        MIN("min"),
        MAX("max");

        private final String symbol;

        Operation(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    private Operation operation = Operation.ADD;

    public MathNode() {
        super("Math");

        // Add input sockets
        NodeSocket inputA = new NodeSocket("A", NodeSocket.SocketType.FLOAT, 0.0f);
        NodeSocket inputB = new NodeSocket("B", NodeSocket.SocketType.FLOAT, 0.0f);
        addInput(inputA);
        addInput(inputB);

        // Add output socket
        NodeSocket output = new NodeSocket("Result", NodeSocket.SocketType.FLOAT, 0.0f);
        addOutput(output);

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw operation symbol in center
        // TODO: Add text rendering when available
        // For now, draw a simple indicator box
        float boxSize = 30;
        float boxX = x + (width - boxSize) / 2;
        float boxY = y + TITLE_BAR_HEIGHT + 20;

        paint.setColor(0xFF3A3A3A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, paint);

        paint.setColor(0xFFFFAA00);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, paint);
    }

    /**
     * Execute the math operation
     */
    public void execute() {
        // Get input values
        float a = 0.0f;
        float b = 0.0f;

        if (inputs.size() >= 2) {
            Object valA = inputs.get(0).getValue();
            Object valB = inputs.get(1).getValue();

            if (valA instanceof Float) a = (Float) valA;
            if (valB instanceof Float) b = (Float) valB;
        }

        // Perform operation
        float result = 0.0f;
        switch (operation) {
            case ADD:
                result = a + b;
                break;
            case SUBTRACT:
                result = a - b;
                break;
            case MULTIPLY:
                result = a * b;
                break;
            case DIVIDE:
                result = (b != 0.0f) ? (a / b) : 0.0f;
                break;
            case POWER:
                result = (float) Math.pow(a, b);
                break;
            case MIN:
                result = Math.min(a, b);
                break;
            case MAX:
                result = Math.max(a, b);
                break;
        }

        // Set output value
        if (!outputs.isEmpty()) {
            outputs.get(0).setValue(result);
        }
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
        execute(); // Recalculate
    }
}
