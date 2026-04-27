package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import java.util.*;

/**
 * Represents a GLSL code fragment that can be injected into existing shaders.
 *
 * Unlike complete shaders, fragments contain:
 * - Variable declarations and computations
 * - Uniform declarations
 * - Injection point metadata
 *
 * But NOT:
 * - #version directives
 * - main() function wrapper
 * - Complete shader structure
 */
public class GLSLFragment {
    private final String code;
    private final Set<String> usedUniforms;
    private final InjectionConfig injectionConfig;

    public GLSLFragment(String code, Set<String> uniforms, InjectionConfig config) {
        this.code = code;
        this.usedUniforms = new HashSet<>(uniforms);
        this.injectionConfig = config;
    }

    public String getCode() {
        return code;
    }

    public Set<String> getUsedUniforms() {
        return Collections.unmodifiableSet(usedUniforms);
    }

    public InjectionConfig getInjectionConfig() {
        return injectionConfig;
    }

    @Override
    public String toString() {
        return "GLSLFragment{\n" +
               "  uniforms=" + usedUniforms.size() + "\n" +
               "  injectionPoint=" + (injectionConfig != null ? injectionConfig.getPoint() : "null") + "\n" +
               "  code=\n" + code + "\n" +
               "}";
    }

    /**
     * Configuration for shader injection
     */
    public static class InjectionConfig {
        private InjectionPoint point;
        private String targetShader;
        private String targetFunction;
        private boolean replaceOriginal;

        public InjectionConfig() {
            this.point = InjectionPoint.MAIN_END;
            this.replaceOriginal = false;
        }

        public enum InjectionPoint {
            BEFORE_DECLARATIONS,
            BEFORE_FUNCTIONS,
            MAIN_START,
            MAIN_END,
            AFTER_FUNCTION,
            REPLACE_BLOCK
        }

        public InjectionPoint getPoint() {
            return point;
        }

        public void setPoint(InjectionPoint point) {
            this.point = point;
        }

        public String getTargetShader() {
            return targetShader;
        }

        public void setTargetShader(String targetShader) {
            this.targetShader = targetShader;
        }

        public String getTargetFunction() {
            return targetFunction;
        }

        public void setTargetFunction(String targetFunction) {
            this.targetFunction = targetFunction;
        }

        public boolean isReplaceOriginal() {
            return replaceOriginal;
        }

        public void setReplaceOriginal(boolean replaceOriginal) {
            this.replaceOriginal = replaceOriginal;
        }
    }

    /**
     * Material output configuration for fragment generation
     */
    public static class MaterialOutputConfig {
        private String baseColor;
        private String metallic;
        private String roughness;
        private String normal;
        private String emissive;
        private String alpha;

        public String getBaseColor() {
            return baseColor;
        }

        public void setBaseColor(String baseColor) {
            this.baseColor = baseColor;
        }

        public String getMetallic() {
            return metallic;
        }

        public void setMetallic(String metallic) {
            this.metallic = metallic;
        }

        public String getRoughness() {
            return roughness;
        }

        public void setRoughness(String roughness) {
            this.roughness = roughness;
        }

        public String getNormal() {
            return normal;
        }

        public void setNormal(String normal) {
            this.normal = normal;
        }

        public String getEmissive() {
            return emissive;
        }

        public void setEmissive(String emissive) {
            this.emissive = emissive;
        }

        public String getAlpha() {
            return alpha;
        }

        public void setAlpha(String alpha) {
            this.alpha = alpha;
        }
    }
}
