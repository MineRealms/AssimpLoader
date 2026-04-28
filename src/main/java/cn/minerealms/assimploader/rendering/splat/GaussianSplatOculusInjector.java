package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.iris.OculusShaderInjector;

import java.util.HashMap;
import java.util.Map;

/**
 * Injects Gaussian Splatting rendering code into Oculus/Iris shader pipeline.
 *
 * This allows splats to be rendered through the shader pack's gbuffers,
 * enabling compatibility with shader effects like lighting, shadows, and post-processing.
 */
public class GaussianSplatOculusInjector {

    private static boolean initialized = false;
    private static boolean injectionSuccessful = false;

    /**
     * Initialize Oculus injection for Gaussian Splatting
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!OculusShaderInjector.isAvailable()) {
            DebugManager.warn(DebugCategory.RENDER,
                "[GaussianSplatOculusInjector] Oculus shader injection not available - using standalone rendering");
            return;
        }

        try {
            injectToGBuffers();
            injectionSuccessful = true;
            DebugManager.info(DebugCategory.RENDER,
                "[GaussianSplatOculusInjector] Successfully injected Gaussian Splatting into Oculus pipeline");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.RENDER,
                "[GaussianSplatOculusInjector] Failed to inject: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if Oculus injection is active
     */
    public static boolean isInjected() {
        return injectionSuccessful;
    }

    /**
     * Inject Gaussian Splatting code into gbuffers
     */
    private static void injectToGBuffers() {
        // Prepare uniforms for TBO access (since gbuffers may not support SSBO)
        Map<String, String> uniforms = new HashMap<>();
        uniforms.put("gaussianDataTBO", "samplerBuffer");
        uniforms.put("gaussianOrderTBO", "isamplerBuffer");
        uniforms.put("splatCount", "int");
        uniforms.put("sh_dim", "int");
        uniforms.put("scale_modifier", "float");
        uniforms.put("render_mod", "int");

        // Generate vertex shader injection code
        String vertexCode = generateVertexShaderCode();

        // Generate fragment shader injection code
        String fragmentCode = generateFragmentShaderCode();

        // Inject into gbuffer_terrain (main terrain rendering)
        String result = OculusShaderInjector.injectToGBuffer(
            "gbuffer_terrain",
            vertexCode + "\n" + fragmentCode,
            uniforms
        );

        if (result == null) {
            throw new RuntimeException("Failed to inject into gbuffer_terrain");
        }

        DebugManager.debug(DebugCategory.RENDER,
            "[GaussianSplatOculusInjector] Injected into gbuffer_terrain");
    }

    /**
     * Generate vertex shader code for Gaussian Splatting
     * This is a simplified version that uses TBO instead of SSBO
     */
    private static String generateVertexShaderCode() {
        return """
            // Gaussian Splatting vertex shader injection
            // SH constants
            #define SH_C0 0.28209479177387814
            #define SH_C1 0.4886025119029199
            #define SH_C2_0 1.0925484305920792
            #define SH_C2_1 -1.0925484305920792
            #define SH_C2_2 0.31539156525252005
            #define SH_C2_3 -1.0925484305920792
            #define SH_C2_4 0.5462742152960396

            // Data layout indices
            #define POS_IDX 0
            #define ROT_IDX 3
            #define SCALE_IDX 7
            #define OPACITY_IDX 10
            #define SH_IDX 11

            // Varyings for fragment shader
            out vec3 splatColor;
            out float splatAlpha;
            out vec3 splatConic;
            out vec2 splatCoordXY;

            // Helper functions for TBO access
            vec3 getSplatVec3(int offset) {
                return vec3(
                    texelFetch(gaussianDataTBO, offset).r,
                    texelFetch(gaussianDataTBO, offset + 1).r,
                    texelFetch(gaussianDataTBO, offset + 2).r
                );
            }

            vec4 getSplatVec4(int offset) {
                return vec4(
                    texelFetch(gaussianDataTBO, offset).r,
                    texelFetch(gaussianDataTBO, offset + 1).r,
                    texelFetch(gaussianDataTBO, offset + 2).r,
                    texelFetch(gaussianDataTBO, offset + 3).r
                );
            }

            mat3 computeSplatCov3D(vec3 scale, vec4 q) {
                mat3 S = mat3(0.0);
                S[0][0] = scale.x;
                S[1][1] = scale.y;
                S[2][2] = scale.z;

                float r = q.x;
                float x = q.y;
                float y = q.z;
                float z = q.w;

                mat3 R = mat3(
                    1.0 - 2.0 * (y * y + z * z), 2.0 * (x * y - r * z), 2.0 * (x * z + r * y),
                    2.0 * (x * y + r * z), 1.0 - 2.0 * (x * x + z * z), 2.0 * (y * z - r * x),
                    2.0 * (x * z - r * y), 2.0 * (y * z + r * x), 1.0 - 2.0 * (x * x + y * y)
                );

                mat3 M = S * R;
                return transpose(M) * M;
            }

            vec3 computeSplatCov2D(vec4 mean_view, float focal, mat3 cov3D, mat4 viewMatrix) {
                vec4 t = mean_view;
                float limx = 1.3;
                float limy = 1.3;
                t.x = clamp(t.x / t.z, -limx, limx) * t.z;
                t.y = clamp(t.y / t.z, -limy, limy) * t.z;

                mat3 J = mat3(
                    focal / t.z, 0.0, -(focal * t.x) / (t.z * t.z),
                    0.0, focal / t.z, -(focal * t.y) / (t.z * t.z),
                    0.0, 0.0, 0.0
                );

                mat3 W = transpose(mat3(viewMatrix));
                mat3 T = W * J;
                mat3 cov = transpose(T) * transpose(cov3D) * T;
                cov[0][0] += 0.3;
                cov[1][1] += 0.3;
                return vec3(cov[0][0], cov[0][1], cov[1][1]);
            }

            void processSplat(int instanceID, vec2 quadPos) {
                int boxid = texelFetch(gaussianOrderTBO, instanceID).r;
                int total_dim = 3 + 4 + 3 + 1 + sh_dim;
                int start = boxid * total_dim;

                vec4 g_pos = vec4(getSplatVec3(start + POS_IDX), 1.0);
                vec4 g_pos_view = gbufferModelView * g_pos;
                vec4 g_pos_screen = gbufferProjection * g_pos_view;
                g_pos_screen.xyz /= g_pos_screen.w;
                g_pos_screen.w = 1.0;

                // Early culling
                if (any(greaterThan(abs(g_pos_screen.xyz), vec3(1.3)))) {
                    gl_Position = vec4(-100.0, -100.0, -100.0, 1.0);
                    return;
                }

                vec4 g_rot = getSplatVec4(start + ROT_IDX);
                vec3 g_scale = getSplatVec3(start + SCALE_IDX);
                float g_opacity = texelFetch(gaussianDataTBO, start + OPACITY_IDX).r;

                mat3 cov3d = computeSplatCov3D(g_scale * scale_modifier, g_rot);
                vec3 cov2d = computeSplatCov2D(g_pos_view, 1.0, cov3d, gbufferModelView);

                // Invert covariance
                float det = cov2d.x * cov2d.z - cov2d.y * cov2d.y;
                if (det == 0.0) {
                    gl_Position = vec4(0.0);
                    return;
                }

                float det_inv = 1.0 / det;
                splatConic = vec3(cov2d.z * det_inv, -cov2d.y * det_inv, cov2d.x * det_inv);

                vec2 quadwh_scr = vec2(3.4 * sqrt(cov2d.x), 3.4 * sqrt(cov2d.z));
                vec2 quadwh_ndc = quadwh_scr / vec2(viewWidth, viewHeight) * 2.0;
                g_pos_screen.xy += quadPos * quadwh_ndc;
                splatCoordXY = quadPos * quadwh_scr;
                gl_Position = g_pos_screen;

                splatAlpha = g_opacity;

                // Convert SH to color
                int sh_start = start + SH_IDX;
                vec3 dir = normalize(g_pos.xyz - cameraPosition);
                splatColor = SH_C0 * getSplatVec3(sh_start);

                if (sh_dim > 3) {
                    float x = dir.x, y = dir.y, z = dir.z;
                    splatColor -= SH_C1 * y * getSplatVec3(sh_start + 3);
                    splatColor += SH_C1 * z * getSplatVec3(sh_start + 6);
                    splatColor -= SH_C1 * x * getSplatVec3(sh_start + 9);
                }
                splatColor += 0.5;
            }
            """;
    }

    /**
     * Generate fragment shader code for Gaussian Splatting
     */
    private static String generateFragmentShaderCode() {
        return """
            // Gaussian Splatting fragment shader injection
            in vec3 splatColor;
            in float splatAlpha;
            in vec3 splatConic;
            in vec2 splatCoordXY;

            vec4 renderSplat() {
                float power = -0.5 * (splatConic.x * splatCoordXY.x * splatCoordXY.x +
                                      splatConic.z * splatCoordXY.y * splatCoordXY.y) -
                              splatConic.y * splatCoordXY.x * splatCoordXY.y;

                if (power > 0.0) {
                    discard;
                }

                float opacity = min(0.99, splatAlpha * exp(power));
                if (opacity < 1.0 / 255.0) {
                    discard;
                }

                return vec4(splatColor, opacity);
            }
            """;
    }
}
