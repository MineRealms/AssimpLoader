package cn.minerealms.assimploader.client.transform;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Objects;

/** Metre offsets, intrinsic XYZ Euler degrees, and uniform scale for transform editors. */
public record EditableTransform(float x, float y, float z, float rx, float ry, float rz, float scale) {
    public EditableTransform {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || Math.abs(x) > 10 || Math.abs(y) > 10 || Math.abs(z) > 10)
            throw new IllegalArgumentException("Transform position must be finite metres in [-10,10]");
        if (!Float.isFinite(rx) || !Float.isFinite(ry) || !Float.isFinite(rz)
                || Math.abs(rx) > 3600 || Math.abs(ry) > 3600 || Math.abs(rz) > 3600)
            throw new IllegalArgumentException("Transform XYZ rotation must be finite degrees in [-3600,3600]");
        if (!Float.isFinite(scale) || scale < .001f || scale > 10)
            throw new IllegalArgumentException("Transform scale must be in [0.001,10]");
    }

    public Quaternionf quaternion() {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(rx), (float) Math.toRadians(ry),
                (float) Math.toRadians(rz)).normalize();
    }

    public static EditableTransform from(float x, float y, float z, Quaternionfc rotation, float scale) {
        Objects.requireNonNull(rotation, "rotation");
        double qx = rotation.x(), qy = rotation.y(), qz = rotation.z(), qw = rotation.w();
        double length = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (!Double.isFinite(length) || length < 1e-8) throw new IllegalArgumentException("Invalid transform quaternion");
        qx /= length; qy /= length; qz /= length; qw /= length;
        double sinY = Math.max(-1, Math.min(1, 2 * (qx * qz + qy * qw)));
        double yAngle = Math.asin(sinY), xAngle, zAngle;
        // Preserve orientation at XYZ gimbal lock; independent atan2 pairs lose the coupled angle.
        if (1 - sinY * sinY < 1e-12) {
            xAngle = Math.atan2(Math.copySign(1, sinY) * 2 * (qx * qy + qz * qw),
                    1 - 2 * (qx * qx + qz * qz));
            zAngle = 0;
        } else {
            xAngle = Math.atan2(2 * (qx * qw - qy * qz), 1 - 2 * (qx * qx + qy * qy));
            zAngle = Math.atan2(2 * (qz * qw - qx * qy), 1 - 2 * (qy * qy + qz * qz));
        }
        return new EditableTransform(x, y, z, (float) Math.toDegrees(xAngle), (float) Math.toDegrees(yAngle),
                (float) Math.toDegrees(zAngle), scale);
    }
}
