package cn.minerealms.assimploader.lookdev.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Orbit camera for 3D preview.
 *
 * Uses spherical coordinates (azimuth, elevation, distance) for intuitive control.
 * Supports orbit (rotate around target), pan (move target), and zoom (change distance).
 */
public class OrbitCamera {

    // Camera position in spherical coordinates
    private float azimuth = 45.0f;      // Horizontal angle (degrees)
    private float elevation = 30.0f;    // Vertical angle (degrees)
    private float distance = 5.0f;      // Distance from target

    // Look-at target
    private final Vector3f target = new Vector3f(0, 0, 0);

    // Camera constraints
    private float minDistance = 0.1f;
    private float maxDistance = 100.0f;
    private float minElevation = -89.0f;
    private float maxElevation = 89.0f;

    // Default values for reset
    private final float defaultAzimuth = 45.0f;
    private final float defaultElevation = 30.0f;
    private final float defaultDistance = 5.0f;
    private final Vector3f defaultTarget = new Vector3f(0, 0, 0);

    /**
     * Create camera with default position
     */
    public OrbitCamera() {
    }

    /**
     * Create camera with custom position
     */
    public OrbitCamera(float azimuth, float elevation, float distance) {
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.distance = distance;
    }

    // ==================== Camera Control ====================

    /**
     * Orbit camera (rotate around target)
     *
     * @param deltaAzimuth Horizontal rotation (degrees)
     * @param deltaElevation Vertical rotation (degrees)
     */
    public void orbit(float deltaAzimuth, float deltaElevation) {
        azimuth += deltaAzimuth;
        elevation += deltaElevation;

        // Wrap azimuth to [0, 360)
        azimuth = azimuth % 360.0f;
        if (azimuth < 0) azimuth += 360.0f;

        // Clamp elevation to avoid gimbal lock
        elevation = Math.max(minElevation, Math.min(maxElevation, elevation));
    }

    /**
     * Pan camera (move target in screen space)
     *
     * @param deltaX Horizontal movement
     * @param deltaY Vertical movement
     */
    public void pan(float deltaX, float deltaY) {
        // Convert screen-space movement to world-space
        // Right vector: perpendicular to view direction
        Vector3f right = getRightVector();
        Vector3f up = getUpVector();

        target.add(right.mul(deltaX));
        target.add(up.mul(deltaY));
    }

    /**
     * Zoom camera (change distance)
     *
     * @param delta Distance change (positive = zoom in)
     */
    public void zoom(float delta) {
        distance -= delta;
        distance = Math.max(minDistance, Math.min(maxDistance, distance));
    }

    /**
     * Reset camera to default position
     */
    public void reset() {
        azimuth = defaultAzimuth;
        elevation = defaultElevation;
        distance = defaultDistance;
        target.set(defaultTarget);
    }

    /**
     * Frame a bounding box (adjust distance to fit)
     *
     * @param minBounds Minimum corner of bounding box
     * @param maxBounds Maximum corner of bounding box
     */
    public void frame(Vector3f minBounds, Vector3f maxBounds) {
        // Calculate center and size
        Vector3f center = new Vector3f(maxBounds).add(minBounds).mul(0.5f);
        Vector3f size = new Vector3f(maxBounds).sub(minBounds);
        float maxDimension = Math.max(size.x, Math.max(size.y, size.z));

        // Set target to center
        target.set(center);

        // Set distance to fit bounding box (with some padding)
        distance = maxDimension * 2.0f;
        distance = Math.max(minDistance, Math.min(maxDistance, distance));
    }

    // ==================== Camera Vectors ====================

    /**
     * Get camera position in world space
     */
    public Vector3f getPosition() {
        float azimuthRad = (float) Math.toRadians(azimuth);
        float elevationRad = (float) Math.toRadians(elevation);

        float x = distance * (float) (Math.cos(elevationRad) * Math.sin(azimuthRad));
        float y = distance * (float) Math.sin(elevationRad);
        float z = distance * (float) (Math.cos(elevationRad) * Math.cos(azimuthRad));

        return new Vector3f(target).add(x, y, z);
    }

    /**
     * Get camera forward vector (normalized)
     */
    public Vector3f getForwardVector() {
        Vector3f position = getPosition();
        return new Vector3f(target).sub(position).normalize();
    }

    /**
     * Get camera right vector (normalized)
     */
    public Vector3f getRightVector() {
        Vector3f forward = getForwardVector();
        Vector3f worldUp = new Vector3f(0, 1, 0);
        return new Vector3f(forward).cross(worldUp).normalize();
    }

    /**
     * Get camera up vector (normalized)
     */
    public Vector3f getUpVector() {
        Vector3f forward = getForwardVector();
        Vector3f right = getRightVector();
        return new Vector3f(right).cross(forward).normalize();
    }

    // ==================== Matrix Generation ====================

    /**
     * Get view matrix (world to camera space)
     */
    public Matrix4f getViewMatrix() {
        Vector3f position = getPosition();
        Vector3f forward = getForwardVector();
        Vector3f up = getUpVector();

        Matrix4f view = new Matrix4f();
        view.lookAt(position, target, up);
        return view;
    }

    /**
     * Get projection matrix (camera to clip space)
     *
     * @param fov Field of view (degrees)
     * @param aspect Aspect ratio (width / height)
     * @param near Near clipping plane
     * @param far Far clipping plane
     */
    public Matrix4f getProjectionMatrix(float fov, float aspect, float near, float far) {
        Matrix4f projection = new Matrix4f();
        projection.perspective((float) Math.toRadians(fov), aspect, near, far);
        return projection;
    }

    /**
     * Apply camera transform to PoseStack
     */
    public void applyToPoseStack(PoseStack poseStack, float fov, float aspect) {
        Matrix4f projection = getProjectionMatrix(fov, aspect, 0.1f, 1000.0f);
        Matrix4f view = getViewMatrix();

        // Apply projection
        poseStack.last().pose().mul(projection);
        // Apply view
        poseStack.last().pose().mul(view);
    }

    // ==================== Getters/Setters ====================

    public float getAzimuth() {
        return azimuth;
    }

    public void setAzimuth(float azimuth) {
        this.azimuth = azimuth;
    }

    public float getElevation() {
        return elevation;
    }

    public void setElevation(float elevation) {
        this.elevation = Math.max(minElevation, Math.min(maxElevation, elevation));
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = Math.max(minDistance, Math.min(maxDistance, distance));
    }

    public Vector3f getTarget() {
        return new Vector3f(target);
    }

    public void setTarget(Vector3f target) {
        this.target.set(target);
    }

    public void setDistanceRange(float min, float max) {
        this.minDistance = min;
        this.maxDistance = max;
        this.distance = Math.max(min, Math.min(max, this.distance));
    }

    public void setElevationRange(float min, float max) {
        this.minElevation = min;
        this.maxElevation = max;
        this.elevation = Math.max(min, Math.min(max, this.elevation));
    }

    @Override
    public String toString() {
        return String.format("OrbitCamera{azimuth=%.1f, elevation=%.1f, distance=%.2f, target=%s}",
                azimuth, elevation, distance, target);
    }
}
