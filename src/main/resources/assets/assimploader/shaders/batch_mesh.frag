#version 430 core
in vec3 v_Position;
in vec3 v_Normal;
in vec2 v_TexCoord;
flat in vec4 v_Tint;
flat in ivec4 v_Light;
flat in int v_Skinned;
in float v_StaticDiffuse;
in float v_StaticDistance;
uniform sampler2D Albedo;
uniform sampler2D Lightmap;
uniform sampler2D Overlay;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int FogShape;
out vec4 color;
void main() {
    color=texture(Albedo,v_TexCoord)*v_Tint*ColorModulator;
    if(color.a<0.1) discard;
    float diffuse=max(dot(normalize(v_Normal),normalize(vec3(0.2,1.0,0.3))),0.0);
    vec3 lighting=v_Skinned!=0?min(vec3(1.0),vec3(0.3+diffuse)):vec3(v_StaticDiffuse);
    color.rgb*=lighting;
    vec4 overlay=texelFetch(Overlay,v_Light.zw,0);
    color.rgb=mix(overlay.rgb,color.rgb,overlay.a);
    color*=texelFetch(Lightmap,clamp(v_Light.xy/16,ivec2(0),ivec2(15)),0);
    float distance=v_Skinned==0?v_StaticDistance:(FogShape==0?length(v_Position):max(length(v_Position.xz),abs(v_Position.y)));
    if(distance>FogStart) {
        float amount=distance>=FogEnd?1.0:smoothstep(FogStart,FogEnd,distance);
        color.rgb=mix(color.rgb,FogColor.rgb,amount*FogColor.a);
    }
}
