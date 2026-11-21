#pragma once

#include "math3.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <random>
#include <span>
#include <stdexcept>
#include <type_traits>
#include <vector>

namespace fk::utils {

inline constexpr float pi = 3.14159265358979323846f;
inline constexpr float two_pi = 6.28318530717958647692f;
inline constexpr float deg_to_rads = pi / 180.0f;
inline constexpr float rads_to_deg = 180.0f / pi;

[[nodiscard]] inline bool approximately_equal(float a, float b, float tolerance = 0.001f) noexcept {
    return std::abs(a - b) < tolerance;
}

[[nodiscard]] constexpr float sign(float value) noexcept {
    return value < 0.0f ? -1.0f : 1.0f;
}

[[nodiscard]] constexpr float clamp(float value, float low, float high) noexcept {
    return std::min(high, std::max(low, value));
}

[[nodiscard]] inline float random_float(float low, float high) {
    static thread_local std::mt19937 rng{std::random_device{}()};
    return std::uniform_real_distribution<float>(low, high)(rng);
}

[[nodiscard]] inline std::vector<float> create_float_buffer(std::span<const float> values) {
    return {values.begin(), values.end()};
}

struct Vec2 {
    float x{}, y{};

    constexpr Vec2() noexcept = default;
    constexpr Vec2(float xx, float yy) noexcept : x(xx), y(yy) {}

    [[nodiscard]] constexpr Vec2 operator+(Vec2 o) const noexcept { return {x + o.x, y + o.y}; }
    [[nodiscard]] constexpr Vec2 operator-(Vec2 o) const noexcept { return {x - o.x, y - o.y}; }
    [[nodiscard]] constexpr Vec2 operator*(float s) const noexcept { return {x * s, y * s}; }
    [[nodiscard]] constexpr Vec2 operator/(float s) const noexcept { return {x / s, y / s}; }
    [[nodiscard]] constexpr Vec2 operator-() const noexcept { return {-x, -y}; }

    Vec2& operator+=(Vec2 o) noexcept { x += o.x; y += o.y; return *this; }
    Vec2& operator-=(Vec2 o) noexcept { x -= o.x; y -= o.y; return *this; }
    Vec2& operator*=(float s) noexcept { x *= s; y *= s; return *this; }

    [[nodiscard]] constexpr float length_sq() const noexcept { return x * x + y * y; }
    [[nodiscard]] float length() const noexcept { return std::sqrt(length_sq()); }

    [[nodiscard]] Vec2 normalized() const noexcept {
        const float l = length();
        return l > epsilon ? Vec2{x / l, y / l} : Vec2{1.0f, 0.0f};
    }
    Vec2& normalize() noexcept { return *this = normalized(); }

    [[nodiscard]] constexpr float dot(Vec2 o) const noexcept { return x * o.x + y * o.y; }
    [[nodiscard]] float angle_rads() const noexcept { return std::atan2(y, x); }
    [[nodiscard]] float angle_degs() const noexcept { return angle_rads() * rads_to_deg; }

    [[nodiscard]] Vec2 rotated_rads(float angle) const noexcept {
        const float c = std::cos(angle), s = std::sin(angle);
        return {x * c - y * s, x * s + y * c};
    }
    [[nodiscard]] Vec2 rotated_degs(float angle) const noexcept { return rotated_rads(angle * deg_to_rads); }

    [[nodiscard]] Vec2 perpendicular() const noexcept { return {-y, x}; }

    [[nodiscard]] Vec2 constrained(Vec2 constraint_uv, float min_angle_deg, float max_angle_deg,
                                   bool constraint_is_local, Vec2 reference_uv) const noexcept {
        Vec2 target_uv = constraint_is_local
            ? Vec2{reference_uv.x * constraint_uv.x - reference_uv.y * constraint_uv.y,
                   reference_uv.x * constraint_uv.y + reference_uv.y * constraint_uv.x}
            : constraint_uv;

        const float base_angle = target_uv.angle_degs();
        const float constrained_angle = angle_degs();
        const float relative = constrained_angle - base_angle;
        const float clamped = clamp(relative, min_angle_deg, max_angle_deg);
        return target_uv.rotated_degs(clamped);
    }

    [[nodiscard]] bool approximately(Vec2 o, float tol = 0.001f) const noexcept {
        return std::abs(x - o.x) < tol && std::abs(y - o.y) < tol;
    }
};

struct Vec3i {
    int x{}, y{}, z{};

    constexpr Vec3i() noexcept = default;
    constexpr Vec3i(int xx, int yy, int zz) noexcept : x(xx), y(yy), z(zz) {}

    [[nodiscard]] constexpr Vec3i operator+(Vec3i o) const noexcept { return {x + o.x, y + o.y, z + o.z}; }
    [[nodiscard]] constexpr Vec3i operator-(Vec3i o) const noexcept { return {x - o.x, y - o.y, z - o.z}; }
    [[nodiscard]] constexpr bool operator==(Vec3i o) const noexcept { return x == o.x && y == o.y && z == o.z; }
};

struct Colour4f {
    float r{}, g{}, b{}, a{1.0f};

    constexpr Colour4f() noexcept = default;
    constexpr Colour4f(float rr, float gg, float bb, float aa = 1.0f) noexcept
        : r(clamp(rr, 0.0f, 1.0f)), g(clamp(gg, 0.0f, 1.0f)),
          b(clamp(bb, 0.0f, 1.0f)), a(clamp(aa, 0.0f, 1.0f)) {}

    explicit Colour4f(std::span<const float> values) noexcept {
        if (values.size() >= 1) r = clamp(values[0], 0.0f, 1.0f);
        if (values.size() >= 2) g = clamp(values[1], 0.0f, 1.0f);
        if (values.size() >= 3) b = clamp(values[2], 0.0f, 1.0f);
        if (values.size() >= 4) a = clamp(values[3], 0.0f, 1.0f);
    }

    [[nodiscard]] std::array<float, 4> to_array() const noexcept { return {r, g, b, a}; }
    [[nodiscard]] constexpr Colour4f operator+(Colour4f o) const noexcept {
        return {r + o.r, g + o.g, b + o.b, a + o.a};
    }
};

class Mat3 {
public:
    std::array<float, 9> m{};

    constexpr Mat3() noexcept {
        m[0] = m[4] = m[8] = 1.0f;
    }

    constexpr Mat3(Vec3 x_basis, Vec3 y_basis, Vec3 z_basis) noexcept {
        set_x_basis(x_basis);
        set_y_basis(y_basis);
        set_z_basis(z_basis);
    }

    [[nodiscard]] constexpr Vec3 x_basis() const noexcept { return {m[0], m[1], m[2]}; }
    [[nodiscard]] constexpr Vec3 y_basis() const noexcept { return {m[3], m[4], m[5]}; }
    [[nodiscard]] constexpr Vec3 z_basis() const noexcept { return {m[6], m[7], m[8]}; }

    constexpr void set_x_basis(Vec3 v) noexcept { m[0] = v.x; m[1] = v.y; m[2] = v.z; }
    constexpr void set_y_basis(Vec3 v) noexcept { m[3] = v.x; m[4] = v.y; m[5] = v.z; }
    constexpr void set_z_basis(Vec3 v) noexcept { m[6] = v.x; m[7] = v.y; m[8] = v.z; }

    [[nodiscard]] constexpr Vec3 times(Vec3 v) const noexcept {
        return x_basis() * v.x + y_basis() * v.y + z_basis() * v.z;
    }

    [[nodiscard]] constexpr Mat3 times(const Mat3& o) const noexcept {
        Mat3 r;
        for (int c = 0; c < 3; ++c) {
            const Vec3 col{o.m[3 * c], o.m[3 * c + 1], o.m[3 * c + 2]};
            const Vec3 result = times(col);
            r.m[3 * c] = result.x; r.m[3 * c + 1] = result.y; r.m[3 * c + 2] = result.z;
        }
        return r;
    }

    [[nodiscard]] Mat3 transposed() const noexcept {
        Mat3 r;
        for (int row = 0; row < 3; ++row)
            for (int col = 0; col < 3; ++col)
                r.m[3 * row + col] = m[3 * col + row];
        return r;
    }

    void transpose() noexcept { *this = transposed(); }

    [[nodiscard]] float determinant() const noexcept {
        return m[6] * m[1] * m[5] - m[6] * m[2] * m[4]
             - m[3] * m[1] * m[8] + m[3] * m[2] * m[7]
             + m[0] * m[4] * m[8] - m[0] * m[5] * m[7];
    }

    [[nodiscard]] Mat3 inverse() const noexcept {
        Mat3 r;
        const float d = determinant();
        if (std::abs(d) < epsilon) return r;
        const float inv = 1.0f / d;
        const float a = m[0], b = m[1], c = m[2];
        const float e = m[3], f = m[4], g = m[5];
        const float h = m[6], i = m[7], j = m[8];
        r.m[0] = (f * j - g * i) * inv;
        r.m[1] = (c * i - b * j) * inv;
        r.m[2] = (b * g - c * f) * inv;
        r.m[3] = (g * h - e * j) * inv;
        r.m[4] = (a * j - c * h) * inv;
        r.m[5] = (c * e - a * g) * inv;
        r.m[6] = (e * i - f * h) * inv;
        r.m[7] = (b * h - a * i) * inv;
        r.m[8] = (a * f - b * e) * inv;
        return r;
    }

    [[nodiscard]] static Mat3 from_rotation(Vec3 reference_direction) noexcept {
        const Vec3 z = reference_direction.normalized();
        if (std::abs(z.y) > 0.9999f) {
            const Vec3 x{1.0f, 0.0f, 0.0f};
            return Mat3{x, x.cross(z).normalized(), z};
        }
        const Vec3 x = z.cross(Vec3{0.0f, 1.0f, 0.0f}).normalized();
        return Mat3{x, x.cross(z).normalized(), z};
    }
};

class Mat4 {
public:
    std::array<float, 16> m{};

    constexpr Mat4() noexcept {
        m[0] = m[5] = m[10] = m[15] = 1.0f;
    }

    explicit Mat4(std::span<const float> values) noexcept {
        if (values.size() >= 16) std::copy_n(values.begin(), 16, m.begin());
    }

    [[nodiscard]] constexpr Mat4 times(const Mat4& o) const noexcept {
        Mat4 r;
        for (int col = 0; col < 4; ++col) {
            for (int row = 0; row < 4; ++row) {
                float sum = 0.0f;
                for (int k = 0; k < 4; ++k) sum += m[4 * k + row] * o.m[4 * col + k];
                r.m[4 * col + row] = sum;
            }
        }
        return r;
    }

    [[nodiscard]] Vec3 transform_point(Vec3 v) const noexcept {
        return {
            m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12],
            m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13],
            m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        };
    }

    [[nodiscard]] Vec3 transform_direction(Vec3 v) const noexcept {
        return {
            m[0] * v.x + m[4] * v.y + m[8] * v.z,
            m[1] * v.x + m[5] * v.y + m[9] * v.z,
            m[2] * v.x + m[6] * v.y + m[10] * v.z
        };
    }

    [[nodiscard]] Mat4 transposed() const noexcept {
        Mat4 r;
        for (int row = 0; row < 4; ++row)
            for (int col = 0; col < 4; ++col)
                r.m[4 * row + col] = m[4 * col + row];
        return r;
    }

    void transpose() noexcept { *this = transposed(); }

    [[nodiscard]] float determinant() const noexcept {
        const float a = m[0], b = m[1], c = m[2], d = m[3];
        const float e = m[4], f = m[5], g = m[6], h = m[7];
        const float i = m[8], j = m[9], k = m[10], l = m[11];
        const float p = m[12], q = m[13], r = m[14], s = m[15];
        const float A = f * k * s - f * l * r - j * g * s + j * h * r + p * g * l - p * h * k;
        const float B = e * k * s - e * l * r - i * g * s + i * h * r + p * g * k - p * h * l;
        const float C = e * j * s - e * l * q - i * f * s + i * h * q + p * f * l - p * h * j;
        const float D = e * j * r - e * k * q - i * f * r + i * g * q + p * f * k - p * g * j;
        return a * A - b * B + c * C - d * D;
    }

    [[nodiscard]] Mat4 inverse() const noexcept {
        Mat4 r;
        const float d = determinant();
        if (std::abs(d) < epsilon) return r;
        const float inv = 1.0f / d;
        const float a = m[0], b = m[1], c = m[2], dd = m[3];
        const float e = m[4], f = m[5], g = m[6], h = m[7];
        const float i = m[8], j = m[9], k = m[10], l = m[11];
        const float p = m[12], q = m[13], rr = m[14], s = m[15];

        r.m[0] = (f * k * s - f * l * rr - j * g * s + j * h * rr + p * g * l - p * h * k) * inv;
        r.m[1] = (b * l * rr - b * k * s - j * d * rr + j * dd * s + p * d * k - p * dd * l) * inv;
        r.m[2] = (b * g * s - b * h * rr - f * d * s + f * dd * rr + p * d * h - p * dd * g) * inv;
        r.m[3] = (b * h * k - b * g * l - f * dd * l + f * d * l + g * dd * k - g * d * k) * inv;

        r.m[4] = (e * l * rr - e * k * s - i * h * rr + i * g * s + p * h * k - p * g * l) * inv;
        r.m[5] = (a * k * s - a * l * rr - i * dd * s + i * d * rr + p * dd * l - p * d * k) * inv;
        r.m[6] = (a * h * rr - a * g * s - e * dd * rr + e * d * s + p * dd * g - p * d * h) * inv;
        r.m[7] = (a * g * l - a * h * k - e * d * l + e * dd * l - g * dd * k + g * d * k) * inv;

        r.m[8] = (e * j * s - e * l * q - i * f * s + i * h * q + p * f * l - p * h * j) * inv;
        r.m[9] = (a * l * q - a * j * s - i * b * q + i * dd * s + p * b * j - p * dd * l) * inv;
        r.m[10] = (a * f * s - a * h * q - e * b * s + e * dd * q + p * b * h - p * dd * f) * inv;
        r.m[11] = (a * h * j - a * f * l - e * dd * l + e * b * l - f * dd * j + f * d * l) * inv;

        r.m[12] = (e * k * q - e * j * rr - i * g * q + i * f * rr + p * j * g - p * f * k) * inv;
        r.m[13] = (a * j * rr - a * k * q - i * b * rr + i * d * q + p * b * k - p * d * j) * inv;
        r.m[14] = (a * g * q - a * f * rr - e * d * q + e * b * rr + p * f * d - p * b * g) * inv;
        r.m[15] = (a * f * j - a * e * k - e * b * j + e * d * k + i * b * g - i * d * f) * inv;
        return r;
    }

    void translate(Vec3 v) noexcept {
        m[12] += m[0] * v.x + m[4] * v.y + m[8] * v.z;
        m[13] += m[1] * v.x + m[5] * v.y + m[9] * v.z;
        m[14] += m[2] * v.x + m[6] * v.y + m[10] * v.z;
    }

    [[nodiscard]] Mat3 to_mat3() const noexcept {
        return Mat3{{m[0], m[1], m[2]}, {m[4], m[5], m[6]}, {m[8], m[9], m[10]}};
    }

    [[nodiscard]] static Mat4 rotation_about_axis_rads(Vec3 axis, float angle) noexcept {
        const Mat3 r3 = Mat3::from_rotation(axis);
        Mat4 r;
        r.m[0] = r3.m[0]; r.m[1] = r3.m[1]; r.m[2] = r3.m[2];
        r.m[4] = r3.m[3]; r.m[5] = r3.m[4]; r.m[6] = r3.m[5];
        r.m[8] = r3.m[6]; r.m[9] = r3.m[7]; r.m[10] = r3.m[8];
        return r;
    }

    [[nodiscard]] static Mat4 rotate_matrix_about_local_axis_rads(const Mat4& matrix, float angle, Vec3 axis) noexcept {
        return matrix.times(rotation_about_axis_rads(axis, angle));
    }

    [[nodiscard]] static Mat4 rotate_matrix_about_local_axis_degs(const Mat4& matrix, float angle, Vec3 axis) noexcept {
        return rotate_matrix_about_local_axis_rads(matrix, angle * deg_to_rads, axis);
    }
};

class BinaryWriter {
public:
    void write_float(float v) { write_bytes(&v, sizeof(v)); }
    void write_int(int32_t v) { write_bytes(&v, sizeof(v)); }
    void write_vec3(Vec3 v) { write_float(v.x); write_float(v.y); write_float(v.z); }
    void write_vec2(Vec2 v) { write_float(v.x); write_float(v.y); }
    void write_colour(Colour4f c) { write_float(c.r); write_float(c.g); write_float(c.b); write_float(c.a); }

    [[nodiscard]] std::span<const std::byte> bytes() const noexcept { return data_; }

private:
    void write_bytes(const void* p, std::size_t count) {
        const auto* b = static_cast<const std::byte*>(p);
        data_.insert(data_.end(), b, b + count);
    }
    std::vector<std::byte> data_;
};

class BinaryReader {
public:
    explicit BinaryReader(std::span<const std::byte> data) : data_(data) {}

    float read_float() { return read<float>(); }
    int32_t read_int() { return read<int32_t>(); }
    Vec3 read_vec3() { return {read_float(), read_float(), read_float()}; }
    Vec2 read_vec2() { return {read_float(), read_float()}; }
    Colour4f read_colour() { return {read_float(), read_float(), read_float(), read_float()}; }

    [[nodiscard]] bool exhausted() const noexcept { return offset_ >= data_.size(); }

private:
    template <class T>
    T read() {
        if (offset_ + sizeof(T) > data_.size()) throw std::out_of_range("BinaryReader: unexpected end of stream");
        T value{};
        std::memcpy(&value, data_.data() + offset_, sizeof(T));
        offset_ += sizeof(T);
        return value;
    }

    std::span<const std::byte> data_;
    std::size_t offset_ = 0;
};

} // namespace fk::utils
