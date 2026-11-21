#include "fabrik.h"

#include "batch.hpp"
#include "solver.hpp"
#include "solver2d.hpp"
#include "utils.hpp"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

namespace {

int failures = 0;

void check(bool condition, const char* expression, int line) {
    if (condition) return;
    std::fprintf(stderr, "FAIL line %d: %s\n", line, expression);
    ++failures;
}

#define CHECK(expr) check((expr), #expr, __LINE__)

bool close(float a, float b, float tol = 0.01f) { return std::abs(a - b) <= tol; }

void test_metadata() {
    CHECK(std::string(fk_version()).find("modern-cpp20") != std::string::npos);
    std::printf("backend: %s\n", fk_simd_backend());
}

void test_scalar_chain() {
    fk::Chain chain(3);
    chain.fixed_base() = true;
    chain.fixed_base_position() = {0.0f, 0.0f, 0.0f};
    const float lengths[3] = {1.0f, 1.0f, 1.0f};
    const float starts[9] = {0, 0, 0, 1, 0, 0, 2, 0, 0};
    const float ends[9] = {1, 0, 0, 2, 0, 0, 3, 0, 0};
    chain.set_bones(lengths, starts, ends);

    const float distance = chain.solve_for_target({2.5f, 0.5f, 0.0f});
    CHECK(close(distance, 0.0f, 0.05f));
    float out_starts[9], out_ends[9];
    chain.get_bones(out_starts, out_ends);
    CHECK(close(out_ends[6], 2.5f, 0.05f));
    CHECK(close(out_ends[7], 0.5f, 0.05f));
}

void test_unreachable_target() {
    fk::Chain chain(3);
    chain.fixed_base() = true;
    chain.fixed_base_position() = {0.0f, 0.0f, 0.0f};
    const float lengths[3] = {1.0f, 1.0f, 1.0f};
    const float starts[9] = {0, 0, 0, 1, 0, 0, 2, 0, 0};
    const float ends[9] = {1, 0, 0, 2, 0, 0, 3, 0, 0};
    chain.set_bones(lengths, starts, ends);
    const float distance = chain.solve_for_target({10.0f, 0.0f, 0.0f});
    CHECK(close(distance, 7.0f, 0.05f));
}

void test_ball_joint_constraint() {
    fk::Chain chain(3);
    chain.fixed_base() = true;
    chain.fixed_base_position() = {0.0f, 0.0f, 0.0f};
    for (int i = 0; i < 3; ++i) {
        chain.joint(i).type = FK_JOINT_BALL;
        chain.joint(i).ball_deg = 15.0f;
    }
    const float lengths[3] = {1.0f, 1.0f, 1.0f};
    const float starts[9] = {0, 0, 0, 1, 0, 0, 2, 0, 0};
    const float ends[9] = {1, 0, 0, 2, 0, 0, 3, 0, 0};
    chain.set_bones(lengths, starts, ends);
    chain.solve_for_target({0.0f, 3.0f, 0.0f});
    const fk::Vec3 d0 = chain.direction(0);
    const fk::Vec3 d1 = chain.direction(1);
    const float angle = fk::angle_deg(d0, d1);
    CHECK(angle <= 15.5f);
}

void test_batch_matches_scalar() {
    constexpr int chains = 6;
    constexpr int bones = 3;
    const float lengths[bones] = {1.0f, 1.0f, 1.0f};
    const float starts[9] = {0, 0, 0, 1, 0, 0, 2, 0, 0};
    const float ends[9] = {1, 0, 0, 2, 0, 0, 3, 0, 0};
    const float targets[chains * 3] = {
        2.5f, 0.5f, 0.0f,
        0.0f, 2.0f, 0.0f,
        0.0f, 0.0f, 2.0f,
        1.5f, 1.5f, 0.0f,
        -1.0f, 0.0f, 0.0f,
        10.0f, 0.0f, 0.0f
    };

    fk::Chain config(bones);
    config.fixed_base() = true;
    config.fixed_base_position() = {0.0f, 0.0f, 0.0f};
    fk::BatchSolver batch(chains, bones, config);
    for (int c = 0; c < chains; ++c) batch.set_chain(c, lengths, starts, ends);

    std::vector<float> batch_distances(chains);
    batch.solve(targets, batch_distances.data());

    for (int c = 0; c < chains; ++c) {
        fk::Chain scalar(bones);
        scalar.fixed_base() = true;
        scalar.fixed_base_position() = {0.0f, 0.0f, 0.0f};
        scalar.set_bones(lengths, starts, ends);
        const float scalar_distance = scalar.solve_for_target({targets[3 * c], targets[3 * c + 1], targets[3 * c + 2]});
        CHECK(close(batch_distances[c], scalar_distance, 0.05f));
    }
}

void test_utils() {
    using namespace fk::utils;
    const Mat4 a = Mat4::rotation_about_axis_rads({0, 1, 0}, pi / 2.0f);
    const Mat4 identity = a.times(a.inverse());
    for (float v : identity.m) CHECK(close(v, (std::abs(v) < 0.001f) ? 0.0f : 1.0f, 0.001f) || close(v, 1.0f, 0.001f));

    const Vec2 v{0.0f, 1.0f};
    const Vec2 rotated = v.rotated_degs(90.0f);
    CHECK(close(rotated.x, -1.0f, 0.001f));
    CHECK(close(rotated.y, 0.0f, 0.001f));

    const Mat3 basis = Mat3::from_rotation({0, 0, 1});
    const fk::Vec3 transformed = basis.times({1, 0, 0});
    CHECK(close(transformed.length(), 1.0f, 0.001f));

    BinaryWriter writer;
    writer.write_vec3({1.0f, 2.0f, 3.0f});
    writer.write_colour({0.1f, 0.2f, 0.3f, 0.4f});
    BinaryReader reader(writer.bytes());
    const fk::Vec3 v3 = reader.read_vec3();
    const Colour4f colour = reader.read_colour();
    CHECK(close(v3.x, 1.0f));
    CHECK(close(v3.z, 3.0f));
    CHECK(close(colour.a, 0.4f));
    CHECK(reader.exhausted());
}

void test_2d_chain() {
    fk::Chain2D chain(2);
    chain.fixed_base() = true;
    chain.fixed_base_position() = {0.0f, 0.0f};
    const float lengths[2] = {1.0f, 1.0f};
    const float starts[4] = {0, 0, 1, 0};
    const float ends[4] = {1, 0, 2, 0};
    chain.set_bones(lengths, starts, ends);
    const float distance = chain.solve_for_target({1.0f, 1.0f});
    CHECK(distance >= 0.0f);
    CHECK(distance < 0.5f);
}

} // namespace

int main() {
    test_metadata();
    test_scalar_chain();
    test_unreachable_target();
    test_ball_joint_constraint();
    test_batch_matches_scalar();
    test_utils();
    test_2d_chain();

    if (failures != 0) {
        std::fprintf(stderr, "%d check(s) failed\n", failures);
        return EXIT_FAILURE;
    }
    std::puts("ALL NATIVE TESTS PASSED");
    return EXIT_SUCCESS;
}
