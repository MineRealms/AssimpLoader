package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Normal Map Node
 *
 * Samples a normal map texture and converts it to tangent-space normals.
 *
 * Inputs:
 * - Normal Map (sampler2D): Normal map texture
 * - UV (vec2): Texture coordinates
 * - Strength (float): Normal map intensity (0.0 = flat, 1.0 = full strength)
 *
 * Outputs:
 * - Normal (vec3): Tangent-space normal vector
 *
 * Common uses:
 * - Adding surface detail without geometry
 * - PBR material normal input
 */
public class NormalMapNode extends Node {

    private float strength = 1.0f;

    public NormalMapNode() {
        super("Normal Map");

        // Add input sockets
        addInput(new EnhancedNodeSocket("Normal Map", EnhancedSocketType.SAMPLER2D));
        addInput(new EnhancedNodeSocket("UV", EnhancedSocketType.VEC2));
        addInput(new EnhancedNodeSocket("Strength", EnhancedSocketType.FLOAT));

        // Add output socket
        addOutput(new EnhancedNodeSocket("Normal", EnhancedSocketType.VEC3));

        this.width = 170.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 10;
        float bodyX = x + 10;
        float bodyWidth = width - 20;

        // Draw normal map indicator (blue-ish box)
        paint.setColor(0xFF8080FF);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(bodyX, bodyY, bodyX + bodyWidth, bodyY + 30, paint);

        // Draw strength indicator
        float barY = bodyY + 35;
        float barHeight = 8;

        // Background
        paint.setColor(0xFF1A1A1A);
        canvas.drawRect(bodyX, barY, bodyX + bodyWidth, barY + barHeight, paint);

        // Strength bar
        paint.setColor(0xFF00AAFF);
        canvas.drawRect(bodyX, barY, bodyX + bodyWidth * strength, barY + barHeight, paint);

        // Border
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(bodyX, barY, bodyX + bodyWidth, barY + barHeight, paint);
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = Math.max(0.0f, Math.min(1.0f, strength));
    }
}
