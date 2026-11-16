#version 430 core
layout(location=0) in vec3 Position;
layout(location=1) in vec3 Normal;
layout(location=2) in vec2 UV;
layout(location=3) in ivec4 BoneIndices;
layout(location=4) in vec4 BoneWeights;
layout(location=5) in uint InstanceIndex;
struct Instance { mat4 modelView; vec4 tint; ivec4 light; ivec4 palette; };
layout(std430,binding=0) readonly buffer Instances { Instance instances[]; };
layout(std430,binding=1) readonly buffer Bones { mat4 bones[]; };
uniform mat4 Projection;
uniform int FogShape;
out vec3 v_Position;
out vec3 v_Normal;
out vec2 v_TexCoord;
flat out vec4 v_Tint;
flat out ivec4 v_Light;
flat out int v_Skinned;
out float v_StaticDiffuse;
out float v_StaticDistance;
void main() {
    Instance instance=instances[InstanceIndex];
    mat4 skin=mat4(0.0);
    float total=0.0;
    for(int slot=0;slot<4;slot++) {
        int index=BoneIndices[slot]; float weight=BoneWeights[slot];
        if(index>=0 && index<instance.palette.y && weight>0.0) {
            skin+=bones[instance.palette.x+index]*weight; total+=weight;
        }
    }
    skin=total>0.000001?skin/total:mat4(1.0);
    vec4 position=instance.modelView*skin*vec4(Position,1.0);
    mat3 normalMatrix=mat3(instance.modelView*skin);
    vec3 normal=abs(determinant(normalMatrix))>0.000001?transpose(inverse(normalMatrix))*Normal:Normal;
    v_Normal=length(normal)>0.000001?normalize(normal):vec3(0.0,1.0,0.0);
    v_StaticDiffuse=min(1.0,0.6+0.4*max(dot(v_Normal,normalize(vec3(0.2,1.0,0.3))),0.0));
    v_StaticDistance=FogShape==0?length(position.xyz):max(length(position.xz),abs(position.y));
    v_Position=position.xyz; v_TexCoord=UV; v_Tint=instance.tint; v_Light=instance.light;v_Skinned=instance.palette.z;
    gl_Position=Projection*position;
}
