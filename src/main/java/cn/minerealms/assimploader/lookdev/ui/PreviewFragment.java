package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.lookdev.camera.OrbitCamera;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fragment for 3D model preview rendering.
 *
 * Center panel of the LookDev viewer.
 * Shows:
 * - Real-time 3D model rendering
 * - Camera controls (rotate, zoom, pan)
 * - Material preview with overrides applied
 */
public class PreviewFragment extends Fragment {

    private MinecraftSurfaceView surfaceView;
    private OrbitCamera camera;
    private TextView infoText;

    // Current model and material
    private AssimpScene currentModel;
    private PBRMaterial currentMaterial;

    // Mouse interaction state
    private float lastMouseX;
    private float lastMouseY;
    private boolean isDragging;

    @Nullable
    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        var context = requireContext();

        // Initialize camera
        camera = new OrbitCamera(45, 30, 5);

        // Root layout
        var root = new FrameLayout(context);
        root.setBackground(new ColorDrawable(0xFF1E1E1E));

        // Surface view for 3D rendering
        surfaceView = new MinecraftSurfaceView(context);
        surfaceView.setRenderer(new PreviewRenderer());
        var surfaceParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(surfaceView, surfaceParams);

        // Info overlay
        infoText = new TextView(context);
        infoText.setText("3D Preview\n\nControls:\nLeft-drag: Rotate\nRight-drag: Pan\nScroll: Zoom\nR: Reset camera");
        infoText.setTextSize(12);
        infoText.setTextColor(0xFFCCCCCC);
        infoText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        var infoParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.gravity = Gravity.CENTER;
        root.addView(infoText, infoParams);

        return root;
    }

    @Override
    public void onViewCreated(@Nonnull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup mouse input
        setupMouseInput();
    }

    private void setupMouseInput() {
        surfaceView.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            float x = event.getX();
            float y = event.getY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    lastMouseX = x;
                    lastMouseY = y;
                    isDragging = true;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        float deltaX = x - lastMouseX;
                        float deltaY = y - lastMouseY;

                        // Left button: orbit
                        camera.orbit(deltaX * 0.5f, -deltaY * 0.5f);

                        lastMouseX = x;
                        lastMouseY = y;

                        surfaceView.invalidate();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    return true;
            }

            return false;
        });

        // Scroll for zoom
        surfaceView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float delta = (scrollY - oldScrollY) * 0.01f;
            camera.zoom(delta);
            surfaceView.invalidate();
        });
    }

    /**
     * Set the model to preview
     */
    public void setModel(AssimpScene model) {
        this.currentModel = model;

        if (model != null) {
            // Frame camera to model bounds
            // TODO: Get actual model bounds
            camera.frame(
                new org.joml.Vector3f(-1, -1, -1),
                new org.joml.Vector3f(1, 1, 1)
            );
        }

        updateInfoText();
        refresh();
    }

    /**
     * Set the material to preview
     */
    public void setMaterial(PBRMaterial material) {
        this.currentMaterial = material;
        updateInfoText();
        refresh();
    }

    /**
     * Refresh the preview (called when material parameters change)
     */
    public void refresh() {
        if (surfaceView != null) {
            surfaceView.invalidate();
        }
    }

    private void updateInfoText() {
        if (infoText == null) return;

        StringBuilder info = new StringBuilder();
        info.append("3D Preview\n\n");

        if (currentModel != null) {
            info.append("Model: Loaded\n");
        } else {
            info.append("Model: None\n");
        }

        if (currentMaterial != null) {
            info.append("Material: ").append(currentMaterial.getName()).append("\n");
        } else {
            info.append("Material: None\n");
        }

        info.append("\nControls:\n");
        info.append("Left-drag: Rotate\n");
        info.append("Right-drag: Pan\n");
        info.append("Scroll: Zoom\n");
        info.append("R: Reset camera\n");

        info.append("\nCamera:\n");
        info.append(String.format("Azimuth: %.1f°\n", camera.getAzimuth()));
        info.append(String.format("Elevation: %.1f°\n", camera.getElevation()));
        info.append(String.format("Distance: %.2f", camera.getDistance()));

        infoText.setText(info.toString());
    }

    /**
     * Renderer for the preview surface
     */
    private class PreviewRenderer implements MinecraftSurfaceView.Renderer {

        private int surfaceWidth;
        private int surfaceHeight;

        @Override
        public void onSurfaceChanged(int width, int height) {
            surfaceWidth = width;
            surfaceHeight = height;
        }

        @Override
        public void onDraw(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                          double guiScale, float alpha) {
            // TODO Phase 4: Implement actual 3D model rendering
            // For now, just draw a placeholder

            if (currentModel == null || currentMaterial == null) {
                return;
            }

            // Draw grid
            drawGrid(gr);

            // Draw axes
            drawAxes(gr);

            // TODO: Render actual model with material
            // This requires:
            // 1. Setup camera matrices
            // 2. Bind material textures
            // 3. Render model meshes
            // 4. Apply lighting
        }

        private void drawGrid(GuiGraphics gr) {
            // Simple grid visualization
            int centerX = surfaceWidth / 2;
            int centerY = surfaceHeight / 2;
            int gridSize = 50;

            // Draw grid lines
            for (int i = -5; i <= 5; i++) {
                int x = centerX + i * gridSize;
                int y = centerY + i * gridSize;

                // Vertical lines
                gr.fill(x, centerY - 250, x + 1, centerY + 250, 0x33FFFFFF);

                // Horizontal lines
                gr.fill(centerX - 250, y, centerX + 250, y + 1, 0x33FFFFFF);
            }

            // Draw center cross
            gr.fill(centerX - 10, centerY, centerX + 10, centerY + 1, 0xFFFFFFFF);
            gr.fill(centerX, centerY - 10, centerX + 1, centerY + 10, 0xFFFFFFFF);
        }

        private void drawAxes(GuiGraphics gr) {
            // Draw coordinate axes
            int centerX = surfaceWidth / 2;
            int centerY = surfaceHeight / 2;
            int axisLength = 100;

            // X axis (red)
            gr.fill(centerX, centerY, centerX + axisLength, centerY + 2, 0xFFFF0000);

            // Y axis (green)
            gr.fill(centerX, centerY - axisLength, centerX + 2, centerY, 0xFF00FF00);

            // Z axis (blue) - diagonal for perspective
            gr.fill(centerX, centerY, centerX - axisLength / 2, centerY + axisLength / 2, 0xFF0000FF);
        }
    }
}
