package cn.minerealms.assimploader.rendering.splat.data;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable Gaussian splat data structure.
 *
 * Represents a single 3D Gaussian with position, rotation, scale, opacity, and spherical harmonics.
 * Scale and opacity are stored in log-space for numerical stability.
 */
public class GaussianSplat {

    private final Vector3f position;
    private final Quaternionf rotation; // JOML quaternion; packed on the GPU as w, x, y, z.
    private final Vector3f logScale;    // log-space scale
    private final float logitOpacity;   // logit-space opacity
    private final float[] sh;           // spherical harmonics coefficients (shDim * 3)

    public GaussianSplat(Vector3f position, Quaternionf rotation, Vector3f logScale,
                         float logitOpacity, float[] sh) {
        if (!position.isFinite() || !logScale.isFinite() || !rotation.isFinite() || Float.isNaN(logitOpacity)) {
            throw new IllegalArgumentException("Non-finite Gaussian splat attributes");
        }
        this.position = new Vector3f(position);
        double length = Math.sqrt((double) rotation.x * rotation.x + (double) rotation.y * rotation.y
                + (double) rotation.z * rotation.z + (double) rotation.w * rotation.w);
        this.rotation = length < 1e-12 ? new Quaternionf() : new Quaternionf(
                (float) (rotation.x / length), (float) (rotation.y / length),
                (float) (rotation.z / length), (float) (rotation.w / length));
        this.logScale = new Vector3f(logScale);
        this.logitOpacity = logitOpacity;
        this.sh = sh.clone();
    }

    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public Vector3f getPosition(Vector3f destination) { return destination.set(position); }

    public Quaternionf getRotation() {
        return new Quaternionf(rotation);
    }

    public Vector3f getLogScale() {
        return new Vector3f(logScale);
    }

    public Vector3f getScale() {
        return new Vector3f(
            boundedExp(logScale.x),
            boundedExp(logScale.y),
            boundedExp(logScale.z)
        );
    }

    public float getLogitOpacity() {
        return logitOpacity;
    }

    public float getOpacity() {
        return sigmoid(logitOpacity);
    }

    public float[] getSH() {
        return sh.clone();
    }

    public int getShDim() {
        return sh.length / 3;
    }

    private static float sigmoid(float x) {
        return 1.0f / (1.0f + (float) Math.exp(-x));
    }

    private static float boundedExp(float value) { return (float) Math.exp(Math.max(-40, Math.min(40, value))); }
}
