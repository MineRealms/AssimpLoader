/*
 * Gaussian Splatting Vertex Shader
 * Adapted from https://github.com/limacv/GaussianSplattingViewer
 * Original license: MIT License, Copyright (c) 2023 Li Ma
 *
 * Modified for Minecraft 1.20.1 compatibility:
 * - Changed #version 430 -> #version 330 (Minecraft baseline)
 * - Kept SSBO for now (will add TBO fallback later)
 */

#version 330 core

#define SH_C0 0.28209479177387814
#define SH_C1 0.4886025119029199

#define SH_C2_0 1.0925484305920792
#define SH_C2_1 -1.0925484305920792
#define SH_C2_2 0.31539156525252005
#define SH_C2_3 -1.0925484305920792
#define SH_C2_4 0.5462742152960396

#define SH_C3_0 -0.5900435899266435
#define SH_C3_1 2.890611442640554
#define SH_C3_2 -0.4570457994644658
#define SH_C3_3 0.3731763325901154
#define SH_C3_4 -0.4570457994644658
#define SH_C3_5 1.445305721320277
#define SH_C3_6 -0.5900435899266435

layout(location = 0) in vec2 position;

#define POS_IDX 0
#define ROT_IDX 3
#define SCALE_IDX 7
#define OPACITY_IDX 10
#define SH_IDX 11

// SSBO requires OpenGL 4.3, but we'll use it with extension check
// TODO: Add TBO fallback for Oculus compatibility
#extension GL_ARB_shader_storage_buffer_object : require

layout (std430, binding=0) buffer gaussian_data {
    float g_data[];
};

layout (std430, binding=1) buffer gaussian_order {
    int gi[];
};

uniform mat4 view_matrix;
uniform mat4 projection_matrix;
uniform vec3 hfovxy_focal;
uniform vec3 cam_pos;
uniform int sh_dim;
uniform float scale_modifier;
uniform int render_mod;  // > 0 render 0-ith SH dim, -1 depth, -2 billboard, -3 gaussian

out vec3 color;
out float alpha;
out vec3 conic;
out vec2 coordxy;

mat3 computeCov3D(vec3 scale, vec4 q)
{
    mat3 S = mat3(0.0);
    S[0][0] = scale.x;
    S[1][1] = scale.y;
    S[2][2] = scale.z;
    float r = q.x;
    float x = q.y;
    float y = q.z;
    float z = q.w;

    mat3 R = mat3(
        1.0 - 2.0 * (y * y + z * z), 2.0 * (x * y - r * z), 2.0 * (x * z + r * y),
        2.0 * (x * y + r * z), 1.0 - 2.0 * (x * x + z * z), 2.0 * (y * z - r * x),
        2.0 * (x * z - r * y), 2.0 * (y * z + r * x), 1.0 - 2.0 * (x * x + y * y)
    );

    mat3 M = S * R;
    mat3 Sigma = transpose(M) * M;
    return Sigma;
}

vec3 computeCov2D(vec4 mean_view, float focal_x, float focal_y, float tan_fovx, float tan_fovy, mat3 cov3D, mat4 viewmatrix)
{
    vec4 t = mean_view;
    float limx = 1.3 * tan_fovx;
    float limy = 1.3 * tan_fovy;
    float txtz = t.x / t.z;
    float tytz = t.y / t.z;
    t.x = min(limx, max(-limx, txtz)) * t.z;
    t.y = min(limy, max(-limy, tytz)) * t.z;

    mat3 J = mat3(
        focal_x / t.z, 0.0, -(focal_x * t.x) / (t.z * t.z),
        0.0, focal_y / t.z, -(focal_y * t.y) / (t.z * t.z),
        0.0, 0.0, 0.0
    );
    mat3 W = transpose(mat3(viewmatrix));
    mat3 T = W * J;

    mat3 cov = transpose(T) * transpose(cov3D) * T;
    cov[0][0] += 0.3;
    cov[1][1] += 0.3;
    return vec3(cov[0][0], cov[0][1], cov[1][1]);
}

vec3 get_vec3(int offset)
{
    return vec3(g_data[offset], g_data[offset + 1], g_data[offset + 2]);
}

vec4 get_vec4(int offset)
{
    return vec4(g_data[offset], g_data[offset + 1], g_data[offset + 2], g_data[offset + 3]);
}

void main()
{
    int boxid = gi[gl_InstanceID];
    int total_dim = 3 + 4 + 3 + 1 + sh_dim;
    int start = boxid * total_dim;
    vec4 g_pos = vec4(get_vec3(start + POS_IDX), 1.0);
    vec4 g_pos_view = view_matrix * g_pos;
    vec4 g_pos_screen = projection_matrix * g_pos_view;
    g_pos_screen.xyz = g_pos_screen.xyz / g_pos_screen.w;
    g_pos_screen.w = 1.0;

    // Early culling
    if (any(greaterThan(abs(g_pos_screen.xyz), vec3(1.3))))
    {
        gl_Position = vec4(-100.0, -100.0, -100.0, 1.0);
        return;
    }

    vec4 g_rot = get_vec4(start + ROT_IDX);
    vec3 g_scale = get_vec3(start + SCALE_IDX);
    float g_opacity = g_data[start + OPACITY_IDX];

    mat3 cov3d = computeCov3D(g_scale * scale_modifier, g_rot);
    vec2 wh = 2.0 * hfovxy_focal.xy * hfovxy_focal.z;
    vec3 cov2d = computeCov2D(g_pos_view,
                              hfovxy_focal.z,
                              hfovxy_focal.z,
                              hfovxy_focal.x,
                              hfovxy_focal.y,
                              cov3d,
                              view_matrix);

    // Invert covariance (EWA algorithm)
    float det = (cov2d.x * cov2d.z - cov2d.y * cov2d.y);
    if (det == 0.0)
    {
        gl_Position = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }

    float det_inv = 1.0 / det;
    conic = vec3(cov2d.z * det_inv, -cov2d.y * det_inv, cov2d.x * det_inv);

    vec2 quadwh_scr = vec2(3.4 * sqrt(cov2d.x), 3.4 * sqrt(cov2d.z));
    vec2 quadwh_ndc = quadwh_scr / wh * 2.0;
    g_pos_screen.xy = g_pos_screen.xy + position * quadwh_ndc;
    coordxy = position * quadwh_scr;
    gl_Position = g_pos_screen;

    alpha = g_opacity;

    if (render_mod == -1)
    {
        float depth = -g_pos_view.z;
        depth = depth < 0.05 ? 1.0 : depth;
        depth = 1.0 / depth;
        color = vec3(depth, depth, depth);
        return;
    }

    // Convert SH to color
    int sh_start = start + SH_IDX;
    vec3 dir = g_pos.xyz - cam_pos;
    dir = normalize(dir);
    color = SH_C0 * get_vec3(sh_start);

    if (sh_dim > 3 && render_mod >= 1)
    {
        float x = dir.x;
        float y = dir.y;
        float z = dir.z;
        color = color - SH_C1 * y * get_vec3(sh_start + 1 * 3) + SH_C1 * z * get_vec3(sh_start + 2 * 3) - SH_C1 * x * get_vec3(sh_start + 3 * 3);

        if (sh_dim > 12 && render_mod >= 2)
        {
            float xx = x * x, yy = y * y, zz = z * z;
            float xy = x * y, yz = y * z, xz = x * z;
            color = color +
                SH_C2_0 * xy * get_vec3(sh_start + 4 * 3) +
                SH_C2_1 * yz * get_vec3(sh_start + 5 * 3) +
                SH_C2_2 * (2.0 * zz - xx - yy) * get_vec3(sh_start + 6 * 3) +
                SH_C2_3 * xz * get_vec3(sh_start + 7 * 3) +
                SH_C2_4 * (xx - yy) * get_vec3(sh_start + 8 * 3);

            if (sh_dim > 27 && render_mod >= 3)
            {
                color = color +
                    SH_C3_0 * y * (3.0 * xx - yy) * get_vec3(sh_start + 9 * 3) +
                    SH_C3_1 * xy * z * get_vec3(sh_start + 10 * 3) +
                    SH_C3_2 * y * (4.0 * zz - xx - yy) * get_vec3(sh_start + 11 * 3) +
                    SH_C3_3 * z * (2.0 * zz - 3.0 * xx - 3.0 * yy) * get_vec3(sh_start + 12 * 3) +
                    SH_C3_4 * x * (4.0 * zz - xx - yy) * get_vec3(sh_start + 13 * 3) +
                    SH_C3_5 * z * (xx - yy) * get_vec3(sh_start + 14 * 3) +
                    SH_C3_6 * x * (xx - 3.0 * yy) * get_vec3(sh_start + 15 * 3);
            }
        }
    }
    color += 0.5;
}
