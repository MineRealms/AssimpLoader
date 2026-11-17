#ifndef ASSIMP_ENTITY_TEMPORAL_GLSL
#define ASSIMP_ENTITY_TEMPORAL_GLSL

bool assimpEntityReactive(ivec2 pixel) {
    ivec2 lastPixel = textureSize(colortex6, 0) - ivec2(1);
    // Include the silhouette: camera-only history cannot follow a skinned mesh.
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            ivec2 samplePixel = clamp(pixel + ivec2(x, y), ivec2(0), lastPixel);
            if (texelFetch(colortex6, samplePixel, 0).a < 0.5) return true;
        }
    }
    return false;
}

bool assimpEntityHistoryValid(vec2 previousCoord) {
    if (any(isnan(previousCoord)) || any(isinf(previousCoord))) return false;
    if (any(lessThanEqual(previousCoord, vec2(0.0))) || any(greaterThanEqual(previousCoord, vec2(1.0)))) return false;
    ivec2 size = textureSize(colortex2, 0);
    ivec2 lastPixel = size - ivec2(1);
    ivec2 firstPixel = ivec2(floor(previousCoord * vec2(size) - 0.5));
    // Test every texel in the bilinear footprint, including invalid silhouette history.
    // Filtering alpha first could round a small invalid contribution back to one.
    for (int y = 0; y < 2; ++y) {
        for (int x = 0; x < 2; ++x) {
            ivec2 samplePixel = clamp(firstPixel + ivec2(x, y), ivec2(0), lastPixel);
            if (texelFetch(colortex2, samplePixel, 0).a < 0.999) return false;
        }
    }
    return true;
}

#endif
