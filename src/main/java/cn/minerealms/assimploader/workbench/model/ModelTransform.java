package cn.minerealms.assimploader.workbench.model;

import org.joml.Matrix4f;

/** Metres, intrinsic XYZ degrees and independent axis scale. Does not alter source vertices. */
public record ModelTransform(float x, float y, float z, float rx, float ry, float rz,
                             float sx, float sy, float sz) {
    public static final ModelTransform IDENTITY = new ModelTransform(0, 0, 0, 0, 0, 0, 1, 1, 1);

    public ModelTransform {
        bounded(x, 1_000_000, "position"); bounded(y, 1_000_000, "position"); bounded(z, 1_000_000, "position");
        bounded(rx, 36_000, "rotation"); bounded(ry, 36_000, "rotation"); bounded(rz, 36_000, "rotation");
        scale(sx); scale(sy); scale(sz);
    }

    public Matrix4f matrix() {
        return new Matrix4f().translation(x, y, z).rotateXYZ((float) Math.toRadians(rx),
                (float) Math.toRadians(ry), (float) Math.toRadians(rz)).scale(sx, sy, sz);
    }

    public ModelTransform withPosition(float a, float b, float c) { return new ModelTransform(a,b,c,rx,ry,rz,sx,sy,sz); }
    public ModelTransform withRotation(float a, float b, float c) { return new ModelTransform(x,y,z,a,b,c,sx,sy,sz); }
    public ModelTransform withScale(float a, float b, float c) { return new ModelTransform(x,y,z,rx,ry,rz,a,b,c); }

    private static void bounded(float value, float limit, String what) {
        if (!Float.isFinite(value) || Math.abs(value) > limit) throw new IllegalArgumentException("Invalid " + what);
    }
    private static void scale(float value) {
        if (!Float.isFinite(value) || Math.abs(value) < .000001f || Math.abs(value) > 100_000)
            throw new IllegalArgumentException("Scale magnitude must be between 0.000001 and 100000");
    }
}
