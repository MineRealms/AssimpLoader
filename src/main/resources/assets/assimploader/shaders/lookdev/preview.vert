#version 150

in vec3 Position;
in vec3 Normal;
in vec2 UV;
in vec4 Color;
uniform mat4 Mvp;
uniform mat3 NormalMatrix;
out vec3 viewNormal;
out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = Mvp * vec4(Position, 1.0);
    viewNormal = NormalMatrix * Normal;
    texCoord = UV;
    vertexColor = Color;
}
