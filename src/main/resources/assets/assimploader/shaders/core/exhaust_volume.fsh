#version 150
uniform sampler2D DepthSampler;
uniform mat4 ProjectionInverse;
uniform mat4 ViewInverse;
uniform vec2 FullResolution;
in vec2 uv;
out vec4 fragColor;
// ASSIMP_EXHAUST_INCLUDE
void main() {
    vec2 snappedUv = (floor(uv * FullResolution) + 0.5) / FullResolution;
    float depth = texture(DepthSampler, snappedUv).r;
    vec4 view = ProjectionInverse * vec4(snappedUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    view /= view.w;
    vec3 ray = normalize(mat3(ViewInverse) * view.xyz);
    float distance = depth >= 0.999999 ? 2048.0 : length(view.xyz);
    // Stable spatial dither; temporal accumulation is deliberately not claimed here.
    float jitter = fract(dot(gl_FragCoord.xy, vec2(0.754877666, 0.569840296)));
    fragColor = assimpIntegrateExhaust(ray, distance, assimpExhaustTime, jitter);
}
