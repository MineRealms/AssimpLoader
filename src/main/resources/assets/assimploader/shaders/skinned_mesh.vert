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
uniform mat4 u_BoneMatrices[64];  // Reduced from 256 to 64 for compatibility

// Outputs
out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;

void main() {
    // Calculate skinned transformation matrix with bounds checking
    mat4 boneTransform = mat4(1.0);

    if (a_BoneIndices.x >= 0 && a_BoneIndices.x < 64) {
        boneTransform = u_BoneMatrices[a_BoneIndices.x] * a_BoneWeights.x;
    }
    if (a_BoneIndices.y >= 0 && a_BoneIndices.y < 64) {
        boneTransform += u_BoneMatrices[a_BoneIndices.y] * a_BoneWeights.y;
    }
    if (a_BoneIndices.z >= 0 && a_BoneIndices.z < 64) {
        boneTransform += u_BoneMatrices[a_BoneIndices.z] * a_BoneWeights.z;
    }
    if (a_BoneIndices.w >= 0 && a_BoneIndices.w < 64) {
        boneTransform += u_BoneMatrices[a_BoneIndices.w] * a_BoneWeights.w;
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
