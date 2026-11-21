#pragma once

#include "simd.hpp"

#include <algorithm>
#include <cmath>
#include <limits>

namespace fk {

inline constexpr float deg_to_rad = 0.017453292519943295f;
inline constexpr float rad_to_deg = 57.29577951308232f;
inline constexpr float max_constraint_deg = 180.0f;
inline constexpr float epsilon = 1.0e-8f;

struct Vec3 {
    float x{}, y{}, z{};

    constexpr Vec3() noexcept = default;
    constexpr Vec3(float xx, float yy, float zz) noexcept : x(xx), y(yy), z(zz) {}

    static Vec3 load(const float* p) noexcept { return {p[0], p[1], p[2]}; }
    void store(float* p) const noexcept { p[0] = x; p[1] = y; p[2] = z; }

    [[nodiscard]] constexpr Vec3 operator+(Vec3 o) const noexcept { return {x + o.x, y + o.y, z + o.z}; }
    [[nodiscard]] constexpr Vec3 operator-(Vec3 o) const noexcept { return {x - o.x, y - o.y, z - o.z}; }
    [[nodiscard]] constexpr Vec3 operator*(float s) const noexcept { return {x * s, y * s, z * s}; }
    [[nodiscard]] constexpr Vec3 operator-() const noexcept { return {-x, -y, -z}; }

    Vec3& operator+=(Vec3 o) noexcept { x += o.x; y += o.y; z += o.z; return *this; }
    Vec3& operator-=(Vec3 o) noexcept { x -= o.x; y -= o.y; z -= o.z; return *this; }

    [[nodiscard]] float length_sq() const noexcept { return x * x + y * y + z * z; }
    [[nodiscard]] float length() const noexcept { return std::sqrt(length_sq()); }

    [[nodiscard]] Vec3 normalized() const noexcept {
        const float l = length();
        return l > epsilon ? (*this) * (1.0f / l) : Vec3{0.0f, 0.0f, 1.0f};
    }

    [[nodiscard]] float dot(Vec3 o) const noexcept { return x * o.x + y * o.y + z * o.z; }

    [[nodiscard]] Vec3 cross(Vec3 o) const noexcept {
        return {y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x};
    }

    [[nodiscard]] bool approximately(Vec3 o, float tol) const noexcept {
        return std::abs(x - o.x) < tol && std::abs(y - o.y) < tol && std::abs(z - o.z) < tol;
    }
};

[[nodiscard]] inline float unit_dot(Vec3 a, Vec3 b) noexcept {
    return std::clamp(a.normalized().dot(b.normalized()), -1.0f, 1.0f);
}

[[nodiscard]] inline float angle_deg(Vec3 a, Vec3 b) noexcept {
    return std::acos(unit_dot(a, b)) * rad_to_deg;
}

[[nodiscard]] inline float signed_angle_deg(Vec3 reference, Vec3 other, Vec3 normal) noexcept {
    const float unsigned_angle = angle_deg(reference, other);
    const float sign_value = unit_dot(reference.cross(other), normal);
    return unsigned_angle * (sign_value < 0.0f ? -1.0f : 1.0f);
}

[[nodiscard]] inline Vec3 rotate_about_axis(Vec3 src, float angle_rads, Vec3 axis) noexcept {
    const float st = std::sin(angle_rads);
    const float ct = std::cos(angle_rads);
    const float omc = 1.0f - ct;
    const float xy = axis.x * axis.y * omc;
    const float xz = axis.x * axis.z * omc;
    const float yz = axis.y * axis.z * omc;
    return {
        (axis.x * axis.x * omc + ct) * src.x + (xy - axis.z * st) * src.y + (xz + axis.y * st) * src.z,
        (xy + axis.z * st) * src.x + (axis.y * axis.y * omc + ct) * src.y + (yz - axis.x * st) * src.z,
        (xz - axis.y * st) * src.x + (yz + axis.x * st) * src.y + (axis.z * axis.z * omc + ct) * src.z
    };
}

[[nodiscard]] inline Vec3 angle_limited_uv(Vec3 vec, Vec3 baseline, float limit_deg) noexcept {
    if (angle_deg(baseline, vec) <= limit_deg) return vec.normalized();
    const Vec3 correction = baseline.normalized().cross(vec.normalized()).normalized();
    return rotate_about_axis(baseline, limit_deg * deg_to_rad, correction).normalized();
}

[[nodiscard]] inline Vec3 project_onto_plane(Vec3 v, Vec3 normal) noexcept {
    const Vec3 b = v.normalized();
    const Vec3 n = normal.normalized();
    Vec3 r = b - n * unit_dot(b, normal);
    const float l = r.length();
    return l > epsilon ? r * (1.0f / l) : b;
}

[[nodiscard]] inline Vec3 rotation_matrix_times(Vec3 direction, Vec3 v) noexcept {
    Vec3 z = direction.normalized();
    Vec3 x;
    if (std::abs(z.y) > 0.9999f) {
        x = {1.0f, 0.0f, 0.0f};
    } else {
        x = z.cross({0.0f, 1.0f, 0.0f}).normalized();
    }
    const Vec3 y = x.cross(z).normalized();
    return x * v.x + y * v.y + z * v.z;
}

struct SVec3;
[[nodiscard]] SVec3 blend(simd::mask4 m, SVec3 a, SVec3 b) noexcept;

struct SVec3 {
    simd::pack4 x{}, y{}, z{};

    static SVec3 load(const float* px, const float* py, const float* pz) noexcept {
        return {simd::pack4::load(px), simd::pack4::load(py), simd::pack4::load(pz)};
    }
    void store(float* px, float* py, float* pz) const noexcept {
        x.store(px); y.store(py); z.store(pz);
    }

    [[nodiscard]] SVec3 operator+(SVec3 o) const noexcept { return {x + o.x, y + o.y, z + o.z}; }
    [[nodiscard]] SVec3 operator-(SVec3 o) const noexcept { return {x - o.x, y - o.y, z - o.z}; }
    [[nodiscard]] SVec3 operator*(simd::pack4 s) const noexcept { return {x * s, y * s, z * s}; }
    [[nodiscard]] SVec3 operator*(float s) const noexcept { return {x * s, y * s, z * s}; }
    [[nodiscard]] SVec3 operator-() const noexcept { return {-x, -y, -z}; }

    [[nodiscard]] simd::pack4 length_sq() const noexcept { return x * x + y * y + z * z; }
    [[nodiscard]] simd::pack4 length() const noexcept { return length_sq().sqrt(); }
    [[nodiscard]] simd::pack4 dot(SVec3 o) const noexcept { return x * o.x + y * o.y + z * o.z; }

    [[nodiscard]] SVec3 cross(SVec3 o) const noexcept {
        return {y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x};
    }

    [[nodiscard]] SVec3 normalized() const noexcept {
        const auto lsq = length_sq();
        const auto degenerate = lsq.less_equal(simd::pack4(epsilon));
        const auto inv = lsq.max(simd::pack4(epsilon)).rsqrt();
        return blend(degenerate,
                     SVec3{simd::pack4(0.0f), simd::pack4(0.0f), simd::pack4(1.0f)},
                     *this * inv);
    }

    [[nodiscard]] simd::pack4 unit_dot(SVec3 o) const noexcept {
        const auto d = normalized().dot(o.normalized());
        return d.min(simd::pack4(1.0f)).max(simd::pack4(-1.0f));
    }
};

[[nodiscard]] inline SVec3 blend(simd::mask4 m, SVec3 a, SVec3 b) noexcept {
    return {simd::blend(m, a.x, b.x), simd::blend(m, a.y, b.y), simd::blend(m, a.z, b.z)};
}

[[nodiscard]] inline simd::pack4 angle_deg(SVec3 a, SVec3 b) noexcept {
    return a.unit_dot(b).acos() * rad_to_deg;
}

[[nodiscard]] inline simd::pack4 signed_angle_deg(SVec3 reference, SVec3 other, SVec3 normal) noexcept {
    const auto unsigned_angle = angle_deg(reference, other);
    const auto sign_value = reference.cross(other).unit_dot(normal);
    return simd::blend(sign_value.less_than(simd::pack4(0.0f)), -unsigned_angle, unsigned_angle);
}

[[nodiscard]] inline SVec3 rotate_about_axis(SVec3 src, simd::pack4 angle_rads, SVec3 axis) noexcept {
    const auto st = angle_rads.sin();
    const auto ct = angle_rads.cos();
    const auto omc = simd::pack4(1.0f) - ct;
    const auto xy = axis.x * axis.y * omc;
    const auto xz = axis.x * axis.z * omc;
    const auto yz = axis.y * axis.z * omc;
    return {
        (axis.x * axis.x * omc + ct) * src.x + (xy - axis.z * st) * src.y + (xz + axis.y * st) * src.z,
        (xy + axis.z * st) * src.x + (axis.y * axis.y * omc + ct) * src.y + (yz - axis.x * st) * src.z,
        (xz - axis.y * st) * src.x + (yz + axis.x * st) * src.y + (axis.z * axis.z * omc + ct) * src.z
    };
}

[[nodiscard]] inline SVec3 angle_limited_uv(SVec3 vec, SVec3 baseline, simd::pack4 limit_deg) noexcept {
    SVec3 r = vec.normalized();
    const auto over = angle_deg(baseline, vec).greater_than(limit_deg);
    if (!over.any()) return r;
    const SVec3 correction = baseline.normalized().cross(vec.normalized()).normalized();
    const SVec3 rotated = rotate_about_axis(baseline, limit_deg * deg_to_rad, correction).normalized();
    return blend(over, rotated, r);
}

[[nodiscard]] inline SVec3 project_onto_plane(SVec3 v, SVec3 normal) noexcept {
    const SVec3 b = v.normalized();
    const SVec3 n = normal.normalized();
    const SVec3 r = b - n * b.unit_dot(normal);
    return r.normalized();
}

[[nodiscard]] inline SVec3 rotation_matrix_times(SVec3 direction, SVec3 v) noexcept {
    const SVec3 z = direction.normalized();
    const auto singular = z.y.abs().greater_than(simd::pack4(0.9999f));
    SVec3 x = z.cross(SVec3{simd::pack4(0.0f), simd::pack4(1.0f), simd::pack4(0.0f)}).normalized();
    x = blend(singular, SVec3{simd::pack4(1.0f), simd::pack4(0.0f), simd::pack4(0.0f)}, x);
    const SVec3 y = x.cross(z).normalized();
    return x * v.x + y * v.y + z * v.z;
}

} // namespace fk
