#version 330 core

// Vertex attributes
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec3 a_Normal;
layout(location = 2) in vec2 a_TexCoord;
layout(location = 3) in ivec4 a_BoneIndices;
layout(location = 4) in vec4 a_BoneWeights;

// Uniforms
uniform mat4 u_ModelViewProjection;
uniform mat4 u_ModelView;

// TBO for bone matrices (supports up to 16384 bones)
uniform samplerBuffer u_BoneMatricesTBO;

// Fallback: uniform array for compatibility (max 64 bones)
uniform mat4 u_BoneMatrices[64];

// Use TBO flag (set by renderer based on GPU capability)
uniform bool u_UseTBO;

// Outputs
out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;

// Fetch bone matrix from TBO
mat4 getBoneMatrixTBO(int boneIndex) {
    int offset = boneIndex * 4;
    return mat4(
        texelFetch(u_BoneMatricesTBO, offset + 0),
        texelFetch(u_BoneMatricesTBO, offset + 1),
        texelFetch(u_BoneMatricesTBO, offset + 2),
        texelFetch(u_BoneMatricesTBO, offset + 3)
    );
}

// Fetch bone matrix from uniform array (fallback)
mat4 getBoneMatrixUniform(int boneIndex) {
    if (boneIndex >= 0 && boneIndex < 64) {
        return u_BoneMatrices[boneIndex];
    }
    return mat4(1.0);
}

// Get bone matrix (TBO or uniform based on capability)
mat4 getBoneMatrix(int boneIndex) {
    if (u_UseTBO) {
        return getBoneMatrixTBO(boneIndex);
    } else {
        return getBoneMatrixUniform(boneIndex);
    }
}

void main() {
    // Calculate skinned transformation matrix
    mat4 boneTransform = mat4(0.0);

    // Accumulate bone transformations weighted by vertex weights
    if (a_BoneIndices.x >= 0) {
        boneTransform += getBoneMatrix(a_BoneIndices.x) * a_BoneWeights.x;
    }
    if (a_BoneIndices.y >= 0) {
        boneTransform += getBoneMatrix(a_BoneIndices.y) * a_BoneWeights.y;
    }
    if (a_BoneIndices.z >= 0) {
        boneTransform += getBoneMatrix(a_BoneIndices.z) * a_BoneWeights.z;
    }
    if (a_BoneIndices.w >= 0) {
        boneTransform += getBoneMatrix(a_BoneIndices.w) * a_BoneWeights.w;
    }

    // If no bones, use identity matrix
    if (a_BoneIndices.x < 0 && a_BoneIndices.y < 0 &&
        a_BoneIndices.z < 0 && a_BoneIndices.w < 0) {
        boneTransform = mat4(1.0);
    }

    // Apply bone transformation to position and normal
    vec4 skinnedPosition = boneTransform * vec4(a_Position, 1.0);
    vec4 skinnedNormal = boneTransform * vec4(a_Normal, 0.0);

    // Transform to view space
    vec4 viewPosition = u_ModelView * skinnedPosition;
    v_Position = viewPosition.xyz;
    v_Normal = normalize(mat3(u_ModelView) * skinnedNormal.xyz);
    v_TexCoord = a_TexCoord;

    // Final projection
    gl_Position = u_ModelViewProjection * skinnedPosition;
}
