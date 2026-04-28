package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import cn.minerealms.assimploader.lookdev.iris.OculusShaderInjector;

import java.util.*;

/**
 * Dual-mode shader manager that supports both:
 * 1. Injection mode (Iris/Oculus) - injects GLSL fragments into existing shaders
 * 2. Full shader mode (Photon) - generates complete shaders
 *
 * The manager automatically detects which mode to use based on available dependencies.
 *
 * Priority:
 * - If Iris/Oculus available: use injection mode
 * - Else if Photon available: use full shader mode
 * - Else: no shader support
 */
public class DualModeShaderManager {

    private static boolean oculusAvailable = false;
    private static boolean photonAvailable = false;
    private static boolean initialized = false;

    /**
     * Initialize and detect available shader systems
     */
    public static void init() {
        if (initialized) {
            return;
        }

        oculusAvailable = checkOculus();
        photonAvailable = checkPhoton();
        initialized = true;

        DebugManager.info(DebugCategory.LOOKDEV,
            "[DualModeShaderManager] Initialized - Oculus: {}, Photon: {}",
            oculusAvailable, photonAvailable);
    }

    /**
     * Check if Iris/Oculus shader injection is available
     */
    public static boolean isOculusAvailable() {
        if (!initialized) {
            init();
        }
        return oculusAvailable;
    }

    /**
     * Check if Photon full shader mode is available
     */
    public static boolean isPhotonAvailable() {
        if (!initialized) {
            init();
        }
        return photonAvailable;
    }

    /**
     * Check if any shader support is available
     */
    public static boolean hasShaderSupport() {
        if (!initialized) {
            init();
        }
        return oculusAvailable || photonAvailable;
    }

    /**
     * Compile shader from node graph
     *
     * @param nodes Node list
     * @param connections Connection list
     * @param config Material output configuration
     * @return Compiled shader result, or null if compilation fails
     */
    public static CompiledShader compile(List<Node> nodes,
                                         List<NodeConnection> connections,
                                         GLSLFragment.MaterialOutputConfig config) {
        if (!initialized) {
            init();
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[DualModeShaderManager] Compiling shader with {} nodes", nodes.size());

        // Validate node graph
        NodeGraphExecutor executor = new NodeGraphExecutor(nodes, connections);
        if (executor.hasCycles()) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] Cannot compile: graph contains cycles");
            return null;
        }

        // Choose compilation mode
        if (oculusAvailable) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] Using injection mode (Oculus)");
            return compileInjectMode(nodes, connections, config);
        } else if (photonAvailable) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] Using full shader mode (Photon)");
            return compileFullMode(nodes, connections);
        }

        DebugManager.warn(DebugCategory.LOOKDEV,
            "[DualModeShaderManager] No shader system available");
        return null;
    }

    /**
     * Compile in injection mode (Iris/Oculus)
     */
    private static CompiledShader compileInjectMode(List<Node> nodes,
                                                     List<NodeConnection> connections,
                                                     GLSLFragment.MaterialOutputConfig config) {
        // Generate GLSL fragment
        GLSLFragmentGenerator fragmentGen = new GLSLFragmentGenerator(nodes, connections);
        GLSLFragment fragment = fragmentGen.generate(config, null);

        if (fragment == null) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] Failed to generate GLSL fragment");
            return null;
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[DualModeShaderManager] Generated GLSL fragment: {} uniforms",
            fragment.getUsedUniforms().size());

        // Use OculusShaderInjector to inject into target shader
        if (OculusShaderInjector.isAvailable()) {
            String targetShader = "gbuffers_textured";

            // Convert Set<String> uniforms to Map<String, String> with types
            Map<String, String> uniformsWithTypes = new HashMap<>();
            for (String uniformName : fragment.getUsedUniforms()) {
                // Infer type from uniform name convention
                String type = inferUniformType(uniformName);
                uniformsWithTypes.put(uniformName, type);
            }

            String injectedCode = OculusShaderInjector.injectToGBuffer(
                targetShader,
                fragment.getCode(),
                uniformsWithTypes
            );

            if (injectedCode != null) {
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[DualModeShaderManager] Successfully injected shader code");

                return new CompiledShader(
                    CompilationMode.INJECTION,
                    injectedCode,
                    null,
                    fragment.getUsedUniforms()
                );
            } else {
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[DualModeShaderManager] OculusShaderInjector.inject() returned null");
            }
        } else {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] OculusShaderInjector not available");
        }

        // Fallback: return fragment without injection
        return new CompiledShader(
            CompilationMode.INJECTION,
            fragment.getCode(),
            null,
            fragment.getUsedUniforms()
        );
    }

    /**
     * Infer GLSL type from uniform name convention
     */
    private static String inferUniformType(String uniformName) {
        // Common naming conventions
        if (uniformName.startsWith("u_Texture") || uniformName.endsWith("Texture")) {
            return "sampler2D";
        } else if (uniformName.endsWith("Color") || uniformName.startsWith("color")) {
            return "vec4";
        } else if (uniformName.endsWith("Factor") || uniformName.contains("Metallic") ||
                   uniformName.contains("Roughness") || uniformName.contains("Alpha")) {
            return "float";
        } else if (uniformName.endsWith("UV") || uniformName.contains("Scale")) {
            return "vec2";
        } else if (uniformName.endsWith("Normal") || uniformName.endsWith("Position")) {
            return "vec3";
        }

        // Default to float
        return "float";
    }

    /**
     * Compile in full shader mode (Photon)
     */
    private static CompiledShader compileFullMode(List<Node> nodes,
                                                   List<NodeConnection> connections) {
        // Use existing ShaderCodeGenerator
        ShaderCodeGenerator fullGen = new ShaderCodeGenerator(nodes, connections);
        ShaderCodeGenerator.ShaderCode shaderCode = fullGen.generate();

        if (shaderCode == null) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] Failed to generate full shader");
            return null;
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[DualModeShaderManager] Generated full shader: {} + {} lines",
            shaderCode.vertexShader.split("\n").length,
            shaderCode.fragmentShader.split("\n").length);

        // Compile with Photon
        String shaderName = "node_based_" + System.currentTimeMillis();
        PhotonShaderCompiler.CompiledShader photonShader =
            PhotonShaderCompiler.compile(shaderName, shaderCode);

        if (photonShader == null) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[DualModeShaderManager] Photon compilation failed");
            return null;
        }

        return new CompiledShader(
            CompilationMode.FULL_SHADER,
            shaderCode.fragmentShader,
            shaderCode.vertexShader,
            Collections.emptySet()
        );
    }

    /**
     * Check if Iris/Oculus is loaded
     */
    private static boolean checkOculus() {
        return OculusShaderInjector.isAvailable();
    }

    /**
     * Check if Photon is loaded
     */
    private static boolean checkPhoton() {
        try {
            Class.forName("com.lowdragmc.photon.Photon");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Compilation mode
     */
    public enum CompilationMode {
        INJECTION,      // Inject into existing shader (Iris/Oculus)
        FULL_SHADER     // Generate complete shader (Photon)
    }

    /**
     * Result of shader compilation
     */
    public static class CompiledShader {
        private final CompilationMode mode;
        private final String fragmentCode;
        private final String vertexCode;
        private final Set<String> uniforms;

        public CompiledShader(CompilationMode mode,
                              String fragmentCode,
                              String vertexCode,
                              Set<String> uniforms) {
            this.mode = mode;
            this.fragmentCode = fragmentCode;
            this.vertexCode = vertexCode;
            this.uniforms = uniforms;
        }

        public CompilationMode getMode() {
            return mode;
        }

        public String getFragmentCode() {
            return fragmentCode;
        }

        public String getVertexCode() {
            return vertexCode;
        }

        public Set<String> getUniforms() {
            return uniforms;
        }

        public boolean isInjectionMode() {
            return mode == CompilationMode.INJECTION;
        }

        public boolean isFullShaderMode() {
            return mode == CompilationMode.FULL_SHADER;
        }
    }
}
