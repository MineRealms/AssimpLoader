package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

/**
 * GLSL shader templates for node-based material system.
 *
 * Provides standard vertex and fragment shader templates
 * that work with Photon/Oculus rendering pipeline.
 */
public class ShaderTemplate {

    /**
     * Vertex shader header
     */
    public static final String VERTEX_HEADER =
        "#version 330 core\n" +
        "\n" +
        "// Vertex Shader - Standard Pass-Through\n";

    /**
     * Vertex shader inputs
     */
    public static final String VERTEX_INPUTS =
        "// Inputs\n" +
        "layout(location = 0) in vec3 aPosition;\n" +
        "layout(location = 1) in vec3 aNormal;\n" +
        "layout(location = 2) in vec2 aTexCoord;\n" +
        "layout(location = 3) in vec3 aTangent;\n" +
        "layout(location = 4) in vec3 aBitangent;\n";

    /**
     * Vertex shader outputs
     */
    public static final String VERTEX_OUTPUTS =
        "// Outputs\n" +
        "out vec3 vPosition;\n" +
        "out vec3 vNormal;\n" +
        "out vec2 vTexCoord;\n" +
        "out vec3 vTangent;\n" +
        "out vec3 vBitangent;\n" +
        "out vec3 vViewDir;\n" +
        "\n" +
        "// Uniforms\n" +
        "uniform mat4 uModelMatrix;\n" +
        "uniform mat4 uViewMatrix;\n" +
        "uniform mat4 uProjectionMatrix;\n" +
        "uniform vec3 uCameraPosition;\n";

    /**
     * Vertex shader main function
     */
    public static final String VERTEX_MAIN =
        "\n" +
        "void main() {\n" +
        "    // Transform position\n" +
        "    vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);\n" +
        "    vPosition = worldPos.xyz;\n" +
        "    gl_Position = uProjectionMatrix * uViewMatrix * worldPos;\n" +
        "    \n" +
        "    // Transform normal, tangent, bitangent\n" +
        "    mat3 normalMatrix = transpose(inverse(mat3(uModelMatrix)));\n" +
        "    vNormal = normalize(normalMatrix * aNormal);\n" +
        "    vTangent = normalize(normalMatrix * aTangent);\n" +
        "    vBitangent = normalize(normalMatrix * aBitangent);\n" +
        "    \n" +
        "    // Pass through texture coordinates\n" +
        "    vTexCoord = aTexCoord;\n" +
        "    \n" +
        "    // Calculate view direction\n" +
        "    vViewDir = uCameraPosition - vPosition;\n" +
        "}\n";

    /**
     * Fragment shader header
     */
    public static final String FRAGMENT_HEADER =
        "#version 330 core\n" +
        "\n" +
        "// Fragment Shader - Node-Based Material\n";

    /**
     * Fragment shader inputs (from vertex shader)
     */
    public static final String FRAGMENT_INPUTS =
        "// Inputs from vertex shader\n" +
        "in vec3 vPosition;\n" +
        "in vec3 vNormal;\n" +
        "in vec2 vTexCoord;\n" +
        "in vec3 vTangent;\n" +
        "in vec3 vBitangent;\n" +
        "in vec3 vViewDir;\n" +
        "\n" +
        "// Output\n" +
        "out vec4 fragColor;\n";

    /**
     * Utility functions for fragment shader
     */
    public static final String UTILITY_FUNCTIONS =
        "// Utility Functions\n" +
        "\n" +
        "// Convert tangent-space normal to world-space\n" +
        "vec3 tangentToWorld(vec3 tangentNormal) {\n" +
        "    mat3 TBN = mat3(vTangent, vBitangent, vNormal);\n" +
        "    return normalize(TBN * tangentNormal);\n" +
        "}\n" +
        "\n" +
        "// Fresnel-Schlick approximation\n" +
        "vec3 fresnelSchlick(float cosTheta, vec3 F0) {\n" +
        "    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);\n" +
        "}\n" +
        "\n" +
        "// GGX/Trowbridge-Reitz normal distribution\n" +
        "float distributionGGX(vec3 N, vec3 H, float roughness) {\n" +
        "    float a = roughness * roughness;\n" +
        "    float a2 = a * a;\n" +
        "    float NdotH = max(dot(N, H), 0.0);\n" +
        "    float NdotH2 = NdotH * NdotH;\n" +
        "    \n" +
        "    float nom = a2;\n" +
        "    float denom = (NdotH2 * (a2 - 1.0) + 1.0);\n" +
        "    denom = 3.14159265359 * denom * denom;\n" +
        "    \n" +
        "    return nom / denom;\n" +
        "}\n" +
        "\n" +
        "// Smith's Schlick-GGX geometry function\n" +
        "float geometrySchlickGGX(float NdotV, float roughness) {\n" +
        "    float r = (roughness + 1.0);\n" +
        "    float k = (r * r) / 8.0;\n" +
        "    \n" +
        "    float nom = NdotV;\n" +
        "    float denom = NdotV * (1.0 - k) + k;\n" +
        "    \n" +
        "    return nom / denom;\n" +
        "}\n" +
        "\n" +
        "float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {\n" +
        "    float NdotV = max(dot(N, V), 0.0);\n" +
        "    float NdotL = max(dot(N, L), 0.0);\n" +
        "    float ggx2 = geometrySchlickGGX(NdotV, roughness);\n" +
        "    float ggx1 = geometrySchlickGGX(NdotL, roughness);\n" +
        "    \n" +
        "    return ggx1 * ggx2;\n" +
        "}\n";

    /**
     * Simple PBR lighting template (used as fallback)
     */
    public static final String SIMPLE_PBR_LIGHTING =
        "    // Simple PBR lighting\n" +
        "    vec3 N = normalize(vNormal);\n" +
        "    vec3 V = normalize(vViewDir);\n" +
        "    vec3 L = normalize(vec3(0.5, 1.0, 0.3));\n" +
        "    vec3 H = normalize(L + V);\n" +
        "    \n" +
        "    float NdotL = max(dot(N, L), 0.0);\n" +
        "    float NdotH = max(dot(N, H), 0.0);\n" +
        "    \n" +
        "    vec3 baseColor = vec3(0.8);\n" +
        "    float metallic = 0.0;\n" +
        "    float roughness = 0.5;\n" +
        "    \n" +
        "    vec3 diffuse = baseColor * NdotL;\n" +
        "    float spec = pow(NdotH, (1.0 - roughness) * 128.0);\n" +
        "    vec3 specular = vec3(spec) * (1.0 - roughness);\n" +
        "    \n" +
        "    vec3 finalColor = mix(diffuse + specular * 0.3, baseColor * (diffuse + specular), metallic);\n" +
        "    fragColor = vec4(finalColor, 1.0);\n";

    /**
     * Generate a complete fallback shader (when no nodes are present)
     */
    public static String generateFallbackShader() {
        return FRAGMENT_HEADER + "\n" +
               FRAGMENT_INPUTS + "\n" +
               UTILITY_FUNCTIONS + "\n" +
               "void main() {\n" +
               SIMPLE_PBR_LIGHTING +
               "}\n";
    }

    private ShaderTemplate() {
        // Utility class, no instantiation
    }
}
