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
uniform mat4 u_BoneMatrices[256];

// Outputs
out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;

void main() {
    // Calculate skinned transformation matrix
    mat4 boneTransform =
        u_BoneMatrices[a_BoneIndices.x] * a_BoneWeights.x +
        u_BoneMatrices[a_BoneIndices.y] * a_BoneWeights.y +
        u_BoneMatrices[a_BoneIndices.z] * a_BoneWeights.z +
        u_BoneMatrices[a_BoneIndices.w] * a_BoneWeights.w;

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
