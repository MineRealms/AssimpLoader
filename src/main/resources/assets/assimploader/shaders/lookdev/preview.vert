#version 150

in vec3 Position;
in vec3 Normal;
in vec2 UV;
in vec4 Color;
in vec4 BoneIds0;
in vec4 BoneIds1;
in vec4 BoneWeights0;
in vec4 BoneWeights1;
uniform samplerBuffer Bones;
uniform int BoneCount;
uniform mat4 Mvp;
uniform mat4 ModelView;
uniform mat3 NormalMatrix;
out vec3 viewNormal;
out vec2 texCoord;
out vec4 vertexColor;
out vec3 viewPosition;

mat4 boneMatrix(int index) {
    int i=index*4;
    return mat4(texelFetch(Bones,i),texelFetch(Bones,i+1),texelFetch(Bones,i+2),texelFetch(Bones,i+3));
}

void main() {
    mat4 skin=mat4(0.0);
    float total=0.0;
    for(int i=0;i<8;i++) {
        float weight=i<4?BoneWeights0[i]:BoneWeights1[i-4];
        int id=int(i<4?BoneIds0[i]:BoneIds1[i-4]);
        if(weight>0.0&&id>=0&&id<BoneCount){skin+=boneMatrix(id)*weight;total+=weight;}
    }
    if(total>0.00001)skin/=total;else skin=mat4(1.0);
    vec4 p=skin*vec4(Position,1.0);
    gl_Position = Mvp * p;
    viewPosition=(ModelView*p).xyz;
    mat3 deform=mat3(skin);
    vec3 n=abs(determinant(deform))>0.00000001?transpose(inverse(deform))*Normal:Normal;
    viewNormal = NormalMatrix * n;
    texCoord = UV;
    vertexColor = Color;
}
