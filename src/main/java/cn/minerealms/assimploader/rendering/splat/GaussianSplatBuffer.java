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
        this.shDim = (shDegree + 1) * (shDegree + 1);
        this.stride = 11 + shDim * 3;
    }

    /**
     * Pack splat data into a FloatBuffer for GPU upload
     */
    public FloatBuffer pack(GaussianSplatData data) {
        int splatCount = data.getSplatCount();
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(splatCount * stride * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        FloatBuffer buffer = byteBuffer.asFloatBuffer();

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
        for (float v : sh) {
            buffer.put(v);
        }

        // Pad if necessary to match expected shDim
        int actualShDim = sh.length / 3;
        if (actualShDim < shDim) {
            for (int i = actualShDim * 3; i < shDim * 3; i++) {
                buffer.put(0.0f);
            }
        }
    }

    public int getStride() {
        return stride;
    }

    public int getShDim() {
        return shDim;
    }
}
