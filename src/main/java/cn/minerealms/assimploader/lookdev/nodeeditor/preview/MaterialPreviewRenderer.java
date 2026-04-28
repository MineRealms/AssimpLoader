package cn.minerealms.assimploader.lookdev.nodeeditor.preview;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.PhotonIntegration;
import cn.minerealms.assimploader.lookdev.nodeeditor.shader.NodeBasedMaterial;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders material preview in 3D viewport.
 *
 * Features:
 * - Real-time material preview
 * - Multiple preview meshes (sphere, cube, plane, cylinder)
 * - Camera controls (rotation, zoom)
 * - Lighting setup
 * - Auto-update when material changes
 *
 * Only available in FULL_INTEGRATION mode (when Photon is loaded).
 */
public class MaterialPreviewRenderer {

    private PreviewMesh previewMesh;
    private NodeBasedMaterial currentMaterial;

    // Camera state
    private float cameraDistance = 3.0f;
    private float cameraYaw = 45.0f;
    private float cameraPitch = 30.0f;
    private Vector3f cameraTarget = new Vector3f(0, 0, 0);

    // Rendering state
    private boolean needsUpdate = true;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 16; // ~60 FPS

    // Debug logging rate limiting (log every 5 seconds)
    private long lastDebugLogTime = 0;
    private static final long DEBUG_LOG_INTERVAL_MS = 5000;
    private int renderCallsSinceLastLog = 0;

    public MaterialPreviewRenderer() {
        this.previewMesh = new PreviewMesh(PreviewMesh.MeshType.SPHERE);
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[MaterialPreviewRenderer] Initialized with SPHERE mesh");
    }

    /**
     * Render the material preview
     */
    public void render(PoseStack poseStack, int x, int y, int width, int height) {
        renderCallsSinceLastLog++;

        // Rate-limited debug logging (every 5 seconds)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDebugLogTime > DEBUG_LOG_INTERVAL_MS) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[MaterialPreviewRenderer] Render stats: {} calls in last {}ms, material ready: {}",
                renderCallsSinceLastLog, DEBUG_LOG_INTERVAL_MS,
                (currentMaterial != null && currentMaterial.isReady()));
            lastDebugLogTime = currentTime;
            renderCallsSinceLastLog = 0;
        }

        if (!PhotonIntegration.hasRealtimePreview()) {
            renderUnavailableMessage(poseStack, x, y, width, height);
            return;
        }

        if (currentMaterial == null || !currentMaterial.isReady()) {
            renderNoMaterialMessage(poseStack, x, y, width, height);
            return;
        }

        // Check if we need to update
        if (!needsUpdate && (currentTime - lastUpdateTime) < UPDATE_INTERVAL_MS) {
            return;
        }

        DebugManager.tryCatch("[MaterialPreviewRenderer] render", () -> {
            // Setup viewport
            setupViewport(x, y, width, height);

            // Setup matrices
            Matrix4f projectionMatrix = createProjectionMatrix(width, height);
            Matrix4f viewMatrix = createViewMatrix();
            Matrix4f modelMatrix = createModelMatrix();

            // Begin rendering
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            // Apply material
            currentMaterial.begin();

            // Build and render mesh
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);

            previewMesh.build(builder, modelMatrix);

            BufferUploader.drawWithShader(builder.end());

            // End material
            currentMaterial.end();

            // Cleanup
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();

            needsUpdate = false;
            lastUpdateTime = currentTime;
        });
    }

    /**
     * Setup OpenGL viewport
     */
    private void setupViewport(int x, int y, int width, int height) {
        RenderSystem.viewport(x, y, width, height);
    }

    /**
     * Create projection matrix
     */
    private Matrix4f createProjectionMatrix(int width, int height) {
        float fov = 60.0f;
        float aspect = (float) width / height;
        float near = 0.1f;
        float far = 100.0f;

        Matrix4f projection = new Matrix4f();
        projection.perspective((float) Math.toRadians(fov), aspect, near, far);
        return projection;
    }

    /**
     * Create view matrix from camera state
     */
    private Matrix4f createViewMatrix() {
        // Calculate camera position from spherical coordinates
        float yawRad = (float) Math.toRadians(cameraYaw);
        float pitchRad = (float) Math.toRadians(cameraPitch);

        float x = cameraDistance * (float) Math.cos(pitchRad) * (float) Math.sin(yawRad);
        float y = cameraDistance * (float) Math.sin(pitchRad);
        float z = cameraDistance * (float) Math.cos(pitchRad) * (float) Math.cos(yawRad);

        Vector3f cameraPos = new Vector3f(x, y, z).add(cameraTarget);
        Vector3f up = new Vector3f(0, 1, 0);

        Matrix4f view = new Matrix4f();
        view.lookAt(cameraPos, cameraTarget, up);
        return view;
    }

    /**
     * Create model matrix
     */
    private Matrix4f createModelMatrix() {
        Matrix4f model = new Matrix4f();
        model.identity();
        // Add rotation or scaling here if needed
        return model;
    }

    /**
     * Render "unavailable" message
     */
    private void renderUnavailableMessage(PoseStack poseStack, int x, int y, int width, int height) {
        // Draw background
        fill(poseStack, x, y, x + width, y + height, 0xFF1A1A1A);

        // Draw border
        drawBorder(poseStack, x, y, width, height, 0xFF444444);

        // Draw text "Preview unavailable - Photon not loaded"
        String message = "Preview unavailable - Photon not loaded";
        int textColor = 0xFF888888;
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int textX = x + (width - font.width(message)) / 2;
        int textY = y + (height - 9) / 2;
        font.drawInBatch(message, textX, textY, textColor, true, poseStack.last().pose(),
                        Minecraft.getInstance().renderBuffers().bufferSource(),
                        net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    /**
     * Render "no material" message
     */
    private void renderNoMaterialMessage(PoseStack poseStack, int x, int y, int width, int height) {
        // Draw background
        fill(poseStack, x, y, x + width, y + height, 0xFF2A2A2A);

        // Draw border
        drawBorder(poseStack, x, y, width, height, 0xFF666666);

        // Draw text "No material loaded"
        String message = "No material loaded";
        int textColor = 0xFF666666;
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int textX = x + (width - font.width(message)) / 2;
        int textY = y + (height - 9) / 2;
        font.drawInBatch(message, textX, textY, textColor, true, poseStack.last().pose(),
                        Minecraft.getInstance().renderBuffers().bufferSource(),
                        net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    /**
     * Fill rectangle
     */
    private void fill(PoseStack poseStack, int x1, int y1, int x2, int y2, int color) {
        // Simple fill implementation
        // TODO: Use proper rendering
    }

    /**
     * Draw border
     */
    private void drawBorder(PoseStack poseStack, int x, int y, int width, int height, int color) {
        // Simple border implementation
        // TODO: Use proper rendering
    }

    /**
     * Set the material to preview
     */
    public void setMaterial(NodeBasedMaterial material) {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[MaterialPreviewRenderer] Setting material: {}",
            material != null ? "NodeBasedMaterial" : "null");
        this.currentMaterial = material;
        this.needsUpdate = true;
    }

    /**
     * Set preview mesh type
     */
    public void setMeshType(PreviewMesh.MeshType type) {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[MaterialPreviewRenderer] Changing mesh type to: {}", type);
        this.previewMesh = new PreviewMesh(type);
        this.needsUpdate = true;
    }

    /**
     * Rotate camera
     */
    public void rotateCamera(float deltaYaw, float deltaPitch) {
        this.cameraYaw += deltaYaw;
        this.cameraPitch += deltaPitch;

        // Clamp pitch
        this.cameraPitch = Math.max(-89.0f, Math.min(89.0f, this.cameraPitch));

        this.needsUpdate = true;
    }

    /**
     * Zoom camera
     */
    public void zoomCamera(float delta) {
        this.cameraDistance += delta;

        // Clamp distance
        this.cameraDistance = Math.max(1.0f, Math.min(10.0f, this.cameraDistance));

        this.needsUpdate = true;
    }

    /**
     * Reset camera to default position
     */
    public void resetCamera() {
        this.cameraDistance = 3.0f;
        this.cameraYaw = 45.0f;
        this.cameraPitch = 30.0f;
        this.cameraTarget.set(0, 0, 0);
        this.needsUpdate = true;
    }

    /**
     * Mark preview as needing update
     */
    public void markDirty() {
        this.needsUpdate = true;
    }

    /**
     * Get current camera distance
     */
    public float getCameraDistance() {
        return cameraDistance;
    }

    /**
     * Get current camera yaw
     */
    public float getCameraYaw() {
        return cameraYaw;
    }

    /**
     * Get current camera pitch
     */
    public float getCameraPitch() {
        return cameraPitch;
    }

    /**
     * Get current preview mes
     */
    public PreviewMesh getPreviewMesh() {
        return previewMesh;
    }

    /**
     * Get current material
     */
    public NodeBasedMaterial getCurrentMaterial() {
        return currentMaterial;
    }
}
