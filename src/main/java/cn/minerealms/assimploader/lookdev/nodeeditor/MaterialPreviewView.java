package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.material.PBRMaterial;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

/**
 * Material Preview View
 *
 * Displays a real-time preview of the material being edited in the node editor.
 * Shows a 3D sphere with the current material applied.
 *
 * Phase 1: Basic UI structure
 * Phase 2: Integrate with shader compilation and rendering
 */
public class MaterialPreviewView extends LinearLayout {

    private TextView titleLabel;
    private PreviewRenderView previewView;
    private String currentMaterialName;
    private PBRMaterial currentMaterial;

    public MaterialPreviewView(Context context) {
        super(context);
        setOrientation(VERTICAL);

        // Set a visible background color to debug visibility
        setBackground(new icyllis.modernui.graphics.drawable.ColorDrawable(0xFF1A1A1A));

        DebugManager.info(DebugCategory.LOOKDEV, "[MaterialPreviewView] Initializing material preview");

        initializeUI();
    }

    private void initializeUI() {
        // Title label
        titleLabel = new TextView(getContext());
        titleLabel.setText("Material Preview");
        titleLabel.setTextSize(14);
        titleLabel.setTextColor(0xFFCCCCCC);
        titleLabel.setPadding(dp(10), dp(5), dp(10), dp(5));
        LayoutParams titleParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(titleLabel, titleParams);

        DebugManager.info(DebugCategory.LOOKDEV, "[MaterialPreviewView] Title label added");

        // Preview render area
        previewView = new PreviewRenderView(getContext());
        LayoutParams previewParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f);
        addView(previewView, previewParams);

        DebugManager.info(DebugCategory.LOOKDEV, "[MaterialPreviewView] Preview render view added with weight=1.0");
    }

    /**
     * Set the material name to display in the preview
     */
    public void setMaterialName(String materialName) {
        this.currentMaterialName = materialName;
        titleLabel.setText("Material Preview: " + (materialName != null ? materialName : "None"));
        previewView.invalidate();

        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialPreviewView] Material name updated: {}", materialName);
    }

    /**
     * Set the material to render in the preview
     */
    public void setMaterial(PBRMaterial material) {
        this.currentMaterial = material;
        if (material != null) {
            this.currentMaterialName = material.getName();
            titleLabel.setText("Material Preview: " + material.getName());
        }
        previewView.setMaterial(material);
        previewView.invalidate();

        DebugManager.info(DebugCategory.LOOKDEV,
            "[MaterialPreviewView] Material updated: {}", material != null ? material.getName() : "null");
    }

    /**
     * Update preview with a specific node (for future node-specific previews)
     */
    public void setPreviewNode(Node node) {
        // TODO: Phase 2 - Implement node-specific preview rendering
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[MaterialPreviewView] Preview node updated: {}", node != null ? node.getTitle() : "null");
    }

    private int dp(int dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    /**
     * Inner view that handles the actual 3D preview rendering
     */
    private static class PreviewRenderView extends View {

        private Paint paint;
        private PBRMaterial material;

        public PreviewRenderView(Context context) {
            super(context);
            paint = new Paint();
            paint.setColor(0xFF2A2A2A);

            DebugManager.info(DebugCategory.LOOKDEV, "[MaterialPreviewView.PreviewRenderView] Created");
        }

        public void setMaterial(PBRMaterial material) {
            this.material = material;
            invalidate();
            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialPreviewView.PreviewRenderView] Material set: {}",
                material != null ? material.getName() : "null");
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialPreviewView.PreviewRenderView] onMeasure - measured width: {}, height: {}",
                getMeasuredWidth(), getMeasuredHeight());
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialPreviewView.PreviewRenderView] onLayout - changed: {}, bounds: ({}, {}, {}, {})",
                changed, left, top, right, bottom);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialPreviewView.PreviewRenderView] onDraw called - width: {}, height: {}, hasMaterial: {}",
                width, height, material != null);

            // Draw background
            paint.setColor(0xFF2A2A2A);
            canvas.drawRect(0, 0, width, height, paint);

            if (material != null) {
                // Render material sphere with actual material properties
                renderMaterialSphere(canvas, width, height);
            } else {
                // Draw placeholder circle
                paint.setColor(0xFF555555);
                float centerX = width / 2f;
                float centerY = height / 2f;
                float radius = Math.min(width, height) / 3f;
                canvas.drawCircle(centerX, centerY, radius, paint);

                DebugManager.info(DebugCategory.LOOKDEV,
                    "[MaterialPreviewView.PreviewRenderView] Drew placeholder circle at ({}, {}) with radius {}",
                    centerX, centerY, radius);
            }
        }

        /**
         * Render a sphere with the material's properties
         */
        private void renderMaterialSphere(Canvas canvas, int width, int height) {
            float centerX = width / 2f;
            float centerY = height / 2f;
            float radius = Math.min(width, height) / 3f;

            // Get material base color
            org.joml.Vector4f baseColor = material.getBaseColor();
            int r = (int) (baseColor.x * 255);
            int g = (int) (baseColor.y * 255);
            int b = (int) (baseColor.z * 255);
            int a = (int) (baseColor.w * 255);
            int color = (a << 24) | (r << 16) | (g << 8) | b;

            // Draw sphere with base color
            paint.setColor(color);
            canvas.drawCircle(centerX, centerY, radius, paint);

            // Draw metallic/roughness indicator (simple gradient effect)
            float metallic = material.getMetallicFactor();
            float roughness = material.getRoughnessFactor();

            // Add highlight for metallic materials
            if (metallic > 0.5f) {
                paint.setColor(0x88FFFFFF);
                float highlightRadius = radius * 0.3f;
                canvas.drawCircle(centerX - radius * 0.3f, centerY - radius * 0.3f, highlightRadius, paint);
            }

            // Draw border
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(2.0f);
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setStyle(Paint.FILL);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialPreviewView.PreviewRenderView] Rendered material sphere - color: #{}, metallic: {}, roughness: {}",
                Integer.toHexString(color), metallic, roughness);
        }
    }
}
