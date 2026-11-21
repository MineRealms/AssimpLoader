#pragma once

#include "solver.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <limits>
#include <stdexcept>
#include <vector>

namespace fk {

[[nodiscard]] inline SVec3 broadcast(Vec3 v) noexcept {
    return {simd::pack4(v.x), simd::pack4(v.y), simd::pack4(v.z)};
}

class BatchSolver {
public:
    BatchSolver(int chain_count, int bone_count, const Chain& config)
        : chain_count_(chain_count),
          bone_count_(bone_count),
          groups_((chain_count + 3) / 4),
          sx_(groups_ * bone_count_), sy_(groups_ * bone_count_), sz_(groups_ * bone_count_),
          ex_(groups_ * bone_count_), ey_(groups_ * bone_count_), ez_(groups_ * bone_count_),
          len_(groups_ * bone_count_),
          bx_(groups_), by_(groups_), bz_(groups_),
          best_sx_(groups_ * bone_count_), best_sy_(groups_ * bone_count_), best_sz_(groups_ * bone_count_),
          best_ex_(groups_ * bone_count_), best_ey_(groups_ * bone_count_), best_ez_(groups_ * bone_count_),
          last_target_(groups_), last_base_(groups_), last_distance_(groups_),
          has_last_(groups_, false) {
        if (chain_count <= 0 || bone_count <= 0) throw std::invalid_argument("invalid batch size");
        joints_.resize(bone_count_);
        for (int i = 0; i < bone_count_; ++i) joints_[i] = config.joint(i);
        base_ = config.base();
        params_ = config.params();
        fixed_base_ = config.fixed_base();
        fixed_base_position_ = broadcast(config.fixed_base_position());
    }

    [[nodiscard]] int chain_count() const noexcept { return chain_count_; }
    [[nodiscard]] int bone_count() const noexcept { return bone_count_; }

    void set_chain(int index, const float* lengths, const float* starts, const float* ends) noexcept {
        const int g = index / 4;
        const int lane = index % 4;
        for (int i = 0; i < bone_count_; ++i) {
            const int k = g * bone_count_ + i;
            sx_[k][lane] = starts[3 * i];
            sy_[k][lane] = starts[3 * i + 1];
            sz_[k][lane] = starts[3 * i + 2];
            ex_[k][lane] = ends[3 * i];
            ey_[k][lane] = ends[3 * i + 1];
            ez_[k][lane] = ends[3 * i + 2];
            len_[k][lane] = lengths[i];
        }
        bx_[g][lane] = starts[0];
        by_[g][lane] = starts[1];
        bz_[g][lane] = starts[2];
        has_last_[g] = false;
    }

    void get_chain(int index, float* starts, float* ends) const noexcept {
        const int g = index / 4;
        const int lane = index % 4;
        for (int i = 0; i < bone_count_; ++i) {
            const int k = g * bone_count_ + i;
            starts[3 * i] = sx_[k][lane];
            starts[3 * i + 1] = sy_[k][lane];
            starts[3 * i + 2] = sz_[k][lane];
            ends[3 * i] = ex_[k][lane];
            ends[3 * i + 1] = ey_[k][lane];
            ends[3 * i + 2] = ez_[k][lane];
        }
    }

    void solve(const float* targets_xyz, float* out_distances) noexcept {
        for (int g = 0; g < groups_; ++g) {
            float tx[4]{}, ty[4]{}, tz[4]{};
            for (int lane = 0; lane < 4; ++lane) {
                const int index = 4 * g + lane;
                if (index < chain_count_) {
                    tx[lane] = targets_xyz[3 * index];
                    ty[lane] = targets_xyz[3 * index + 1];
                    tz[lane] = targets_xyz[3 * index + 2];
                } else {
                    tx[lane] = ty[lane] = tz[lane] = 0.0f;
                }
            }
            const SVec3 target{simd::pack4::load(tx), simd::pack4::load(ty), simd::pack4::load(tz)};
            const auto distance = solve_group(g, target);
            distance.store(out_distances + 4 * g);
        }
    }

private:
    [[nodiscard]] SVec3 start(int g, int i) const noexcept {
        const int k = g * bone_count_ + i;
        return {sx_[k], sy_[k], sz_[k]};
    }
    [[nodiscard]] SVec3 end(int g, int i) const noexcept {
        const int k = g * bone_count_ + i;
        return {ex_[k], ey_[k], ez_[k]};
    }
    [[nodiscard]] SVec3 direction(int g, int i) const noexcept {
        return (end(g, i) - start(g, i)).normalized();
    }

    void set_start(int g, int i, SVec3 v) noexcept {
        const int k = g * bone_count_ + i;
        sx_[k] = v.x; sy_[k] = v.y; sz_[k] = v.z;
    }
    void set_end(int g, int i, SVec3 v) noexcept {
        const int k = g * bone_count_ + i;
        ex_[k] = v.x; ey_[k] = v.y; ez_[k] = v.z;
    }
    [[nodiscard]] simd::pack4 length(int g, int i) const noexcept {
        return len_[g * bone_count_ + i];
    }

    [[nodiscard]] SVec3 base_position(int g) const noexcept {
        return fixed_base_ ? fixed_base_position_
                           : SVec3{bx_[g], by_[g], bz_[g]};
    }

    simd::pack4 solve_group(int g, SVec3 target) noexcept {
        const SVec3 base = base_position(g);
        if (has_last_[g] && approx_equal(last_target_[g], target) && approx_equal(last_base_[g], base))
            return last_distance_[g];

        simd::pack4 best = simd::pack4(std::numeric_limits<float>::infinity());
        simd::pack4 last_pass = best;
        for (int iteration = 0; iteration < params_.max_iterations; ++iteration) {
            const auto distance = solve_ik(g, target);
            const auto improved = distance.less_than(best);
            if (improved.any()) {
                best = simd::blend(improved, distance, best);
                save_best(g);
                const auto done = distance.less_equal(simd::pack4(params_.solve_threshold));
                if (done.all()) break;
            } else {
                const auto delta = (distance - last_pass).abs();
                const auto stalled = delta.less_equal(simd::pack4(params_.min_iteration_change));
                if (stalled.all()) break;
            }
            last_pass = distance;
        }
        restore_best(g);
        last_target_[g] = target;
        last_base_[g] = base;
        last_distance_[g] = best;
        has_last_[g] = true;
        return best;
    }

    [[nodiscard]] static bool approx_equal(SVec3 a, SVec3 b) noexcept {
        const auto dx = (a.x - b.x).abs();
        const auto dy = (a.y - b.y).abs();
        const auto dz = (a.z - b.z).abs();
        const auto tol = simd::pack4(0.001f);
        return dx.less_than(tol).all() && dy.less_than(tol).all() && dz.less_than(tol).all();
    }

    void save_best(int g) noexcept {
        for (int i = 0; i < bone_count_; ++i) {
            const int k = g * bone_count_ + i;
            best_sx_[k] = sx_[k]; best_sy_[k] = sy_[k]; best_sz_[k] = sz_[k];
            best_ex_[k] = ex_[k]; best_ey_[k] = ey_[k]; best_ez_[k] = ez_[k];
        }
    }

    void restore_best(int g) noexcept {
        for (int i = 0; i < bone_count_; ++i) {
            const int k = g * bone_count_ + i;
            sx_[k] = best_sx_[k]; sy_[k] = best_sy_[k]; sz_[k] = best_sz_[k];
            ex_[k] = best_ex_[k]; ey_[k] = best_ey_[k]; ez_[k] = best_ez_[k];
        }
    }

    simd::pack4 solve_ik(int g, SVec3 target) noexcept {
        forward_pass(g, target);
        backward_pass(g);
        return (end(g, bone_count_ - 1) - target).length();
    }

    void forward_pass(int g, SVec3 target) noexcept {
        for (int i = bone_count_ - 1; i >= 0; --i) {
            const Joint& joint = joints_[i];
            SVec3 uv;
            if (i != bone_count_ - 1) {
                const SVec3 outer_uv = -direction(g, i + 1);
                uv = -direction(g, i);
                switch (joint.type) {
                    case FK_JOINT_BALL: {
                        const auto over = angle_deg(outer_uv, uv).greater_than(simd::pack4(joint.ball_deg));
                        if (over.any()) uv = blend(over, angle_limited_uv(uv, outer_uv, simd::pack4(joint.ball_deg)), uv);
                        break;
                    }
                    case FK_JOINT_GLOBAL_HINGE:
                        uv = project_onto_plane(uv, broadcast(joint.hinge_rotation));
                        break;
                    case FK_JOINT_LOCAL_HINGE: {
                        const SVec3 axis = i > 0
                            ? rotation_matrix_times(direction(g, i - 1), broadcast(joint.hinge_rotation))
                            : broadcast(base_.relative_uv);
                        uv = project_onto_plane(uv, axis);
                        break;
                    }
                    default: break;
                }
                const SVec3 new_start = end(g, i) + uv * length(g, i);
                set_start(g, i, new_start);
                if (i > 0) set_end(g, i - 1, new_start);
            } else {
                set_end(g, i, target);
                uv = -direction(g, i);
                switch (joint.type) {
                    case FK_JOINT_BALL: break;
                    case FK_JOINT_GLOBAL_HINGE:
                        uv = project_onto_plane(uv, broadcast(joint.hinge_rotation));
                        break;
                    case FK_JOINT_LOCAL_HINGE:
                        uv = project_onto_plane(uv, rotation_matrix_times(direction(g, i - 1), broadcast(joint.hinge_rotation)));
                        break;
                    default: break;
                }
                const SVec3 new_start = target + uv * length(g, i);
                set_start(g, i, new_start);
                if (i > 0) set_end(g, i - 1, new_start);
            }
        }
    }

    void backward_pass(int g) noexcept {
        for (int i = 0; i < bone_count_; ++i) {
            if (i != 0) {
                const Joint& joint = joints_[i];
                SVec3 uv = direction(g, i);
                const SVec3 previous = direction(g, i - 1);
                switch (joint.type) {
                    case FK_JOINT_BALL: {
                        const auto over = angle_deg(previous, uv).greater_than(simd::pack4(joint.ball_deg));
                        if (over.any()) uv = blend(over, angle_limited_uv(uv, previous, simd::pack4(joint.ball_deg)), uv);
                        break;
                    }
                    case FK_JOINT_GLOBAL_HINGE: {
                        const SVec3 axis = broadcast(joint.hinge_rotation);
                        uv = project_onto_plane(uv, axis);
                        if (!joint.hinge_is_free()) {
                            const auto reference = broadcast(joint.hinge_reference);
                            const auto signed_angle = signed_angle_deg(reference, uv, axis);
                            const auto acw = simd::pack4(joint.anticlockwise_deg);
                            const auto cw = simd::pack4(-joint.clockwise_deg);
                            const auto over_acw = signed_angle.greater_than(acw);
                            const auto over_cw = signed_angle.less_than(cw);
                            if (over_acw.any() || over_cw.any()) {
                                const SVec3 rotated_acw = rotate_about_axis(reference, acw * deg_to_rad, axis).normalized();
                                const SVec3 rotated_cw = rotate_about_axis(reference, cw * deg_to_rad, axis).normalized();
                                uv = blend(over_acw, rotated_acw, uv);
                                uv = blend(over_cw, rotated_cw, uv);
                            }
                        }
                        break;
                    }
                    case FK_JOINT_LOCAL_HINGE: {
                        const SVec3 axis = rotation_matrix_times(previous, broadcast(joint.hinge_rotation));
                        uv = project_onto_plane(uv, axis);
                        if (!joint.hinge_is_free()) {
                            const SVec3 reference = rotation_matrix_times(previous, broadcast(joint.hinge_reference));
                            const auto signed_angle = signed_angle_deg(reference, uv, axis);
                            const auto acw = simd::pack4(joint.anticlockwise_deg);
                            const auto cw = simd::pack4(-joint.clockwise_deg);
                            const auto over_acw = signed_angle.greater_than(acw);
                            const auto over_cw = signed_angle.less_than(cw);
                            if (over_acw.any() || over_cw.any()) {
                                const SVec3 rotated_acw = rotate_about_axis(reference, acw * deg_to_rad, axis).normalized();
                                const SVec3 rotated_cw = rotate_about_axis(reference, cw * deg_to_rad, axis).normalized();
                                uv = blend(over_acw, rotated_acw, uv);
                                uv = blend(over_cw, rotated_cw, uv);
                            }
                        }
                        break;
                    }
                    default: break;
                }
                const SVec3 new_end = start(g, i) + uv * length(g, i);
                set_end(g, i, new_end);
                if (i < bone_count_ - 1) set_start(g, i + 1, new_end);
            } else {
                if (fixed_base_) {
                    set_start(g, 0, fixed_base_position_);
                } else {
                    set_start(g, 0, end(g, 0) - direction(g, 0) * length(g, 0));
                }
                SVec3 uv;
                switch (base_.type) {
                    case FK_BASE_NONE:
                        uv = direction(g, 0);
                        break;
                    case FK_BASE_GLOBAL_ROTOR:
                    case FK_BASE_LOCAL_ROTOR: {
                        uv = direction(g, 0);
                        const SVec3 baseline = base_.type == FK_BASE_GLOBAL_ROTOR
                            ? broadcast(base_.uv) : broadcast(base_.relative_uv);
                        const auto over = angle_deg(baseline, uv).greater_than(simd::pack4(base_.ball_deg));
                        if (over.any()) uv = blend(over, angle_limited_uv(uv, baseline, simd::pack4(base_.ball_deg)), uv);
                        break;
                    }
                    case FK_BASE_GLOBAL_HINGE: {
                        const Joint& joint = joints_[0];
                        const SVec3 axis = broadcast(joint.hinge_rotation);
                        uv = project_onto_plane(direction(g, 0), axis);
                        if (!joint.hinge_is_free()) {
                            const auto reference = broadcast(joint.hinge_reference);
                            const auto signed_angle = signed_angle_deg(reference, uv, axis);
                            const auto acw = simd::pack4(joint.anticlockwise_deg);
                            const auto cw = simd::pack4(-joint.clockwise_deg);
                            const auto over_acw = signed_angle.greater_than(acw);
                            const auto over_cw = signed_angle.less_than(cw);
                            if (over_acw.any() || over_cw.any()) {
                                uv = blend(over_acw, rotate_about_axis(reference, acw * deg_to_rad, axis).normalized(), uv);
                                uv = blend(over_cw, rotate_about_axis(reference, cw * deg_to_rad, axis).normalized(), uv);
                            }
                        }
                        break;
                    }
                    case FK_BASE_LOCAL_HINGE: {
                        const Joint& joint = joints_[0];
                        const SVec3 axis = broadcast(base_.relative_uv);
                        uv = project_onto_plane(direction(g, 0), axis);
                        if (!joint.hinge_is_free()) {
                            const auto reference = broadcast(base_.relative_reference_uv);
                            const auto signed_angle = signed_angle_deg(reference, uv, axis);
                            const auto acw = simd::pack4(joint.anticlockwise_deg);
                            const auto cw = simd::pack4(-joint.clockwise_deg);
                            const auto over_acw = signed_angle.greater_than(acw);
                            const auto over_cw = signed_angle.less_than(cw);
                            if (over_acw.any() || over_cw.any()) {
                                uv = blend(over_acw, rotate_about_axis(reference, acw * deg_to_rad, axis).normalized(), uv);
                                uv = blend(over_cw, rotate_about_axis(reference, cw * deg_to_rad, axis).normalized(), uv);
                            }
                        }
                        break;
                    }
                    default:
                        uv = direction(g, 0);
                        break;
                }
                const SVec3 new_end = start(g, 0) + uv * length(g, 0);
                set_end(g, 0, new_end);
                if (bone_count_ > 1) set_start(g, 1, new_end);
            }
        }
    }

    int chain_count_, bone_count_, groups_;
    std::vector<simd::pack4> sx_, sy_, sz_, ex_, ey_, ez_, len_;
    std::vector<simd::pack4> bx_, by_, bz_;
    std::vector<simd::pack4> best_sx_, best_sy_, best_sz_, best_ex_, best_ey_, best_ez_;
    std::vector<SVec3> last_target_, last_base_;
    std::vector<simd::pack4> last_distance_;
    std::vector<bool> has_last_;
    std::vector<Joint> joints_;
    BaseConstraint base_{};
    SolverParams params_{};
    bool fixed_base_ = false;
    SVec3 fixed_base_position_{};
};

} // namespace fk
