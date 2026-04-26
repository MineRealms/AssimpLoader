package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Output node that displays the final result.
 *
 * This is the terminal node in the graph.
 * Multiple output nodes can exist for different purposes:
 * - Material Output (PBR parameters)
 * - Preview Output (direct visualization)
 * - Debug Output (intermediate values)
 */
public class OutputNode extends Node {

    public enum OutputType {
        MATERIAL("Material"),
        PREVIEW("Preview"),
        DEBUG("Debug");

        private final String displayName;

        OutputType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private OutputType outputType = OutputType.MATERIAL;

    public OutputNode() {
        super("Output");

        // Add input sockets for PBR material
        addInput(new NodeSocket("Base Color", NodeSocket.SocketType.COLOR));
        addInput(new NodeSocket("Metallic", NodeSocket.SocketType.FLOAT));
        addInput(new NodeSocket("Roughness", NodeSocket.SocketType.FLOAT));
        addInput(new NodeSocket("Normal", NodeSocket.SocketType.TEXTURE));
        addInput(new NodeSocket("Emissive", NodeSocket.SocketType.COLOR));

        this.width = 180.0f;
        this.titleBarColor = 0xFF2A4A2A; // Green tint for output
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw output indicator
        float indicatorSize = 20;
        float indicatorX = x + (width - indicatorSize) / 2;
        float indicatorY = y + height - indicatorSize - 10;

        // Draw circle indicator
        paint.setColor(0xFF00FF00);
        paint.setStyle(Paint.FILL);
        canvas.drawCircle(
            indicatorX + indicatorSize / 2,
            indicatorY + indicatorSize / 2,
            indicatorSize / 2,
            paint
        );

        // Draw border
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawCircle(
            indicatorX + indicatorSize / 2,
            indicatorY + indicatorSize / 2,
            indicatorSize / 2,
            paint
        );
    }

    /**
     * Get the final material output values
     */
    public MaterialOutput getMaterialOutput() {
        MaterialOutput output = new MaterialOutput();

        if (inputs.size() >= 5) {
            output.baseColor = inputs.get(0).getValue();
            output.metallic = inputs.get(1).getValue();
            output.roughness = inputs.get(2).getValue();
            output.normal = inputs.get(3).getValue();
            output.emissive = inputs.get(4).getValue();
        }

        return output;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }

    /**
     * Container for material output values
     */
    public static class MaterialOutput {
        public Object baseColor;
        public Object metallic;
        public Object roughness;
        public Object normal;
        public Object emissive;
    }
}
