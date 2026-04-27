package cn.minerealms.assimploader.lookdev.iris;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;

import java.util.*;

// glsl-transformer imports (optional dependency)
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;

/**
 * Injects GLSL fragments generated from node graphs into Iris/Oculus shader pipeline.
 *
 * This class uses glsl-transformer to parse and modify existing shaders,
 * injecting custom material code at specified points.
 *
 * Only available when Iris/Oculus is loaded (optional dependency).
 */
public class OculusShaderInjector {

    private static boolean glslTransformerAvailable = false;
    private static boolean irisAvailable = false;
    private static SingleASTTransformer<JobParameters> transformer = null;

    static {
        // Check if glsl-transformer is available
        try {
            Class.forName("io.github.douira.glsl_transformer.ast.transform.ASTTransformer");
            glslTransformerAvailable = true;
            initializeTransformer();
            DebugManager.info(DebugCategory.LOOKDEV, "[OculusShaderInjector] glsl-transformer is available");
        } catch (ClassNotFoundException e) {
            DebugManager.warn(DebugCategory.LOOKDEV, "[OculusShaderInjector] glsl-transformer not found - shader injection disabled");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[OculusShaderInjector] Failed to initialize transformer: {}", e.getMessage());
            glslTransformerAvailable = false;
        }

        // Check if Iris is available
        try {
            Class.forName("net.irisshaders.iris.Iris");
            irisAvailable = true;
            DebugManager.info(DebugCategory.LOOKDEV, "[OculusShaderInjector] Iris is available");
        } catch (ClassNotFoundException e) {
            DebugManager.debug(DebugCategory.LOOKDEV, "[OculusShaderInjector] Iris not found");
        }
    }

    /**
     * Initialize the AST transformer
     */
    private static void initializeTransformer() {
        if (!glslTransformerAvailable) {
            return;
        }

        try {
            transformer = new SingleASTTransformer<JobParameters>() {
                {
                    setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
                }
            };
            DebugManager.debug(DebugCategory.LOOKDEV, "[OculusShaderInjector] Transformer initialized");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[OculusShaderInjector] Failed to create transformer: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Check if shader injection is available
     */
    public static boolean isAvailable() {
        return glslTransformerAvailable && irisAvailable;
    }

    /**
     * Inject GLSL fragment into target shader
     *
     * @param targetShaderName Target shader type (e.g., "gbuffer_terrain", "composite1")
     * @param fragmentCode GLSL fragment code to inject
     * @param uniforms Uniform declarations
     * @param injectionPoint Where to inject the code
     * @return Transformed shader code, or null if injection fails
     */
    public String inject(String targetShaderName,
                         String fragmentCode,
                         Map<String, String> uniforms,
                         InjectionPoint injectionPoint) {
        if (!isAvailable()) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[OculusShaderInjector] Cannot inject: glsl-transformer or Iris not available");
            return null;
        }

        if (transformer == null) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[OculusShaderInjector] Transformer not initialized");
            return null;
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[OculusShaderInjector] Injecting into shader: {}", targetShaderName);

        try {
            // Get base shader template
            String baseShader = getBaseFragmentShader();

            // Set up transformation
            transformer.setTransformation((tree, root) -> {
                // Get ASTParser instance for parsing injected code
                ASTParser astParser = ASTParser._getInternalInstance();

                // 1. Inject uniforms at BEFORE_DECLARATIONS
                if (uniforms != null && !uniforms.isEmpty()) {
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[OculusShaderInjector] Injecting {} uniforms", uniforms.size());

                    for (Map.Entry<String, String> uniform : uniforms.entrySet()) {
                        String uniformDecl = "uniform " + uniform.getValue() + " " + uniform.getKey() + ";";
                        tree.parseAndInjectNode(astParser, ASTInjectionPoint.BEFORE_DECLARATIONS, uniformDecl);
                        DebugManager.debug(DebugCategory.LOOKDEV,
                            "[OculusShaderInjector] Injected uniform: {}", uniformDecl);
                    }
                }

                // 2. Inject fragment code at specified injection point
                ASTInjectionPoint astPoint = convertInjectionPoint(injectionPoint);
                DebugManager.debug(DebugCategory.LOOKDEV,
                    "[OculusShaderInjector] Injecting code at: {}", astPoint);

                tree.parseAndInjectNode(astParser, astPoint, fragmentCode);
            });

            // Execute transformation
            String result = transformer.transform(baseShader, JobParameters.EMPTY);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[OculusShaderInjector] Successfully injected code into shader");

            return result;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[OculusShaderInjector] Injection failed: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convert InjectionPoint to ASTInjectionPoint
     */
    private ASTInjectionPoint convertInjectionPoint(InjectionPoint point) {
        return switch (point) {
            case BEFORE_DECLARATIONS -> ASTInjectionPoint.BEFORE_DECLARATIONS;
            case BEFORE_FUNCTIONS -> ASTInjectionPoint.BEFORE_FUNCTIONS;
            case MAIN_START -> ASTInjectionPoint.BEFORE_FUNCTIONS; // Inject before main function
            case MAIN_END -> ASTInjectionPoint.END; // Inject at end of file
            case AFTER_FUNCTION -> ASTInjectionPoint.END; // Inject at end
        };
    }

    /**
     * Inject into gbuffer shader (main rendering pass)
     */
    public String injectToGBuffer(String gbufferName,
                                  String fragmentCode,
                                  Map<String, String> uniforms) {
        return inject(gbufferName, fragmentCode, uniforms, InjectionPoint.MAIN_END);
    }

    /**
     * Inject into composite shader (post-processing)
     */
    public String injectToComposite(String compositeName,
                                    String fragmentCode,
                                    Map<String, String> uniforms) {
        return inject("composite_" + compositeName, fragmentCode, uniforms, InjectionPoint.MAIN_START);
    }

    /**
     * Injection point in shader
     */
    public enum InjectionPoint {
        BEFORE_DECLARATIONS,  // Before any declarations
        BEFORE_FUNCTIONS,     // After declarations, before functions
        MAIN_START,           // At start of main()
        MAIN_END,             // At end of main()
        AFTER_FUNCTION        // After a specific function
    }

    /**
     * Get base fragment shader template for testing
     */
    private String getBaseFragmentShader() {
        return """
            #version 330 core
            precision highp float;

            in vec2 texCoord;
            in vec3 normal;
            in vec3 viewDir;

            out vec4 fragColor;

            void main() {
                fragColor = vec4(1.0);
            }
            """;
    }
}
