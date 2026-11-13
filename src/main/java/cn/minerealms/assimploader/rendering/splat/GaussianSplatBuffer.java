package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplat;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Packs Gaussian splat data into GPU buffer format.
 *
 * Buffer layout per splat:
 * - position (3 floats)
 * - rotation (4 floats: w, x, y, z)
 * - scale (3 floats: exp(logScale))
 * - opacity (1 float: sigmoid(logit))
 * - sh coefficients (shDim * 3 floats)
 *
 * Total stride: (11 + shDim * 3) floats
 */
public class GaussianSplatBuffer {

    private final int shDim;
    private final int stride; // floats per splat

    public GaussianSplatBuffer(int shDegree) {
        if (shDegree < 0 || shDegree > 3) throw new IllegalArgumentException("SH degree must be 0-3");
        this.shDim = (shDegree + 1) * (shDegree + 1);
        this.stride = 11 + shDim * 3;
    }

    /**
     * Pack splat data into a FloatBuffer for GPU upload
     */
    public FloatBuffer pack(GaussianSplatData data) {
        int splatCount = data.getSplatCount();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(splatCount, stride), Float.BYTES))
                .order(ByteOrder.nativeOrder());
        return pack(data, byteBuffer.asFloatBuffer());
    }

    /** Packs into caller-owned storage so GPU upload can release native staging immediately. */
    public FloatBuffer pack(GaussianSplatData data, FloatBuffer buffer) {
        int values = Math.multiplyExact(data.getSplatCount(), stride);
        if (buffer.capacity() < values) throw new IllegalArgumentException("Splat packing buffer is too small");
        buffer.clear();
        for (GaussianSplat splat : data.getSplats()) {
            packSplat(buffer, splat);
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Pack a single splat into the buffer
     */
    private void packSplat(FloatBuffer buffer, GaussianSplat splat) {
        // Position (3 floats)
        Vector3f pos = splat.getPosition();
        buffer.put(pos.x);
        buffer.put(pos.y);
        buffer.put(pos.z);

        // Rotation (4 floats: w, x, y, z)
        Quaternionf rot = splat.getRotation();
        buffer.put(rot.w);
        buffer.put(rot.x);
        buffer.put(rot.y);
        buffer.put(rot.z);

        // Scale (3 floats: exp(logScale))
        Vector3f scale = splat.getScale();
        buffer.put(scale.x);
        buffer.put(scale.y);
        buffer.put(scale.z);

        // Opacity (1 float: sigmoid(logit))
        buffer.put(splat.getOpacity());

        // SH coefficients (shDim * 3 floats)
        float[] sh = splat.getSH();
        int expected = shDim * 3;
        for (int i = 0; i < expected; i++) buffer.put(i < sh.length && Float.isFinite(sh[i]) ? sh[i] : 0);
    }

    public int getStride() {
        return stride;
    }

    public int getShDim() {
        return shDim;
    }
}
