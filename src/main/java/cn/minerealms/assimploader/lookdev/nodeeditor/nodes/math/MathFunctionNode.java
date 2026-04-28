package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEvaluationContext;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Math Function Node
 *
 * Applies mathematical functions to input values.
 *
 * Inputs:
 * - Value (float): Input value (default: 0.0)
 *
 * Outputs:
 * - Result (float): Function result
 *
 * Functions:
 * - Sin, Cos, Tan: Trigonometric functions
 * - Sqrt: Square root
 * - Abs: Absolute value
 * - Floor, Ceil, Round: Rounding functions
 * - Exp, Log: Exponential and logarithm
 * - Pow: Power (requires second input)
 */
public class MathFunctionNode extends Node {

    public enum Function {
        // Trigonometric
        SIN("Sin"),
        COS("Cos"),
        TAN("Tan"),
        ASIN("Asin"),
        ACOS("Acos"),
        ATAN("Atan"),

        // Exponential & Logarithmic
        SQRT("Sqrt"),
        EXP("Exp"),
        LOG("Log"),
        POW("Pow"),

        // Rounding
        ABS("Abs"),
        SIGN("Sign"),
        FLOOR("Floor"),
        CEIL("Ceil"),
        ROUND("Round"),
        FRACT("Fract"),

        // Comparison
        MIN("Min"),
        MAX("Max"),
        CLAMP("Clamp"),

        // Interpolation
        LERP("Lerp"),
        SMOOTHSTEP("Smoothstep"),

        // Modulo
        MOD("Mod");

        private final String displayName;

        Function(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Function function = Function.SIN;

    public MathFunctionNode() {
        super("Math Function");

        addInput(new EnhancedNodeSocket("Value", EnhancedSocketType.FLOAT, 0.0f));
        addInput(new EnhancedNodeSocket("B", EnhancedSocketType.FLOAT, 2.0f)); // For POW, MIN, MAX, MOD
        addInput(new EnhancedNodeSocket("Min", EnhancedSocketType.FLOAT, 0.0f)); // For CLAMP, SMOOTHSTEP (Edge0)
        addInput(new EnhancedNodeSocket("Max", EnhancedSocketType.FLOAT, 1.0f)); // For CLAMP, SMOOTHSTEP (Edge1), LERP (T)
        addOutput(new EnhancedNodeSocket("Result", EnhancedSocketType.FLOAT));

        this.width = 160.0f;
    }

    public Function getFunction() {
        return function;
    }

    public void setFunction(Function function) {
        this.function = function;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;
        float graphWidth = 60;
        float graphHeight = 30;
        float graphLeft = centerX - graphWidth / 2;
        float graphTop = bodyY - graphHeight / 2;

        // Draw graph background
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(graphLeft, graphTop, graphLeft + graphWidth, graphTop + graphHeight, paint);

        // Draw function curve
        paint.setColor(0xFF00AAFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        int samples = 40;
        float prevX = graphLeft;
        float prevY = evaluateFunctionForPreview(0.0f, graphTop, graphHeight);

        for (int i = 1; i <= samples; i++) {
            float t = i / (float) samples;
            float x = graphLeft + t * graphWidth;
            float y = evaluateFunctionForPreview(t, graphTop, graphHeight);

            canvas.drawLine(prevX, prevY, x, y, paint);
            prevX = x;
            prevY = y;
        }

        // Draw border
        paint.setColor(0xFF666666);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(graphLeft, graphTop, graphLeft + graphWidth, graphTop + graphHeight, paint);
    }

    /**
     * Evaluate function for preview visualization
     * @param t Input value normalized to [0, 1]
     * @param graphTop Top of graph area
     * @param graphHeight Height of graph area
     * @return Y coordinate for drawing
     */
    private float evaluateFunctionForPreview(float t, float graphTop, float graphHeight) {
        // Map t from [0,1] to appropriate input range for the function
        float input;
        float output;

        switch (function) {
            // Trigonometric - use [0, 2π] range
            case SIN:
            case COS:
            case TAN:
                input = t * (float) (Math.PI * 2);
                break;
            // Inverse trig - use [-1, 1] range
            case ASIN:
            case ACOS:
                input = t * 2 - 1;
                break;
            case ATAN:
                input = t * 4 - 2; // [-2, 2]
                break;
            // Exponential - use [0, 2] range
            case SQRT:
            case EXP:
            case LOG:
                input = t * 2;
                break;
            // Others - use [-1, 1] or [0, 1] range
            default:
                input = t * 2 - 1;
                break;
        }

        // Evaluate function
        switch (function) {
            case SIN:
                output = (float) Math.sin(input);
                break;
            case COS:
                output = (float) Math.cos(input);
                break;
            case TAN:
                output = (float) Math.tan(input);
                output = Math.max(-2, Math.min(2, output)); // Clamp for display
                break;
            case ASIN:
                output = (float) Math.asin(Math.max(-1, Math.min(1, input)));
                break;
            case ACOS:
                output = (float) Math.acos(Math.max(-1, Math.min(1, input)));
                break;
            case ATAN:
                output = (float) Math.atan(input);
                break;
            case SQRT:
                output = (float) Math.sqrt(Math.max(0, input));
                break;
            case EXP:
                output = (float) Math.exp(input);
                output = Math.min(4, output); // Clamp for display
                break;
            case LOG:
                output = input > 0 ? (float) Math.log(input) : -2;
                break;
            case POW:
                output = (float) Math.pow(Math.abs(input), 2);
                break;
            case ABS:
                output = Math.abs(input);
                break;
            case SIGN:
                output = Math.signum(input);
                break;
            case FLOOR:
                output = (float) Math.floor(input);
                break;
            case CEIL:
                output = (float) Math.ceil(input);
                break;
            case ROUND:
                output = Math.round(input);
                break;
            case FRACT:
                output = input - (float) Math.floor(input);
                break;
            case MIN:
            case MAX:
            case CLAMP:
            case LERP:
            case SMOOTHSTEP:
            case MOD:
                // These need multiple inputs, show a simple line
                output = input;
                break;
            default:
                output = input;
        }

        // Normalize output to graph space
        // Map output range to [0, 1] for display
        float normalizedOutput;
        switch (function) {
            case SIN:
            case COS:
            case SIGN:
                normalizedOutput = (output + 1) / 2; // [-1, 1] -> [0, 1]
                break;
            case TAN:
                normalizedOutput = (output + 2) / 4; // [-2, 2] -> [0, 1]
                break;
            case ASIN:
            case ACOS:
            case ATAN:
                normalizedOutput = (output + (float) Math.PI / 2) / (float) Math.PI; // [-π/2, π/2] -> [0, 1]
                break;
            case EXP:
                normalizedOutput = output / 4; // [0, 4] -> [0, 1]
                break;
            case LOG:
                normalizedOutput = (output + 2) / 4; // [-2, 2] -> [0, 1]
                break;
            default:
                normalizedOutput = (output + 1) / 2; // Default: [-1, 1] -> [0, 1]
                break;
        }

        // Clamp and invert Y (canvas Y increases downward)
        normalizedOutput = Math.max(0, Math.min(1, normalizedOutput));
        return graphTop + graphHeight * (1 - normalizedOutput);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        float value = ((Number) inputs.get(0).getValue()).floatValue();
        float b = ((Number) inputs.get(1).getValue()).floatValue();
        float min = ((Number) inputs.get(2).getValue()).floatValue();
        float max = ((Number) inputs.get(3).getValue()).floatValue();
        float result;

        switch (function) {
            // Trigonometric
            case SIN:
                result = (float) Math.sin(value);
                break;
            case COS:
                result = (float) Math.cos(value);
                break;
            case TAN:
                result = (float) Math.tan(value);
                break;
            case ASIN:
                result = (float) Math.asin(Math.max(-1, Math.min(1, value)));
                break;
            case ACOS:
                result = (float) Math.acos(Math.max(-1, Math.min(1, value)));
                break;
            case ATAN:
                result = (float) Math.atan(value);
                break;

            // Exponential & Logarithmic
            case SQRT:
                result = (float) Math.sqrt(Math.max(0, value));
                break;
            case EXP:
                result = (float) Math.exp(value);
                break;
            case LOG:
                result = value > 0 ? (float) Math.log(value) : 0.0f;
                break;
            case POW:
                result = (float) Math.pow(value, b);
                break;

            // Rounding
            case ABS:
                result = Math.abs(value);
                break;
            case SIGN:
                result = Math.signum(value);
                break;
            case FLOOR:
                result = (float) Math.floor(value);
                break;
            case CEIL:
                result = (float) Math.ceil(value);
                break;
            case ROUND:
                result = Math.round(value);
                break;
            case FRACT:
                result = value - (float) Math.floor(value);
                break;

            // Comparison
            case MIN:
                result = Math.min(value, b);
                break;
            case MAX:
                result = Math.max(value, b);
                break;
            case CLAMP:
                result = Math.max(min, Math.min(max, value));
                break;

            // Interpolation
            case LERP:
                // LERP(A, B, T) = A + (B - A) * T
                // value = A, b = B, max = T
                result = value + (b - value) * max;
                break;
            case SMOOTHSTEP:
                // SMOOTHSTEP(Edge0, Edge1, X) - Hermite interpolation
                // min = Edge0, max = Edge1, value = X
                float t = Math.max(0, Math.min(1, (value - min) / (max - min)));
                result = t * t * (3 - 2 * t);
                break;

            // Modulo
            case MOD:
                result = value % b;
                break;

            default:
                result = value;
        }

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
