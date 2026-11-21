#pragma once

#include "utils.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <vector>

namespace fk {

using utils::Vec2;

enum class BaseConstraintType2D {
    none,
    global_absolute,
    local_relative,
    local_absolute
};

struct Joint2D {
    enum class CoordinateSystem { local, global } coordinate_system = CoordinateSystem::global;
    float clockwise_deg = max_constraint_deg;
    float anticlockwise_deg = max_constraint_deg;
    Vec2 hinge_rotation{1.0f, 0.0f};

    [[nodiscard]] bool constrained() const noexcept {
        return std::abs(-clockwise_deg + max_constraint_deg) >= 0.01f ||
               std::abs(anticlockwise_deg - max_constraint_deg) >= 0.01f;
    }
};

class Chain2D {
public:
    explicit Chain2D(int bone_count) : n_(bone_count), length_(bone_count, 1.0f),
        start_(bone_count), end_(bone_count), joints_(bone_count),
        best_start_(bone_count), best_end_(bone_count) {
        if (bone_count <= 0) throw std::invalid_argument("bone_count must be positive");
    }

    [[nodiscard]] int bone_count() const noexcept { return n_; }
    Joint2D& joint(int i) noexcept { return joints_[i]; }
    BaseConstraintType2D& base_constraint_type() noexcept { return base_type_; }
    Vec2& base_constraint_uv() noexcept { return base_uv_; }
    SolverParams& params() noexcept { return params_; }
    bool& fixed_base() noexcept { return fixed_base_; }
    Vec2& fixed_base_position() noexcept { return fixed_base_position_; }

    void set_bones(const float* lengths, const float* starts, const float* ends) noexcept {
        for (int i = 0; i < n_; ++i) {
            length_[i] = lengths[i];
            start_[i] = {starts[2 * i], starts[2 * i + 1]};
            end_[i] = {ends[2 * i], ends[2 * i + 1]};
        }
        has_last_ = false;
    }

    void get_bones(float* starts, float* ends) const noexcept {
        for (int i = 0; i < n_; ++i) {
            starts[2 * i] = start_[i].x; starts[2 * i + 1] = start_[i].y;
            ends[2 * i] = end_[i].x; ends[2 * i + 1] = end_[i].y;
        }
    }

    [[nodiscard]] Vec2 direction(int i) const noexcept {
        return (end_[i] - start_[i]).normalized();
    }

    float solve_for_target(Vec2 target) {
        const Vec2 base = fixed_base_ ? fixed_base_position_ : start_[0];
        if (has_last_ && last_target_.approximately(target) && last_base_.approximately(base))
            return last_distance_;

        float best = std::numeric_limits<float>::infinity();
        float last_pass = std::numeric_limits<float>::infinity();
        for (int iteration = 0; iteration < params_.max_iterations; ++iteration) {
            const float distance = solve_ik(target);
            if (distance < best) {
                best = distance;
                best_start_ = start_;
                best_end_ = end_;
                if (distance <= params_.solve_threshold) break;
            } else if (std::abs(distance - last_pass) < params_.min_iteration_change) {
                break;
            }
            last_pass = distance;
        }
        start_ = best_start_;
        end_ = best_end_;
        last_target_ = target;
        last_base_ = base;
        last_distance_ = best;
        return best;
    }

private:
    float solve_ik(Vec2 target) noexcept {
        forward_pass(target);
        backward_pass();
        return (end_[n_ - 1] - target).length();
    }

    Vec2 constrained_direction(int i, Vec2 baseline, Vec2 reference) const noexcept {
        const Joint2D& j = joints_[i];
        const bool local = j.coordinate_system == Joint2D::CoordinateSystem::local;
        return direction(i).constrained(baseline, -j.clockwise_deg, j.anticlockwise_deg, local, reference);
    }

    void forward_pass(Vec2 target) noexcept {
        for (int i = n_ - 1; i >= 0; --i) {
            Vec2 uv;
            if (i != n_ - 1) {
                const Vec2 outer_uv = -direction(i + 1);
                uv = constrained_direction(i, outer_uv, outer_uv);
                const Vec2 new_start = end_[i] + uv * length_[i];
                start_[i] = new_start;
                if (i > 0) end_[i - 1] = new_start;
            } else {
                end_[i] = target;
                uv = (target - start_[i]).normalized();
                const Joint2D& j = joints_[i];
                if (j.constrained()) {
                    const bool local = j.coordinate_system == Joint2D::CoordinateSystem::local;
                    const Vec2 baseline = i > 0 ? direction(i - 1) : base_uv_;
                    uv = uv.constrained(baseline, -j.clockwise_deg, j.anticlockwise_deg, local, j.hinge_rotation);
                }
                const Vec2 new_start = target - uv * length_[i];
                start_[i] = new_start;
                if (i > 0) end_[i - 1] = new_start;
            }
        }
    }

    void backward_pass() noexcept {
        for (int i = 0; i < n_; ++i) {
            if (i != 0) {
                Vec2 uv = constrained_direction(i, direction(i - 1), direction(i - 1));
                const Vec2 new_end = start_[i] + uv * length_[i];
                end_[i] = new_end;
                if (i < n_ - 1) start_[i + 1] = new_end;
            } else {
                if (fixed_base_) {
                    start_[0] = fixed_base_position_;
                } else {
                    start_[0] = end_[0] - direction(0) * length_[0];
                }
                Vec2 uv = direction(0);
                Vec2 constraint_uv = base_uv_;
                switch (base_type_) {
                    case BaseConstraintType2D::none: break;
                    case BaseConstraintType2D::global_absolute:
                        uv = uv.constrained(base_uv_, -max_constraint_deg, max_constraint_deg, false, base_uv_);
                        break;
                    case BaseConstraintType2D::local_relative:
                        uv = uv.constrained(base_uv_, -max_constraint_deg, max_constraint_deg, true, base_uv_);
                        break;
                    case BaseConstraintType2D::local_absolute:
                        constraint_uv = base_uv_.rotated_rads(direction(0).angle_rads());
                        uv = uv.constrained(constraint_uv, -max_constraint_deg, max_constraint_deg, true, constraint_uv);
                        break;
                }
                const Vec2 new_end = start_[0] + uv * length_[0];
                end_[0] = new_end;
                if (n_ > 1) start_[1] = new_end;
            }
        }
    }

    int n_;
    std::vector<float> length_;
    std::vector<Vec2> start_, end_;
    std::vector<Joint2D> joints_;
    BaseConstraintType2D base_type_ = BaseConstraintType2D::none;
    Vec2 base_uv_{1.0f, 0.0f};
    SolverParams params_{};
    bool fixed_base_ = false;
    Vec2 fixed_base_position_{};
    std::vector<Vec2> best_start_, best_end_;
    Vec2 last_target_{}, last_base_{};
    bool has_last_ = false;
    float last_distance_ = 0.0f;
};

class Structure2D {
public:
    void add_chain(Chain2D& chain) noexcept { chains_.push_back(&chain); }

    float solve_for_target(Vec2 target) {
        float worst = 0.0f;
        for (Chain2D* chain : chains_) {
            worst = std::max(worst, chain->solve_for_target(target));
        }
        return worst;
    }

private:
    std::vector<Chain2D*> chains_;
};

} // namespace fk
