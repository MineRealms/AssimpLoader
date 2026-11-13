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
uniform vec4 u_Tint;
uniform vec4 u_ColorModulator;
uniform sampler2D u_Lightmap;
uniform sampler2D u_Overlay;
uniform ivec2 u_LightUV;
uniform ivec2 u_OverlayUV;
uniform float u_FogStart;
uniform float u_FogEnd;
uniform vec4 u_FogColor;
uniform int u_FogShape;

// Output
out vec4 FragColor;

void main() {
    // Sample texture
    vec4 texColor = texture(u_Texture, v_TexCoord) * u_Tint * u_ColorModulator;
    if (texColor.a < 0.1) discard;

    // Basic lighting calculation
    vec3 normal = normalize(v_Normal);
    vec3 lightDir = normalize(u_LightDirection);

    // Diffuse lighting
    float diff = max(dot(normal, lightDir), 0.0);
    vec3 diffuse = diff * u_LightColor;

    // Combine ambient and diffuse
    vec3 lighting = min(vec3(1.0), u_AmbientColor + diffuse);

    // Final color
    FragColor = vec4(texColor.rgb * lighting, texColor.a);
    vec4 overlay = texelFetch(u_Overlay, u_OverlayUV, 0);
    FragColor.rgb = mix(overlay.rgb, FragColor.rgb, overlay.a);
    FragColor *= texelFetch(u_Lightmap, clamp(u_LightUV / 16, ivec2(0), ivec2(15)), 0);
    float distance = u_FogShape == 0 ? length(v_Position) : max(length(v_Position.xz), abs(v_Position.y));
    if (distance > u_FogStart) {
        float amount = distance >= u_FogEnd ? 1.0 : smoothstep(u_FogStart, u_FogEnd, distance);
        FragColor.rgb = mix(FragColor.rgb, u_FogColor.rgb, amount * u_FogColor.a);
    }
}
