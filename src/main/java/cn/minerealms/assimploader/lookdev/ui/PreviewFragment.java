package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.camera.OrbitCamera;
import cn.minerealms.assimploader.lookdev.comparison.ComparisonSnapshot;
import cn.minerealms.assimploader.lookdev.material.MaterialOverrideManager;
import cn.minerealms.assimploader.lookdev.render.OffscreenRenderer;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import org.joml.Vector3f;

import icyllis.modernui.annotation.NonNull;
import javax.annotation.Nullable;

/**
 * Fragment for 3D model preview rendering.
 *
 * Center panel of the LookDev viewer.
 * Uses offscreen rendering to display 3D models.
 */
public class PreviewFragment extends Fragment {

    private TextView infoText;
    private PreviewView previewView;
    private OrbitCamera camera;
    private OffscreenRenderer offscreenRenderer;

    // Current model and material
    private AssimpScene currentModel;
    private PBRMaterial currentMaterial;
    private MaterialOverrideManager overrideManager;

    // Comparison mode
    private boolean comparisonMode = false;
    private ComparisonSnapshot comparisonSnapshot = null;

    // Mouse interaction state
    private float lastMouseX;
    private float lastMouseY;
    private boolean isDragging;

    // Render mode
    public enum RenderMode {
        ITEM_RENDERER("Item Renderer"),
        ENTITY_RENDERER("Entity Renderer"),
        DIRECT_MESH("Direct Mesh");

        private final String displayName;
        RenderMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    private RenderMode renderMode = RenderMode.DIRECT_MESH;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] onCreateView START");

        try {
            var context = requireContext();

            // Initialize camera
            camera = new OrbitCamera(45, 30, 5);
            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] Camera initialized");

            // Initialize offscreen renderer
            offscreenRenderer = new OffscreenRenderer(512, 512);
            offscreenRenderer.setCamera(camera);
            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] OffscreenRenderer initialized");

            // Root layout
            var root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(new ColorDrawable(0xFF1E1E1E));
            root.setPadding(root.dp(10), root.dp(10), root.dp(10), root.dp(10));

            // Title
            var titleText = new TextView(context);
            titleText.setText("3D Preview");
            titleText.setTextSize(16);
            titleText.setTextColor(0xFFFFFFFF);
            var titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, 0, 0, root.dp(10));
            root.addView(titleText, titleParams);

            // Preview view (renders the offscreen texture)
            previewView = new PreviewView();
            var previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            );
            previewParams.weight = 1.0f;
            previewParams.setMargins(0, 0, 0, root.dp(10));
            root.addView(previewView, previewParams);

            // Info text
            infoText = new TextView(context);
            infoText.setText("No model loaded");
            infoText.setTextSize(11);
            infoText.setTextColor(0xFFCCCCCC);
            var infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            root.addView(infoText, infoParams);

            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] onCreateView COMPLETE");
            return root;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] FATAL ERROR in onCreateView", e);
            throw e;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable DataSet savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] onViewCreated START");

        try {
            setupMouseInput();

            // If model was set before view was created, update now
            if (currentModel != null && camera != null) {
                DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] Model already set, framing camera");
                camera.frame(new Vector3f(-1, -1, -1), new Vector3f(1, 1, 1));
                updateOffscreenRenderer();
                updateInfoText();
            }

            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] onViewCreated COMPLETE");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in onViewCreated", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (offscreenRenderer != null) {
            offscreenRenderer.destroy();
            offscreenRenderer = null;
        }

        DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] View destroyed, overlay renderer disabled");
    }

    private void setupMouseInput() {
        if (previewView == null) return;

        previewView.setOnTouchListener((v, event) -> {
            try {
                float x = event.getX();
                float y = event.getY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastMouseX = x;
                        lastMouseY = y;
                        isDragging = true;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (isDragging && camera != null) {
                            float dx = x - lastMouseX;
                            float dy = y - lastMouseY;

                            // Orbit camera
                            camera.orbit(dx * 0.5f, -dy * 0.5f);

                            lastMouseX = x;
                            lastMouseY = y;

                            updateOffscreenRenderer();
                            updateInfoText();
                            previewView.invalidate();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        return true;
                }
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] Mouse input error", e);
            }
            return false;
        });
    }

    /**
     * Set the model to preview
     */
    public void setModel(AssimpScene model) {
        try {
            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] setModel called: {}",
                model != null ? "model present" : "null");

            this.currentModel = model;

            if (model != null && camera != null) {
                // Frame camera to model bounds
                camera.frame(new Vector3f(-1, -1, -1), new Vector3f(1, 1, 1));
                DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] Camera framed to model");
            }

            updateOffscreenRenderer();
            updateInfoText();
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in setModel", e);
        }
    }

    /**
     * Set the material to preview
     */
    public void setMaterial(PBRMaterial material) {
        try {
            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] setMaterial: {}",
                material != null ? material.getName() : "null");

            this.currentMaterial = material;

            // Create override manager for this material
            if (material != null) {
                this.overrideManager = new MaterialOverrideManager(material);
            } else {
                this.overrideManager = null;
            }

            updateOffscreenRenderer();
            updateInfoText();
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in setMaterial", e);
        }
    }

    /**
     * Set render mode
     */
    public void setRenderMode(RenderMode mode) {
        try {
            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] setRenderMode: {}", mode);
            this.renderMode = mode;
            updateOffscreenRenderer();
            updateInfoText();
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in setRenderMode", e);
        }
    }

    /**
     * Refresh the preview (called when material parameters change)
     */
    public void refresh() {
        try {
            updateOffscreenRenderer();
            updateInfoText();
            if (previewView != null) {
                previewView.invalidate();
            }
            DebugManager.debug(DebugCategory.LOOKDEV, "[PreviewFragment] Refreshed");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in refresh", e);
        }
    }

    /**
     * Set comparison mode
     */
    public void setComparisonMode(boolean enabled, @Nullable ComparisonSnapshot snapshot) {
        try {
            this.comparisonMode = enabled;
            this.comparisonSnapshot = snapshot;
            updateInfoText();
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in setComparisonMode", e);
        }
    }

    /**
     * Get the material override manager for parameter adjustments
     */
    public MaterialOverrideManager getOverrideManager() {
        return overrideManager;
    }

    /**
     * Reset camera to default position
     */
    public void resetCamera() {
        try {
            if (camera != null) {
                camera.reset();
                updateOffscreenRenderer();
                updateInfoText();
                if (previewView != null) {
                    previewView.invalidate();
                }
                DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] Camera reset");
            }
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in resetCamera", e);
        }
    }

    private void updateOffscreenRenderer() {
        if (offscreenRenderer == null) return;

        offscreenRenderer.setModel(currentModel);

        // Use effective material from override manager if available
        PBRMaterial materialToRender = currentMaterial;
        if (overrideManager != null) {
            materialToRender = overrideManager.getEffectiveMaterial();
            DebugManager.debug(DebugCategory.LOOKDEV, "[PreviewFragment] Using effective material from override manager");
        }

        offscreenRenderer.setMaterial(materialToRender);
        offscreenRenderer.setCamera(camera);
        offscreenRenderer.requestRender();
    }

    private void updateInfoText() {
        try {
            if (infoText == null) return;

            StringBuilder info = new StringBuilder();

            if (comparisonMode) {
                info.append("MODE: COMPARISON\n");
                if (comparisonSnapshot != null) {
                    info.append("Snapshot: ").append(comparisonSnapshot.getLabel()).append("\n");
                }
                info.append("\n");
            }

            info.append("Render Mode: ").append(renderMode.getDisplayName()).append("\n");

            if (currentModel != null) {
                info.append("Model: Loaded (").append(currentModel.getMeshCount()).append(" meshes)\n");
            } else {
                info.append("Model: None\n");
            }

            if (currentMaterial != null) {
                info.append("Material: ").append(currentMaterial.getName());
            } else {
                info.append("Material: None");
            }

            infoText.setText(info.toString());
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[PreviewFragment] ERROR in updateInfoText", e);
        }
    }

    /**
     * Custom view that displays the offscreen-rendered texture
     * Draws pixels directly from the FBO pixel buffer
     */
    private class PreviewView extends View {

        private Paint paint;

        public PreviewView() {
            super(requireContext());
            paint = new Paint();
            setBackground(new ColorDrawable(0xFF2A2A2A));
        }

        private long lastLogTime = 0;
        private static final long LOG_INTERVAL = 2000; // 每2秒打印一次日志

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            try {
                int width = getWidth();
                int height = getHeight();

                // Draw background
                paint.setColor(0xFF2A2A2A);
                canvas.drawRect(0, 0, width, height, paint);

                if (offscreenRenderer != null && currentModel != null) {
                    // Request render on render thread (async)
                    offscreenRenderer.requestRender();

                    // Try to get pixel buffer
                    int[] pixels = offscreenRenderer.getPixelBuffer();

                    long now = System.currentTimeMillis();
                    if (now - lastLogTime > LOG_INTERVAL) {
                        if (pixels != null) {
                            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewView] Got pixel buffer: {} pixels, first=0x{}",
                                pixels.length, Integer.toHexString(pixels[0]));
                        } else {
                            DebugManager.info(DebugCategory.LOOKDEV, "[PreviewView] No pixel buffer available");
                        }
                        lastLogTime = now;
                    }

                    if (pixels != null) {
                        drawPixelBuffer(canvas, pixels, width, height);
                    } else {
                        // Draw loading indicator
                        paint.setColor(0xFF888888);
                        canvas.drawRect(width / 2f - 50, height / 2f - 10, width / 2f + 50, height / 2f + 10, paint);
                    }

                    // Request continuous updates (~30 FPS)
                    postInvalidateDelayed(33);
                } else {
                    // No model loaded - draw placeholder
                    paint.setColor(0xFF888888);
                    canvas.drawRect(width / 2f - 50, height / 2f - 10, width / 2f + 50, height / 2f + 10, paint);
                }
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[PreviewView] onDraw error", e);
            }
        }

        /**
         * Draw pixel buffer from FBO
         */
        private void drawPixelBuffer(Canvas canvas, int[] pixels, int viewWidth, int viewHeight) {
            // OffscreenRenderer is 512x512
            int bufferWidth = 512;
            int bufferHeight = 512;

            // Calculate centered position
            int drawWidth = Math.min(viewWidth, bufferWidth);
            int drawHeight = Math.min(viewHeight, bufferHeight);
            int offsetX = (viewWidth - drawWidth) / 2;
            int offsetY = (viewHeight - drawHeight) / 2;

            // Draw pixels
            for (int y = 0; y < drawHeight; y++) {
                for (int x = 0; x < drawWidth; x++) {
                    int pixelIndex = y * bufferWidth + x;
                    if (pixelIndex < pixels.length) {
                        int color = pixels[pixelIndex];
                        paint.setColor(color);
                        canvas.drawPoint(offsetX + x, offsetY + y, paint);
                    }
                }
            }
        }
    }
}

