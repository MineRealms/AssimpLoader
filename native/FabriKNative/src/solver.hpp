#pragma once

#include "math3.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <stdexcept>
#include <vector>

namespace fk {

struct Joint {
    int type = FK_JOINT_BALL;
    float ball_deg = 45.0f;
    Vec3 hinge_rotation{0.0f, 1.0f, 0.0f};
    Vec3 hinge_reference{1.0f, 0.0f, 0.0f};
    float clockwise_deg = max_constraint_deg;
    float anticlockwise_deg = max_constraint_deg;

    [[nodiscard]] bool hinge_is_free() const noexcept {
        return std::abs(-clockwise_deg + max_constraint_deg) < 0.01f &&
               std::abs(anticlockwise_deg - max_constraint_deg) < 0.01f;
    }
};

struct BaseConstraint {
    int type = FK_BASE_NONE;
    Vec3 uv{1.0f, 0.0f, 0.0f};
    Vec3 reference_uv{1.0f, 0.0f, 0.0f};
    Vec3 relative_uv{1.0f, 0.0f, 0.0f};
    Vec3 relative_reference_uv{1.0f, 0.0f, 0.0f};
    float ball_deg = 45.0f;
    float clockwise_deg = max_constraint_deg;
    float anticlockwise_deg = max_constraint_deg;
};

struct SolverParams {
    int max_iterations = 20;
    float min_iteration_change = 0.001f;
    float solve_threshold = 0.01f;
};

class Chain {
public:
    explicit Chain(int bone_count)
        : n_(bone_count),
          length_(bone_count, 1.0f),
          start_(bone_count),
          end_(bone_count),
          joints_(bone_count),
          best_start_(bone_count),
          best_end_(bone_count) {
        if (bone_count <= 0) throw std::invalid_argument("bone_count must be positive");
    }

    [[nodiscard]] int bone_count() const noexcept { return n_; }

    void set_bones(const float* lengths, const float* starts, const float* ends) noexcept {
        for (int i = 0; i < n_; ++i) {
            length_[i] = lengths[i];
            start_[i] = Vec3::load(starts + 3 * i);
            end_[i] = Vec3::load(ends + 3 * i);
        }
        has_last_ = false;
    }

    void get_bones(float* starts, float* ends) const noexcept {
        for (int i = 0; i < n_; ++i) {
            start_[i].store(starts + 3 * i);
            end_[i].store(ends + 3 * i);
        }
    }

    Joint& joint(int i) noexcept { return joints_[i]; }
    [[nodiscard]] const Joint& joint(int i) const noexcept { return joints_[i]; }
    BaseConstraint& base() noexcept { return base_; }
    [[nodiscard]] const BaseConstraint& base() const noexcept { return base_; }
    SolverParams& params() noexcept { return params_; }
    [[nodiscard]] const SolverParams& params() const noexcept { return params_; }
    Vec3& fixed_base_position() noexcept { return fixed_base_position_; }
    [[nodiscard]] Vec3 fixed_base_position() const noexcept { return fixed_base_position_; }
    bool& fixed_base() noexcept { return fixed_base_; }
    [[nodiscard]] bool fixed_base() const noexcept { return fixed_base_; }
    [[nodiscard]] float last_distance() const noexcept { return last_distance_; }

    [[nodiscard]] Vec3 direction(int i) const noexcept {
        return (end_[i] - start_[i]).normalized();
    }

    float solve_for_target(Vec3 target) {
        const Vec3 base = base_position();
        if (has_last_ && last_target_.approximately(target, 0.001f) &&
            last_base_.approximately(base, 0.001f)) {
            return last_distance_;
        }

        float best_distance = std::numeric_limits<float>::infinity();
        float last_pass_distance = std::numeric_limits<float>::infinity();

        for (int iteration = 0; iteration < params_.max_iterations; ++iteration) {
            const float distance = solve_ik(target);
            if (distance < best_distance) {
                best_distance = distance;
                best_start_ = start_;
                best_end_ = end_;
                if (distance <= params_.solve_threshold) break;
            } else if (std::abs(distance - last_pass_distance) < params_.min_iteration_change) {
                break;
            }
            last_pass_distance = distance;
        }

        start_ = best_start_;
        end_ = best_end_;
        last_target_ = target;
        last_base_ = base;
        last_distance_ = best_distance;
        return best_distance;
    }

private:
    [[nodiscard]] Vec3 base_position() const noexcept {
        return fixed_base_ ? fixed_base_position_ : start_[0];
    }

    float solve_ik(Vec3 target) noexcept {
        forward_pass(target);
        backward_pass();
        return (end_[n_ - 1] - target).length();
    }

    void forward_pass(Vec3 target) noexcept {
        for (int i = n_ - 1; i >= 0; --i) {
            Vec3 uv;
            if (i != n_ - 1) {
                const Vec3 outer_uv = -direction(i + 1);
                uv = -direction(i);
                const Joint& j = joints_[i];
                switch (j.type) {
                    case FK_JOINT_BALL:
                        if (angle_deg(outer_uv, uv) > j.ball_deg)
                            uv = angle_limited_uv(uv, outer_uv, j.ball_deg);
                        break;
                    case FK_JOINT_GLOBAL_HINGE:
                        uv = project_onto_plane(uv, j.hinge_rotation);
                        break;
                    case FK_JOINT_LOCAL_HINGE: {
                        const Vec3 axis = i > 0
                            ? rotation_matrix_times(direction(i - 1), j.hinge_rotation).normalized()
                            : base_.relative_uv;
                        uv = project_onto_plane(uv, axis);
                        break;
                    }
                    default: break;
                }
                const Vec3 new_start = end_[i] + uv * length_[i];
                start_[i] = new_start;
                if (i > 0) end_[i - 1] = new_start;
            } else {
                end_[i] = target;
                uv = -direction(i);
                const Joint& j = joints_[i];
                switch (j.type) {
                    case FK_JOINT_BALL: break;
                    case FK_JOINT_GLOBAL_HINGE:
                        uv = project_onto_plane(uv, j.hinge_rotation);
                        break;
                    case FK_JOINT_LOCAL_HINGE: {
                        const Vec3 axis = rotation_matrix_times(direction(i - 1), j.hinge_rotation).normalized();
                        uv = project_onto_plane(uv, axis);
                        break;
                    }
                    default: break;
                }
                const Vec3 new_start = target + uv * length_[i];
                start_[i] = new_start;
                if (i > 0) end_[i - 1] = new_start;
            }
        }
    }

    void backward_pass() noexcept {
        for (int i = 0; i < n_; ++i) {
            if (i != 0) {
                Vec3 uv = direction(i);
                const Vec3 previous = direction(i - 1);
                const Joint& j = joints_[i];
                switch (j.type) {
                    case FK_JOINT_BALL:
                        if (angle_deg(previous, uv) > j.ball_deg)
                            uv = angle_limited_uv(uv, previous, j.ball_deg);
                        break;
                    case FK_JOINT_GLOBAL_HINGE: {
                        uv = project_onto_plane(uv, j.hinge_rotation);
                        if (!j.hinge_is_free()) {
                            const float signed_angle = signed_angle_deg(j.hinge_reference, uv, j.hinge_rotation);
                            if (signed_angle > j.anticlockwise_deg)
                                uv = rotate_about_axis(j.hinge_reference, j.anticlockwise_deg * deg_to_rad, j.hinge_rotation).normalized();
                            else if (signed_angle < -j.clockwise_deg)
                                uv = rotate_about_axis(j.hinge_reference, -j.clockwise_deg * deg_to_rad, j.hinge_rotation).normalized();
                        }
                        break;
                    }
                    case FK_JOINT_LOCAL_HINGE: {
                        const Vec3 axis = rotation_matrix_times(previous, j.hinge_rotation).normalized();
                        uv = project_onto_plane(uv, axis);
                        if (!j.hinge_is_free()) {
                            const Vec3 reference = rotation_matrix_times(previous, j.hinge_reference).normalized();
                            const float signed_angle = signed_angle_deg(reference, uv, axis);
                            if (signed_angle > j.anticlockwise_deg)
                                uv = rotate_about_axis(reference, j.anticlockwise_deg * deg_to_rad, axis).normalized();
                            else if (signed_angle < -j.clockwise_deg)
                                uv = rotate_about_axis(reference, -j.clockwise_deg * deg_to_rad, axis).normalized();
                        }
                        break;
                    }
                    default: break;
                }
                const Vec3 new_end = start_[i] + uv * length_[i];
                end_[i] = new_end;
                if (i < n_ - 1) start_[i + 1] = new_end;
            } else {
                if (fixed_base_) {
                    start_[0] = fixed_base_position_;
                } else {
                    start_[0] = end_[0] - direction(0) * length_[0];
                }
                Vec3 uv;
                switch (base_.type) {
                    case FK_BASE_NONE:
                        uv = direction(0);
                        break;
                    case FK_BASE_GLOBAL_ROTOR:
                        uv = direction(0);
                        if (angle_deg(base_.uv, uv) > base_.ball_deg)
                            uv = angle_limited_uv(uv, base_.uv, base_.ball_deg);
                        break;
                    case FK_BASE_LOCAL_ROTOR:
                        uv = direction(0);
                        if (angle_deg(base_.relative_uv, uv) > base_.ball_deg)
                            uv = angle_limited_uv(uv, base_.relative_uv, base_.ball_deg);
                        break;
                    case FK_BASE_GLOBAL_HINGE: {
                        const Joint& j = joints_[0];
                        uv = project_onto_plane(direction(0), j.hinge_rotation);
                        if (!j.hinge_is_free()) {
                            const float signed_angle = signed_angle_deg(j.hinge_reference, uv, j.hinge_rotation);
                            if (signed_angle > j.anticlockwise_deg)
                                uv = rotate_about_axis(j.hinge_reference, j.anticlockwise_deg * deg_to_rad, j.hinge_rotation).normalized();
                            else if (signed_angle < -j.clockwise_deg)
                                uv = rotate_about_axis(j.hinge_reference, -j.clockwise_deg * deg_to_rad, j.hinge_rotation).normalized();
                        }
                        break;
                    }
                    case FK_BASE_LOCAL_HINGE: {
                        const Joint& j = joints_[0];
                        const Vec3 axis = base_.relative_uv;
                        uv = project_onto_plane(direction(0), axis);
                        if (!j.hinge_is_free()) {
                            const float signed_angle = signed_angle_deg(base_.relative_reference_uv, uv, axis);
                            if (signed_angle > j.anticlockwise_deg)
                                uv = rotate_about_axis(base_.relative_reference_uv, j.anticlockwise_deg * deg_to_rad, axis).normalized();
                            else if (signed_angle < -j.clockwise_deg)
                                uv = rotate_about_axis(base_.relative_reference_uv, -j.clockwise_deg * deg_to_rad, axis).normalized();
                        }
                        break;
                    }
                    default:
                        uv = direction(0);
                        break;
                }
                const Vec3 new_end = start_[0] + uv * length_[0];
                end_[0] = new_end;
                if (n_ > 1) start_[1] = new_end;
            }
        }
    }

    int n_;
    std::vector<float> length_;
    std::vector<Vec3> start_, end_;
    std::vector<Joint> joints_;
    BaseConstraint base_{};
    SolverParams params_{};
    bool fixed_base_ = false;
    Vec3 fixed_base_position_{};
    std::vector<Vec3> best_start_, best_end_;
    Vec3 last_target_{}, last_base_{};
    bool has_last_ = false;
    float last_distance_ = 0.0f;
};

} // namespace fk
