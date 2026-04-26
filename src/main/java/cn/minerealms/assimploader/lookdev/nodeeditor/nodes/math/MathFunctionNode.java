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
        SIN("Sin"),
        COS("Cos"),
        TAN("Tan"),
        SQRT("Sqrt"),
        ABS("Abs"),
        FLOOR("Floor"),
        CEIL("Ceil"),
        ROUND("Round"),
        EXP("Exp"),
        LOG("Log"),
        POW("Pow");

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
        addInput(new EnhancedNodeSocket("Exponent", EnhancedSocketType.FLOAT, 2.0f)); // For POW
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

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);

        // Draw function symbol (f(x))
        float symbolSize = 15;
        // Draw a simple wave to represent function
        canvas.drawLine(centerX - symbolSize, bodyY - 5, centerX - symbolSize / 2, bodyY + 5, paint);
        canvas.drawLine(centerX - symbolSize / 2, bodyY + 5, centerX, bodyY - 5, paint);
        canvas.drawLine(centerX, bodyY - 5, centerX + symbolSize / 2, bodyY + 5, paint);
    }

    @Override
    protected void computeOutputs(NodeEvaluationContext context) {
        float value = ((Number) inputs.get(0).getValue()).floatValue();
        float exponent = ((Number) inputs.get(1).getValue()).floatValue();
        float result;

        switch (function) {
            case SIN:
                result = (float) Math.sin(value);
                break;
            case COS:
                result = (float) Math.cos(value);
                break;
            case TAN:
                result = (float) Math.tan(value);
                break;
            case SQRT:
                result = (float) Math.sqrt(Math.max(0, value));
                break;
            case ABS:
                result = Math.abs(value);
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
            case EXP:
                result = (float) Math.exp(value);
                break;
            case LOG:
                result = value > 0 ? (float) Math.log(value) : 0.0f;
                break;
            case POW:
                result = (float) Math.pow(value, exponent);
                break;
            default:
                result = value;
        }

        outputs.get(0).setValue(result);
        context.setSocketValue(outputs.get(0), result);
    }
}
