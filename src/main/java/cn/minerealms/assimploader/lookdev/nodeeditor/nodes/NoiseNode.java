package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Shader node that generates procedural noise.
 *
 * Supports multiple noise types:
 * - PERLIN: Classic Perlin noise
 * - SIMPLEX: Simplex noise (faster, better quality)
 * - WORLEY: Cellular/Worley noise (for organic patterns)
 *
 * Used for:
 * - Procedural textures
 * - Terrain generation
 * - Organic patterns
 * - Randomness in materials
 */
public class NoiseNode extends Node {

    public enum NoiseType {
        PERLIN("Perlin"),
        SIMPLEX("Simplex"),
        WORLEY("Worley");

        private final String displayName;

        NoiseType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private NoiseType noiseType = NoiseType.PERLIN;
    private float scale = 1.0f;

    public NoiseNode() {
        super("Noise");

        // Add input sockets
        addInput(new NodeSocket("Position", NodeSocket.SocketType.VECTOR, null));
        addInput(new NodeSocket("Scale", NodeSocket.SocketType.FLOAT, scale));

        // Add output sockets
        addOutput(new NodeSocket("Value", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Color", NodeSocket.SocketType.VECTOR, null));

        this.width = 160.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw a simple noise pattern visualization
        float patternY = y + TITLE_BAR_HEIGHT + 15;
        float patternSize = 8;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                float brightness = (float) Math.random();
                int color = (int) (brightness * 255);
                paint.setColor(0xFF000000 | (color << 16) | (color << 8) | color);
                paint.setStyle(Paint.FILL);
                canvas.drawRect(
                    x + 10 + i * patternSize,
                    patternY + j * patternSize,
                    x + 10 + (i + 1) * patternSize,
                    patternY + (j + 1) * patternSize,
                    paint
                );
            }
        }
    }

    public NoiseType getNoiseType() {
        return noiseType;
    }

    public void setNoiseType(NoiseType noiseType) {
        this.noiseType = noiseType;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        if (!inputs.isEmpty() && inputs.size() > 1) {
            inputs.get(1).setValue(scale);
        }
    }
}
