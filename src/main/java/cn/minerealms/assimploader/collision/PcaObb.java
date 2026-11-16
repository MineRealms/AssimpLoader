package cn.minerealms.assimploader.collision;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.Optional;

/** Immutable, conservative PCA enclosure. PCA chooses axes; every input point determines extents. */
public final class PcaObb implements CollisionShape {
    public record Limits(int maxVertices, int maxCovarianceSamples) {
        public static final Limits DEFAULT = new Limits(1_000_000, 4096);
        public Limits {
            if (maxVertices < 1 || maxVertices > 10_000_000 || maxCovarianceSamples < 3
                    || maxCovarianceSamples > 65_536) throw new IllegalArgumentException("Invalid PCA workload limits");
        }
    }

    private final Matrix4f transform;
    private final Vector3f minimum, maximum;
    private final ObbCollider collider;
    private final int sampleCount;

    private PcaObb(Matrix4f transform, Vector3f minimum, Vector3f maximum, int samples) {
        this.transform = transform;
        this.minimum = minimum;
        this.maximum = maximum;
        this.sampleCount = samples;
        collider = new ObbCollider(minimum, maximum, transform);
    }

    public static PcaObb fit(float[] positions) { return fit(positions, Limits.DEFAULT); }

    /** O(vertices + samples), fixed solver bound and constant scratch storage. Empty input is invalid. */
    public static PcaObb fit(float[] positions, Limits limits) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(limits, "limits");
        if (positions.length == 0 || positions.length % 3 != 0) throw new IllegalArgumentException("Nonempty XYZ layout required");
        int count = positions.length / 3;
        if (count > limits.maxVertices()) throw new IllegalArgumentException("PCA vertex budget exceeded: " + count);
        int samples = Math.min(count, limits.maxCovarianceSamples());
        double[] mean = new double[3];
        double[][] covariance = new double[3][3];
        double[] delta = new double[3];
        for (int sample = 0; sample < samples; sample++) {
            int offset = (samples == 1 ? 0 : (int)((long)sample * (count - 1) / (samples - 1))) * 3;
            for (int axis = 0; axis < 3; axis++) {
                float value = positions[offset + axis];
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite PCA position");
                delta[axis] = value - mean[axis];
                mean[axis] += delta[axis] / (sample + 1);
            }
            for (int row = 0; row < 3; row++) for (int column = row; column < 3; column++) {
                covariance[row][column] += delta[row] * (positions[offset + column] - mean[column]);
                covariance[column][row] = covariance[row][column];
            }
        }
        double[][] basis = diagonalize(covariance);
        int[] order = {0, 1, 2};
        for (int i = 0; i < 2; i++) for (int j = i + 1; j < 3; j++)
            if (covariance[order[j]][order[j]] > covariance[order[i]][order[i]]) {
                int swap = order[i]; order[i] = order[j]; order[j] = swap;
            }
        Vector3f x = column(basis, order[0]);
        Vector3f y = column(basis, order[1]);
        // A line has no unique minor axes; pick the least-aligned world axis deterministically.
        if (covariance[order[1]][order[1]] <= Math.max(0, covariance[order[0]][order[0]]) * 1e-12) {
            y.set(Math.abs(x.x) <= Math.abs(x.y) && Math.abs(x.x) <= Math.abs(x.z) ? 1 : 0,
                    Math.abs(x.y) < Math.abs(x.x) && Math.abs(x.y) <= Math.abs(x.z) ? 1 : 0,
                    Math.abs(x.z) < Math.abs(x.x) && Math.abs(x.z) < Math.abs(x.y) ? 1 : 0);
        }
        canonicalize(x);
        y.fma(-x.dot(y), x).normalize();
        canonicalize(y);
        Vector3f z = new Vector3f(x).cross(y).normalize();
        y.set(z).cross(x).normalize();
        Vector3f center = new Vector3f((float)mean[0], (float)mean[1], (float)mean[2]);
        Matrix4f transform = new Matrix4f().setColumn(0, new org.joml.Vector4f(x, 0))
                .setColumn(1, new org.joml.Vector4f(y, 0)).setColumn(2, new org.joml.Vector4f(z, 0))
                .setTranslation(center);
        // Project using the actual float matrix inverse rather than idealized double eigenvectors.
        Matrix4f inverse = new Matrix4f(transform).invert();
        double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double coordinateMagnitude = 0;
        for (int offset = 0; offset < positions.length; offset += 3) {
            double px = positions[offset], py = positions[offset + 1], pz = positions[offset + 2];
            if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)) throw new IllegalArgumentException("Non-finite PCA position");
            coordinateMagnitude = Math.max(coordinateMagnitude, Math.max(Math.abs(px), Math.max(Math.abs(py), Math.abs(pz))));
            for (int axis = 0; axis < 3; axis++) {
                double projected = inverse.get(0, axis) * px + inverse.get(1, axis) * py
                        + inverse.get(2, axis) * pz + inverse.get(3, axis);
                low[axis] = Math.min(low[axis], projected); high[axis] = Math.max(high[axis], projected);
            }
        }
        Vector3f minimum = new Vector3f(), maximum = new Vector3f();
        double padding = Math.max(1e-6, coordinateMagnitude * 8 * Math.ulp(1.0f));
        for (int axis = 0; axis < 3; axis++) {
            double a = low[axis] - padding, b = high[axis] + padding;
            if (a < -Float.MAX_VALUE || b > Float.MAX_VALUE) throw new IllegalArgumentException("PCA extents exceed float range");
            minimum.setComponent(axis, Math.nextDown((float)a));
            maximum.setComponent(axis, Math.nextUp((float)b));
        }
        return new PcaObb(transform, minimum, maximum, samples);
    }

    /** Symmetric Jacobi eigensolver with at most 32 rotations on the 3x3 covariance. */
    private static double[][] diagonalize(double[][] matrix) {
        double[][] vectors = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int iteration = 0; iteration < 32; iteration++) {
            int p = 0, q = 1;
            if (Math.abs(matrix[0][2]) > Math.abs(matrix[p][q])) { p = 0; q = 2; }
            if (Math.abs(matrix[1][2]) > Math.abs(matrix[p][q])) { p = 1; q = 2; }
            double off = matrix[p][q];
            double magnitude = Math.abs(matrix[0][0]) + Math.abs(matrix[1][1]) + Math.abs(matrix[2][2]);
            if (Math.abs(off) <= magnitude * 1e-15 || off == 0) break;
            double tau = (matrix[q][q] - matrix[p][p]) / (2 * off);
            double t = Math.copySign(1, tau) / (Math.abs(tau) + Math.hypot(1, tau));
            double c = 1 / Math.sqrt(1 + t * t), s = t * c;
            matrix[p][p] -= t * off;
            matrix[q][q] += t * off;
            matrix[p][q] = matrix[q][p] = 0;
            for (int k = 0; k < 3; k++) {
                if (k != p && k != q) {
                    double kp = matrix[k][p], kq = matrix[k][q];
                    matrix[k][p] = matrix[p][k] = c * kp - s * kq;
                    matrix[k][q] = matrix[q][k] = s * kp + c * kq;
                }
                double vp = vectors[k][p], vq = vectors[k][q];
                vectors[k][p] = c * vp - s * vq;
                vectors[k][q] = s * vp + c * vq;
            }
        }
        return vectors;
    }

    private static Vector3f column(double[][] matrix, int column) {
        return new Vector3f((float)matrix[0][column], (float)matrix[1][column], (float)matrix[2][column]).normalize();
    }

    private static void canonicalize(Vector3f axis) {
        float dominant = Math.abs(axis.x) >= Math.abs(axis.y) && Math.abs(axis.x) >= Math.abs(axis.z) ? axis.x
                : Math.abs(axis.y) >= Math.abs(axis.z) ? axis.y : axis.z;
        if (dominant < 0) axis.negate();
    }

    public Matrix4f transform() { return new Matrix4f(transform); }
    public Vector3f minimum() { return new Vector3f(minimum); }
    public Vector3f maximum() { return new Vector3f(maximum); }
    public int covarianceSamples() { return sampleCount; }
    @Override public Optional<RayHit> raycast(Ray ray, float maximumDistance) { return collider.raycast(ray, maximumDistance); }
}
