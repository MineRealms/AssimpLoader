#version 330 core
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec3 a_Normal;
layout(location = 2) in vec2 a_TexCoord;
layout(location = 3) in ivec4 a_BoneIndices;
layout(location = 4) in vec4 a_BoneWeights;
uniform mat4 u_ModelViewProjection;
uniform mat4 u_ModelView;
uniform mat4 u_BoneMatrices[64];
uniform int u_BoneCount;
uniform samplerBuffer u_MorphDeltas;
uniform float u_MorphWeights[32];
uniform int u_MorphCount;
uniform int u_MorphVertices;
out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;

void main() {
    mat4 skin = mat4(0.0);
    float total = 0.0;
    for (int slot = 0; slot < 4; slot++) {
        int index = a_BoneIndices[slot];
        float weight = a_BoneWeights[slot];
        if (index >= 0 && index < min(u_BoneCount, 64) && weight > 0.0) {
            skin += u_BoneMatrices[index] * weight;
            total += weight;
        }
    }
    skin = total > 0.000001 ? skin / total : mat4(1.0);
    vec3 morphedPosition=a_Position;
    vec3 morphedNormal=a_Normal;
    bool morphActive=false;
    for(int target=0;target<u_MorphCount;target++) {
        float weight=u_MorphWeights[target];
        if(weight==0.0) continue;
        int offset=(target*u_MorphVertices+gl_VertexID)*2;
        morphedPosition+=texelFetch(u_MorphDeltas,offset).xyz*weight;
        morphedNormal+=texelFetch(u_MorphDeltas,offset+1).xyz*weight;
        morphActive=true;
    }
    if(morphActive) morphedNormal=length(morphedNormal)>0.00000001?normalize(morphedNormal):vec3(0.0,1.0,0.0);
    vec4 position = skin * vec4(morphedPosition, 1.0);
    mat3 normalMatrix = mat3(u_ModelView * skin);
    vec3 normal = abs(determinant(normalMatrix)) > 0.000001
            ? transpose(inverse(normalMatrix)) * morphedNormal : morphedNormal;
    v_Normal = length(normal) > 0.000001 ? normalize(normal) : vec3(0.0, 1.0, 0.0);
    v_Position = (u_ModelView * position).xyz;
    v_TexCoord = a_TexCoord;
    gl_Position = u_ModelViewProjection * position;
}
