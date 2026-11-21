#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>

#if defined(__AVX__) || defined(__AVX2__) || defined(__AVX512F__) || defined(_M_AVX2) || defined(_M_IX86_FP)
    #include <immintrin.h>
    #define FK_SIMD_AVX2 1
#elif defined(__aarch64__) && defined(__ARM_NEON)
    #include <arm_neon.h>
    #define FK_SIMD_NEON 1
#else
    #define FK_SIMD_SCALAR 1
#endif

namespace fk::simd {

inline constexpr std::size_t lanes = 4;
using storage = std::array<float, lanes>;

struct mask4 {
    std::array<bool, lanes> active{};

    [[nodiscard]] bool any() const noexcept {
        return std::any_of(active.begin(), active.end(), [](bool b) { return b; });
    }
    [[nodiscard]] bool all() const noexcept {
        return std::all_of(active.begin(), active.end(), [](bool b) { return b; });
    }
    [[nodiscard]] class pack4 select(class pack4 yes, class pack4 no) const noexcept;
};

class pack4 {
public:
    using value_type = float;
    static constexpr std::size_t size = lanes;

    constexpr pack4() noexcept = default;
    explicit pack4(float s) noexcept { m_data.fill(s); }
    explicit pack4(storage s) noexcept : m_data(s) {}

    static pack4 load(const float* p) noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        _mm_storeu_ps(r.m_data.data(), _mm_loadu_ps(p));
#else
        std::copy_n(p, lanes, r.m_data.begin());
#endif
        return r;
    }

    void store(float* p) const noexcept { std::copy(m_data.begin(), m_data.end(), p); }
    [[nodiscard]] storage array() const noexcept { return m_data; }
    [[nodiscard]] float lane(std::size_t i) const noexcept { return m_data[i]; }

    float& operator[](std::size_t i) noexcept { return m_data[i]; }
    const float& operator[](std::size_t i) const noexcept { return m_data[i]; }

    [[nodiscard]] pack4 operator+(pack4 o) const noexcept { return binary<Op::add>(o); }
    [[nodiscard]] pack4 operator-(pack4 o) const noexcept { return binary<Op::sub>(o); }
    [[nodiscard]] pack4 operator-() const noexcept { return pack4(0.0f) - *this; }
    [[nodiscard]] pack4 operator*(pack4 o) const noexcept { return binary<Op::mul>(o); }
    [[nodiscard]] pack4 operator/(pack4 o) const noexcept { return binary<Op::div>(o); }
    [[nodiscard]] pack4 operator+(float s) const noexcept { return *this + pack4(s); }
    [[nodiscard]] pack4 operator-(float s) const noexcept { return *this - pack4(s); }
    [[nodiscard]] pack4 operator*(float s) const noexcept { return *this * pack4(s); }
    [[nodiscard]] pack4 operator/(float s) const noexcept { return *this / pack4(s); }

    pack4& operator+=(pack4 o) noexcept { return *this = *this + o; }
    pack4& operator-=(pack4 o) noexcept { return *this = *this - o; }
    pack4& operator*=(pack4 o) noexcept { return *this = *this * o; }

    [[nodiscard]] pack4 sqrt() const noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        _mm_storeu_ps(r.m_data.data(), _mm_sqrt_ps(_mm_loadu_ps(m_data.data())));
#elif defined(FK_SIMD_NEON)
        const auto v = vld1q_f32(m_data.data());
        auto est = vrsqrteq_f32(v);
        est = vmulq_f32(est, vrsqrtsq_f32(v, vmulq_f32(est, est)));
        est = vmulq_f32(est, vrsqrtsq_f32(v, vmulq_f32(est, est)));
        vst1q_f32(r.m_data.data(), vmulq_f32(v, est));
#else
        map_inplace([](float v) { return std::sqrt(v); }, r);
#endif
        return r;
    }

    [[nodiscard]] pack4 rsqrt() const noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        const auto v = _mm_loadu_ps(m_data.data());
        const auto e = _mm_rsqrt_ps(v);
        const auto refined = _mm_mul_ps(e, _mm_sub_ps(_mm_set1_ps(1.5f),
            _mm_mul_ps(_mm_set1_ps(0.5f), _mm_mul_ps(v, _mm_mul_ps(e, e)))));
        _mm_storeu_ps(r.m_data.data(), refined);
#elif defined(FK_SIMD_NEON)
        const auto v = vld1q_f32(m_data.data());
        auto est = vrsqrteq_f32(v);
        est = vmulq_f32(est, vrsqrtsq_f32(v, vmulq_f32(est, est)));
        est = vmulq_f32(est, vrsqrtsq_f32(v, vmulq_f32(est, est)));
        vst1q_f32(r.m_data.data(), est);
#else
        map_inplace([](float v) { return 1.0f / std::sqrt(v); }, r);
#endif
        return r;
    }

    [[nodiscard]] pack4 abs() const noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        _mm_storeu_ps(r.m_data.data(),
            _mm_and_ps(_mm_loadu_ps(m_data.data()),
                          _mm_castsi128_ps(_mm_set1_epi32(0x7fffffff))));
#else
        map_inplace([](float v) { return std::abs(v); }, r);
#endif
        return r;
    }

    [[nodiscard]] pack4 min(pack4 o) const noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        _mm_storeu_ps(r.m_data.data(), _mm_min_ps(_mm_loadu_ps(m_data.data()), _mm_loadu_ps(o.m_data.data())));
#elif defined(FK_SIMD_NEON)
        vst1q_f32(r.m_data.data(), vminq_f32(vld1q_f32(m_data.data()), vld1q_f32(o.m_data.data())));
#else
        for (std::size_t i = 0; i < lanes; ++i) r[i] = std::min((*this)[i], o[i]);
#endif
        return r;
    }

    [[nodiscard]] pack4 max(pack4 o) const noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        _mm_storeu_ps(r.m_data.data(), _mm_max_ps(_mm_loadu_ps(m_data.data()), _mm_loadu_ps(o.m_data.data())));
#elif defined(FK_SIMD_NEON)
        vst1q_f32(r.m_data.data(), vmaxq_f32(vld1q_f32(m_data.data()), vld1q_f32(o.m_data.data())));
#else
        for (std::size_t i = 0; i < lanes; ++i) r[i] = std::max((*this)[i], o[i]);
#endif
        return r;
    }

    [[nodiscard]] pack4 fma(pack4 b, pack4 c) const noexcept {
        pack4 r;
#if defined(__FMA__)
        _mm_storeu_ps(r.m_data.data(), _mm_fmadd_ps(
            _mm_loadu_ps(m_data.data()), _mm_loadu_ps(b.m_data.data()), _mm_loadu_ps(c.m_data.data())));
#else
        r = *this * b + c;
#endif
        return r;
    }

    [[nodiscard]] mask4 less_than(pack4 o) const noexcept { return compare(o, [](float a, float b) { return a < b; }); }
    [[nodiscard]] mask4 greater_than(pack4 o) const noexcept { return compare(o, [](float a, float b) { return a > b; }); }
    [[nodiscard]] mask4 less_equal(pack4 o) const noexcept { return compare(o, [](float a, float b) { return a <= b; }); }

    [[nodiscard]] float horizontal_min() const noexcept {
        return *std::min_element(m_data.begin(), m_data.end());
    }
    [[nodiscard]] float horizontal_max() const noexcept {
        return *std::max_element(m_data.begin(), m_data.end());
    }
    [[nodiscard]] float horizontal_sum() const noexcept {
        float r = 0.0f;
        for (float v : m_data) r += v;
        return r;
    }

    [[nodiscard]] pack4 acos() const noexcept { return unary([](float v) { return std::acos(v); }); }
    [[nodiscard]] pack4 sin() const noexcept { return unary([](float v) { return std::sin(v); }); }
    [[nodiscard]] pack4 cos() const noexcept { return unary([](float v) { return std::cos(v); }); }

    [[nodiscard]] pack4 atan2(pack4 o) const noexcept {
        pack4 r;
        for (std::size_t i = 0; i < lanes; ++i) r[i] = std::atan2(m_data[i], o[i]);
        return r;
    }

    [[nodiscard]] pack4 copy_sign(float sign_source) const noexcept {
        pack4 r;
        for (std::size_t i = 0; i < lanes; ++i) r[i] = std::copysign(m_data[i], sign_source);
        return r;
    }

private:
    enum class Op { add, sub, mul, div };

    storage m_data{};

    template <Op op>
    [[nodiscard]] pack4 binary(pack4 o) const noexcept {
        pack4 r;
#if defined(FK_SIMD_AVX2)
        const auto a = _mm_loadu_ps(m_data.data());
        const auto b = _mm_loadu_ps(o.m_data.data());
        __m128 c{};
        if constexpr (op == Op::add) c = _mm_add_ps(a, b);
        else if constexpr (op == Op::sub) c = _mm_sub_ps(a, b);
        else if constexpr (op == Op::mul) c = _mm_mul_ps(a, b);
        else c = _mm_div_ps(a, b);
        _mm_storeu_ps(r.m_data.data(), c);
#else
        for (std::size_t i = 0; i < lanes; ++i) {
            if constexpr (op == Op::add) r[i] = m_data[i] + o[i];
            else if constexpr (op == Op::sub) r[i] = m_data[i] - o[i];
            else if constexpr (op == Op::mul) r[i] = m_data[i] * o[i];
            else r[i] = m_data[i] / o[i];
        }
#endif
        return r;
    }

    template <class F>
    [[nodiscard]] pack4 unary(F&& fn) const noexcept {
        pack4 r;
        for (std::size_t i = 0; i < lanes; ++i) r[i] = fn(m_data[i]);
        return r;
    }

    template <class F>
    void map_inplace(F&& fn, pack4& r) const noexcept {
        for (std::size_t i = 0; i < lanes; ++i) r[i] = fn(m_data[i]);
    }

    template <class F>
    [[nodiscard]] mask4 compare(pack4 o, F&& fn) const noexcept {
        mask4 m;
        for (std::size_t i = 0; i < lanes; ++i) m.active[i] = fn(m_data[i], o[i]);
        return m;
    }
};

inline pack4 mask4::select(pack4 yes, pack4 no) const noexcept {
    pack4 r;
    for (std::size_t i = 0; i < lanes; ++i) r[i] = active[i] ? yes[i] : no[i];
    return r;
}

[[nodiscard]] inline pack4 blend(mask4 m, pack4 a, pack4 b) noexcept { return m.select(a, b); }
[[nodiscard]] inline pack4 operator*(float s, pack4 p) noexcept { return p * s; }

[[nodiscard]] inline const char* backend_name() noexcept {
#if defined(FK_SIMD_AVX2)
    return "SSE/AVX 128-bit (4 lanes)";
#elif defined(FK_SIMD_NEON)
    return "NEON (4 lanes)";
#else
    return "scalar";
#endif
}

} // namespace fk::simd

