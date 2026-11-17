#version 150
uniform sampler2D VolumeSampler;
uniform sampler2D DepthSampler;
uniform mat4 ProjectionInverse;
in vec2 uv;
out vec4 fragColor;
float viewDistance(vec2 coordinate) {
    float depth = texture(DepthSampler, coordinate).r;
    vec4 p = ProjectionInverse * vec4(coordinate * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(p.xyz / p.w);
}
void main() {
    ivec2 size = textureSize(VolumeSampler, 0);
    vec2 samplePos = uv * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(samplePos));
    vec2 fraction = fract(samplePos);
    float centerDepth = viewDistance(uv);
    vec4 result = vec4(0.0);
    float weights = 0.0;
    for (int y = 0; y < 2; ++y) for (int x = 0; x < 2; ++x) {
        ivec2 pixel = clamp(base + ivec2(x,y), ivec2(0), size - 1);
        vec2 coordinate = (vec2(pixel) + 0.5) / vec2(size);
        float spatial = (x == 0 ? 1.0 - fraction.x : fraction.x) * (y == 0 ? 1.0 - fraction.y : fraction.y);
        float weight = spatial * exp(-abs(viewDistance(coordinate) - centerDepth) / max(0.1, centerDepth * 0.01));
        result += texelFetch(VolumeSampler, pixel, 0) * weight;
        weights += weight;
    }
    result = weights > 0.00001 ? result / weights : texture(VolumeSampler, uv);
    // Vanilla has no HDR bloom pipeline; this display curve is used only by this fallback.
    fragColor = vec4(1.0 - exp(-max(result.rgb, vec3(0.0)) * 0.22), clamp(result.a, 0.0, 1.0));
}
