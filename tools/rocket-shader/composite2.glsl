// AssimpLoader local exhaust pass, installed in the unused r5.3 composite1 slot.
#include "/lib/common.glsl"

#ifdef FRAGMENT_SHADER
flat in vec3 assimpExhaustSunView;

vec3 GetSunVector() {
    return assimpExhaustSunView;
}

int assimpExhaustStepBudget = 48;
#define ASSIMP_EXHAUST_STEP_BUDGET assimpExhaustStepBudget
#include "/lib/assimploader/exhaust_environment.glsl"
#include "/lib/assimploader/exhaust_volume.glsl"
#include "/lib/assimploader/temporal.glsl"

/* Pack format directives are parsed by Oculus, not compiled as GLSL identifiers.
const int colortex8Format = RGBA16F;
const int colortex9Format = RGBA16F;
const int colortex10Format = RGBA16F;
*/
const bool colortex8Clear = false;
const bool colortex9Clear = false;
const bool colortex10Clear = false;
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 1.0);
const vec4 colortex9ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex10ClearColor = vec4(0.0, 0.0, 0.0, 0.0);

vec3 assimpExhaustViewPosition(vec2 uv, float depth) {
    vec4 position = gbufferProjectionInverse * vec4(assimpExhaustUnjitterUv(uv) * 2.0 - 1.0,
        depth * 2.0 - 1.0, 1.0);
    return position.xyz / position.w;
}

void main() {
    vec2 halfSize = vec2(textureSize(colortex8, 0));
    vec2 uv = gl_FragCoord.xy / halfSize;
    float depth = texture2D(depthtex0, uv).r;
    vec3 viewPosition = assimpExhaustViewPosition(uv, depth);
    float sceneDistance = length(viewPosition);
    vec3 ray = normalize(mat3(gbufferModelViewInverse) * viewPosition);
    #ifdef DISTANT_HORIZONS
        float dhDepth = texture2D(dhDepthTex, uv).r;
        if (dhDepth < 1.0) {
            vec4 dhView = dhProjectionInverse * vec4(uv * 2.0 - 1.0, dhDepth * 2.0 - 1.0, 1.0);
            sceneDistance = min(sceneDistance, length(dhView.xyz / dhView.w));
        }
    #endif
    assimpExhaustStepBudget = assimpExhaustChooseSteps(ray, sceneDistance, halfSize);
    float jitter = fract(texture2D(noisetex, gl_FragCoord.xy / 128.0).r
        + float(frameCounter % 1024) * 0.61803398875);
    vec4 volume = assimpIntegrateExhaust(ray, sceneDistance, assimpExhaustTime, jitter);
    vec3 viewHeat = mat3(gbufferModelView) * assimpExhaustHeatVector;
    vec2 distortion = viewHeat.xy * vec2(gbufferProjection[0][0], gbufferProjection[1][1]);
    distortion *= 0.02 / max(assimpExhaustMeanDistance, 1.0);
    distortion = clamp(distortion, vec2(-0.012), vec2(0.012));
    vec4 historyMetadata;
    volume = assimpExhaustTemporal(uv, ray, sceneDistance, depth, volume, distortion, historyMetadata);
    /* RENDERTARGETS: 8,9,10 */
    gl_FragData[0] = volume;
    // XY: distortion UV; Z: scene depth; W: radiance/extinction weighted plume distance.
    gl_FragData[1] = vec4(distortion, min(sceneDistance, 65000.0), assimpExhaustMeanDistance);
    gl_FragData[2] = historyMetadata;
}
#endif

#ifdef VERTEX_SHADER
flat out vec3 assimpExhaustSunView;

void main() {
    gl_Position = ftransform();
    assimpExhaustSunView = GetSunVector();
}
#endif
