#ifndef ASSIMP_EXHAUST_TEMPORAL_GLSL
#define ASSIMP_EXHAUST_TEMPORAL_GLSL

uniform sampler2D colortex10;

#ifdef ASSIMP_EXHAUST_SCENE_REACTIVE
uniform int assimpExhaustCount;
uniform int assimpExhaustPuffCount;
uniform int assimpExhaustHistoryValid;

bool assimpExhaustSceneReactive(vec2 uv) {
    // Avoid filtering the moving flame a second time in the pack's scene TAA.
    return ((assimpExhaustCount + assimpExhaustPuffCount) > 0 && assimpExhaustHistoryValid == 0)
        || abs(texture2D(colortex10, uv).x) > 0.05;
}
#else

#ifdef TAA
#include "/lib/antialiasing/jitter.glsl"
#endif

#ifndef ASSIMP_EXHAUST_HISTORY_UNIFORMS
#define ASSIMP_EXHAUST_HISTORY_UNIFORMS
uniform vec4 assimpExhaustPreviousOriginRadius[16];
uniform vec4 assimpExhaustPreviousAxisLength[16];
uniform float assimpExhaustDeltaTime;
uniform int assimpExhaustHistoryValid;
uniform vec3 assimpExhaustPreviousCameraDelta;
#endif

float assimpExhaustLuminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

int assimpExhaustIntersectedEmitters;

vec2 assimpExhaustUnjitterUv(vec2 uv) {
    #ifdef TAA
        return TAAJitter(uv, -0.5);
    #else
        return uv;
    #endif
}

vec2 assimpExhaustEncodedSize(vec2 size) {
    // Exact half-float encoding of integer dimensions, including widths above 2048.
    return exp2(floor(size / 1024.0) - 8.0) * (1.0 + mod(size, 1024.0) / 1024.0);
}

int assimpExhaustChooseSteps(vec3 ray, float sceneDistance, vec2 halfSize) {
    float diameterPixels = 0.0;
    int hits = 0;
    for (int emitter = 0; emitter < ASSIMP_EXHAUST_MAX; emitter++) {
        if (emitter >= assimpExhaustCount) break;
        vec2 interval;
        if (!assimpExhaustInterval(emitter, ray, sceneDistance, interval)) continue;
        hits++;
        vec4 nozzle = assimpExhaustOriginRadius[emitter];
        vec4 plume = assimpExhaustAxisLength[emitter];
        float radius = max(nozzle.w, nozzle.w + assimpExhaustShape[emitter].x * plume.w) * 2.5;
        vec3 center = nozzle.xyz + normalize(plume.xyz) * plume.w * 0.5;
        float boundRadius = length(vec2(radius, plume.w * 0.5));
        float nearestDepth = -(gbufferModelView * vec4(center, 1.0)).z - boundRadius;
        float projected = halfSize.y * abs(gbufferProjection[1][1])
            * boundRadius / max(nearestDepth, 0.25);
        diameterPixels = max(diameterPixels, projected);
    }
    assimpExhaustIntersectedEmitters = hits;
    // Overlapping volumes need the full union budget even when individually small.
    if (hits > 1 || diameterPixels >= 220.0) return 48;
    if (diameterPixels >= 80.0 || assimpExhaustPuffCount > 0) return 32;
    if (diameterPixels >= 24.0) return 24;
    return 16;
}

vec3 assimpExhaustRotateAxis(vec3 point, vec3 from, vec3 to) {
    vec3 rotation = cross(from, to);
    float cosine = clamp(dot(from, to), -1.0, 1.0);
    return point + cross(rotation, point)
        + cross(rotation, cross(rotation, point)) / max(1.0 + cosine, 0.001);
}

bool assimpExhaustPreviousUv(vec3 previousPoint, out vec2 previousUv) {
    vec4 clip = gbufferPreviousProjection * gbufferPreviousModelView * vec4(previousPoint, 1.0);
    if (clip.w <= 0.0001) return false;
    previousUv = clip.xy / clip.w * 0.5 + 0.5;
    #ifdef TAA
        int previousIndex = (int(framemod8) + 7) % 8;
        vec2 previousJitter = jitterOffsets[previousIndex] * (0.5 / vec2(viewWidth, viewHeight));
        #if TAA_MODE == 1
            previousJitter *= 0.125;
        #endif
        previousUv += previousJitter;
    #endif
    vec2 border = 1.5 / vec2(textureSize(colortex8, 0));
    return all(greaterThan(previousUv, border)) && all(lessThan(previousUv, 1.0 - border));
}

bool assimpExhaustHistorySizeValid(vec4 metadata, vec2 size) {
    return all(equal(metadata.zw, assimpExhaustEncodedSize(size)));
}

vec4 assimpExhaustTemporal(vec2 uv, vec3 ray, float sceneDistance, float sceneDepth,
                           vec4 current, inout vec2 distortion, out vec4 metadata) {
    vec2 size = vec2(textureSize(colortex8, 0));
    if (!all(greaterThanEqual(current, vec4(0.0)))
        || !all(lessThanEqual(current, vec4(1e30)))) current = vec4(0.0, 0.0, 0.0, 1.0);
    current.rgb = min(current.rgb, vec3(65000.0));
    current.a = clamp(current.a, 0.0, 1.0);
    // Quad derivatives bound the current local neighborhood without raymarching
    // extra rays or sampling the attachment being written by this pass.
    vec3 neighborhoodRadius = min(max(fwidth(current.rgb) * 0.75,
        current.rgb * 0.15 + vec3(0.03)), max(current.rgb * 0.65, vec3(0.08)));
    float transmissionRadius = clamp(fwidth(current.a) * 0.75, 0.025, 0.15);
    float luma = assimpExhaustLuminance(current.rgb);
    bool visible = current.a < 0.99995 || luma > 0.00005;
    metadata = vec4(0.0, 1.0, assimpExhaustEncodedSize(size));
    if (!visible || assimpExhaustMeanDistance <= 0.0) {
        // Keep a two-frame reactive marker so scene TAA also releases extinguished pixels.
        vec4 oldMetadata = texture2D(colortex10, uv);
        if (assimpExhaustHistorySizeValid(oldMetadata, size)) {
            metadata.x = oldMetadata.x > 0.0 ? -2.0 : min(oldMetadata.x + 1.0, 0.0);
        }
        metadata.y = 0.0;
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    vec3 point = ray * assimpExhaustMeanDistance;
    // The integral selects the representative source. Puff-only or ambiguous
    // overlapping media have no single nozzle transform and use current samples.
    int emitter = assimpExhaustIntersectedEmitters == 1 ? assimpExhaustDominantEmitter : -1;
    // An ambiguous anchor still marks the pixel reactive, but never accumulates history.
    metadata.x = float(emitter >= 0 ? emitter + 1 : ASSIMP_EXHAUST_MAX + 1);
    if (emitter < 0 || assimpExhaustHistoryValid == 0
        || assimpExhaustDeltaTime <= 0.0 || assimpExhaustDeltaTime > 0.1) return current;

    vec2 anchorInterval;
    if (!assimpExhaustInterval(emitter, ray, sceneDistance, anchorInterval)
        || assimpExhaustMeanDistance < anchorInterval.x || assimpExhaustMeanDistance > anchorInterval.y) {
        metadata.x = float(ASSIMP_EXHAUST_MAX + 1);
        return current;
    }

    vec4 nozzle = assimpExhaustOriginRadius[emitter];
    vec4 previousNozzle = assimpExhaustPreviousOriginRadius[emitter];
    vec4 plume = assimpExhaustAxisLength[emitter];
    vec4 previousPlume = assimpExhaustPreviousAxisLength[emitter];
    if (previousNozzle.w <= 0.0 || previousPlume.w <= 0.0) return current;
    vec3 axis = normalize(plume.xyz);
    vec3 previousAxis = normalize(previousPlume.xyz);
    float radiusRatio = previousNozzle.w / max(nozzle.w, 0.001);
    float lengthRatio = previousPlume.w / max(plume.w, 0.001);
    if (dot(axis, previousAxis) < 0.96 || abs(radiusRatio - 1.0) > 0.15
        || abs(lengthRatio - 1.0) > 0.15) return current;

    vec3 local = point - nozzle.xyz;
    vec3 axial = axis * dot(local, axis);
    vec3 scaledLocal = (local - axial) * radiusRatio + axial * lengthRatio;
    vec3 previousPoint = previousNozzle.xyz
        + assimpExhaustRotateAxis(scaledLocal, axis, previousAxis);
    vec2 previousUv;
    if (!assimpExhaustPreviousUv(previousPoint, previousUv)) return current;
    vec2 motion = (previousUv - uv) * size;
    if (dot(motion, motion) > 576.0) return current;

    // Pick a depth/identity-compatible texel before sampling radiance. Bilinear metadata
    // would interpolate source slots and blur foreground geometry across the history.
    vec2 pixel = previousUv * size - 0.5;
    ivec2 base = ivec2(floor(pixel));
    float previousDistance = length(previousPoint);
    float bestError = 1e20;
    vec4 history = current;
    vec4 historyMetadata = vec4(0.0);
    vec4 historyGeometry = vec4(0.0);
    for (int candidate = 0; candidate < 4; candidate++) {
        ivec2 offset = ivec2(candidate - (candidate / 2) * 2, candidate / 2);
        ivec2 coordinate = clamp(base + offset, ivec2(0), ivec2(size) - 1);
        vec4 candidateMetadata = texelFetch(colortex10, coordinate, 0);
        if (abs(candidateMetadata.x - float(emitter + 1)) > 0.1
            || !assimpExhaustHistorySizeValid(candidateMetadata, size)
            || candidateMetadata.y < 1.0) continue;
        vec4 geometry = texelFetch(colortex9, coordinate, 0);
        float error = abs(geometry.w - previousDistance);
        float tolerance = 0.2 + 0.06 * previousDistance;
        if (error > tolerance || geometry.z + 0.05 < previousDistance) continue;
        error += length(vec2(coordinate) - pixel) * 0.025;
        if (error >= bestError) continue;
        bestError = error;
        history = texelFetch(colortex8, coordinate, 0);
        historyMetadata = candidateMetadata;
        historyGeometry = geometry;
    }
    if (bestError > 1e19) return current;

    // Independently reproject the visible background. This rejects disocclusion when
    // foreground geometry moves across the volume instead of attaching history to it.
    if (sceneDepth < 0.999999 && sceneDistance < 60000.0) {
        vec3 previousSurface = ray * sceneDistance + assimpExhaustPreviousCameraDelta;
        vec2 surfaceUv;
        if (!assimpExhaustPreviousUv(previousSurface, surfaceUv)) return current;
        vec4 oldSurface = texture2D(colortex9, surfaceUv);
        if (abs(oldSurface.z - length(previousSurface)) > 0.15 + length(previousSurface) * 0.015)
            return current;
    }

    float historyLuma = assimpExhaustLuminance(history.rgb);
    if (!(historyLuma >= 0.0 && historyLuma <= 65000.0 && history.a >= 0.0 && history.a <= 1.0))
        return current;
    float lumaChange = abs(log2((luma + 0.05) / (historyLuma + 0.05)));
    if (lumaChange > 2.0 || abs(current.a - history.a) > 0.35) return current;

    // The field is animated, so a short physical-time response is safer than the
    // long TAA history used for static geometry. This blend is entirely scene-linear.
    float frames = min(historyMetadata.y, 16.0);
    float weight = min(frames / (frames + 1.0), exp(-assimpExhaustDeltaTime / 0.055));
    weight *= exp(-length(motion) * 0.06) * exp(-lumaChange * 0.7);
    history.rgb = clamp(history.rgb, max(current.rgb - neighborhoodRadius, vec3(0.0)),
        current.rgb + neighborhoodRadius);
    history.a = clamp(history.a, max(current.a - transmissionRadius, 0.0),
        min(current.a + transmissionRadius, 1.0));
    metadata.y = min(frames + 1.0, 16.0);
    distortion = mix(distortion, historyGeometry.xy, min(weight, 0.35));
    return mix(current, history, weight);
}

#endif
#endif
