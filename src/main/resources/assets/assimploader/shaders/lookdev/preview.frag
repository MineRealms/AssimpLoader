#version 150

in vec3 viewNormal;
in vec2 texCoord;
in vec4 vertexColor;
uniform sampler2D Albedo;
uniform vec4 MaterialColor;
out vec4 fragColor;

void main() {
    vec4 base = texture(Albedo, texCoord) * vertexColor * MaterialColor;
    if (base.a < 0.01) discard;
    float normalLength = length(viewNormal);
    vec3 normal = normalLength > 0.00001 ? viewNormal / normalLength : vec3(0.0, 1.0, 0.0);
    if (!gl_FrontFacing) normal = -normal;
    float key = max(dot(normal, normalize(vec3(-0.4, 0.7, 0.8))), 0.0);
    float fill = max(dot(normal, normalize(vec3(0.8, 0.2, -0.5))), 0.0);
    fragColor = vec4(base.rgb * (0.38 + key * 0.52 + fill * 0.15), base.a);
}
