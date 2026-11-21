#include "fabrik.h"

#include "batch.hpp"
#include "solver.hpp"
#include "solver2d.hpp"
#include "utils.hpp"

#include <new>

struct FkChain {
    explicit FkChain(int n) : impl(n) {}
    fk::Chain impl;
};

struct FkBatch {
    FkBatch(int c, int n, const fk::Chain& config) : impl(c, n, config) {}
    fk::BatchSolver impl;
};

struct FkChain2D {
    explicit FkChain2D(int n) : impl(n) {}
    fk::Chain2D impl;
};

extern "C" {

const char* fk_version(void) { return "1.0.0-modern-cpp20"; }
const char* fk_simd_backend(void) { return fk::simd::backend_name(); }

FkChain* fk_chain_create(int32_t bone_count) {
    try { return new FkChain(bone_count); }
    catch (...) { return nullptr; }
}

void fk_chain_destroy(FkChain* chain) { delete chain; }

int32_t fk_chain_bone_count(const FkChain* chain) {
    return chain ? chain->impl.bone_count() : 0;
}

void fk_chain_set_bones(FkChain* chain, const float* lengths, const float* starts, const float* ends) {
    if (chain && lengths && starts && ends) chain->impl.set_bones(lengths, starts, ends);
}

void fk_chain_get_bones(const FkChain* chain, float* starts, float* ends) {
    if (chain && starts && ends) chain->impl.get_bones(starts, ends);
}

void fk_chain_set_joint(FkChain* chain, int32_t bone, int32_t type,
                        float ball_deg, const float* hinge_axis, const float* hinge_ref,
                        float cw_deg, float acw_deg) {
    if (!chain || bone < 0 || bone >= chain->impl.bone_count()) return;
    auto& joint = chain->impl.joint(bone);
    joint.type = type;
    joint.ball_deg = ball_deg;
    joint.clockwise_deg = cw_deg;
    joint.anticlockwise_deg = acw_deg;
    if (hinge_axis) joint.hinge_rotation = fk::Vec3::load(hinge_axis);
    if (hinge_ref) joint.hinge_reference = fk::Vec3::load(hinge_ref);
}

void fk_chain_set_base_constraint(FkChain* chain, int32_t type,
                                  const float* uv, const float* reference_uv,
                                  float ball_deg, float cw_deg, float acw_deg) {
    if (!chain) return;
    auto& base = chain->impl.base();
    base.type = type;
    base.ball_deg = ball_deg;
    base.clockwise_deg = cw_deg;
    base.anticlockwise_deg = acw_deg;
    if (uv) { base.uv = fk::Vec3::load(uv); base.relative_uv = base.uv; }
    if (reference_uv) { base.reference_uv = fk::Vec3::load(reference_uv); base.relative_reference_uv = base.reference_uv; }
}

void fk_chain_set_fixed_base(FkChain* chain, int32_t fixed, const float* base_xyz) {
    if (!chain) return;
    chain->impl.fixed_base() = fixed != 0;
    if (base_xyz) chain->impl.fixed_base_position() = fk::Vec3::load(base_xyz);
}

void fk_chain_set_params(FkChain* chain, int32_t max_iterations, float min_change, float threshold) {
    if (!chain) return;
    auto& params = chain->impl.params();
    params.max_iterations = max_iterations;
    params.min_iteration_change = min_change;
    params.solve_threshold = threshold;
}

float fk_chain_solve(FkChain* chain, float x, float y, float z) {
    return chain ? chain->impl.solve_for_target({x, y, z}) : 0.0f;
}

float fk_chain_last_distance(const FkChain* chain) {
    return chain ? chain->impl.last_distance() : 0.0f;
}

FkBatch* fk_batch_create(int32_t chain_count, int32_t bone_count, const FkChain* config) {
    try {
        static const FkChain default_config(1);
        const FkChain& cfg = config ? *config : default_config;
        return new FkBatch(chain_count, bone_count, cfg.impl);
    } catch (...) { return nullptr; }
}

void fk_batch_destroy(FkBatch* batch) { delete batch; }
int32_t fk_batch_chain_count(const FkBatch* batch) { return batch ? batch->impl.chain_count() : 0; }
int32_t fk_batch_bone_count(const FkBatch* batch) { return batch ? batch->impl.bone_count() : 0; }

void fk_batch_set_chain(FkBatch* batch, int32_t index, const float* lengths, const float* starts, const float* ends) {
    if (batch && lengths && starts && ends) batch->impl.set_chain(index, lengths, starts, ends);
}

void fk_batch_get_chain(const FkBatch* batch, int32_t index, float* starts, float* ends) {
    if (batch && starts && ends) batch->impl.get_chain(index, starts, ends);
}

void fk_batch_solve(FkBatch* batch, const float* targets, float* out_distances) {
    if (batch && targets && out_distances) batch->impl.solve(targets, out_distances);
}

FkChain2D* fk_chain2d_create(int32_t bone_count) {
    try { return new FkChain2D(bone_count); }
    catch (...) { return nullptr; }
}

void fk_chain2d_destroy(FkChain2D* chain) { delete chain; }

void fk_chain2d_set_bones(FkChain2D* chain, const float* lengths, const float* starts, const float* ends) {
    if (chain && lengths && starts && ends) chain->impl.set_bones(lengths, starts, ends);
}

void fk_chain2d_get_bones(const FkChain2D* chain, float* starts, float* ends) {
    if (chain && starts && ends) chain->impl.get_bones(starts, ends);
}

void fk_chain2d_set_joint(FkChain2D* chain, int32_t bone, int32_t local_coordinates,
                          float cw_deg, float acw_deg, const float* hinge_axis) {
    if (!chain || bone < 0 || bone >= chain->impl.bone_count()) return;
    auto& joint = chain->impl.joint(bone);
    joint.coordinate_system = local_coordinates
        ? fk::Joint2D::CoordinateSystem::local
        : fk::Joint2D::CoordinateSystem::global;
    joint.clockwise_deg = cw_deg;
    joint.anticlockwise_deg = acw_deg;
    if (hinge_axis) joint.hinge_rotation = {hinge_axis[0], hinge_axis[1]};
}

void fk_chain2d_set_base_constraint(FkChain2D* chain, int32_t type, const float* uv) {
    if (!chain) return;
    chain->impl.base_constraint_type() = static_cast<fk::BaseConstraintType2D>(type);
    if (uv) chain->impl.base_constraint_uv() = {uv[0], uv[1]};
}

float fk_chain2d_solve(FkChain2D* chain, float x, float y) {
    return chain ? chain->impl.solve_for_target({x, y}) : 0.0f;
}

} // extern "C"
