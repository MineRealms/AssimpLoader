/*
 * Gaussian Splatting Fragment Shader
 * Adapted from https://github.com/limacv/GaussianSplattingViewer
 * Original license: MIT License, Copyright (c) 2023 Li Ma
 *
 * Modified for Minecraft 1.20.1 compatibility:
 * - Changed #version 430 -> #version 330 (Minecraft baseline)
 */

#version 330 core

in vec3 color;
in float alpha;
in vec3 conic;
in vec2 coordxy;

uniform int render_mod;  // > 0 render 0-ith SH dim, -1 depth, -2 billboard, -3 flat ball, -4 gaussian ball

out vec4 FragColor;

void main()
{
    if (render_mod == -2)
    {
        FragColor = vec4(color, 1.0);
        return;
    }

    float power = -0.5 * (conic.x * coordxy.x * coordxy.x + conic.z * coordxy.y * coordxy.y) - conic.y * coordxy.x * coordxy.y;
    if (power > 0.0)
        discard;
    float opacity = min(0.99, alpha * exp(power));
    if (opacity < 1.0 / 255.0)
        discard;
    FragColor = vec4(color, opacity);

    // Special shading effects
    if (render_mod == -3)
        FragColor.a = FragColor.a > 0.22 ? 1.0 : 0.0;
    else if (render_mod == -4)
    {
        FragColor.a = FragColor.a > 0.22 ? 1.0 : 0.0;
        FragColor.rgb = FragColor.rgb * exp(power);
    }
}
