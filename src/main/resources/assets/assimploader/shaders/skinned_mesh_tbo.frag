#version 330 core

// Inputs from vertex shader
in vec3 v_Position;
in vec3 v_Normal;
in vec2 v_TexCoord;

// Uniforms
uniform sampler2D u_Texture;
uniform vec3 u_LightDirection;
uniform vec3 u_LightColor;
uniform vec3 u_AmbientColor;
uniform float u_LightLevel;

// Output
out vec4 FragColor;

void main() {
    // Sample texture
    vec4 texColor = texture(u_Texture, v_TexCoord);

    // Basic lighting calculation
    vec3 normal = normalize(v_Normal);
    vec3 lightDir = normalize(u_LightDirection);

    // Diffuse lighting
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * u_LightColor;

    // Combine ambient and diffuse
    vec3 lighting = u_AmbientColor + diffuse;

    // Apply Minecraft light level
    lighting *= u_LightLevel;

    // Final color
    FragColor = vec4(texColor.rgb * lighting, texColor.a);
}
