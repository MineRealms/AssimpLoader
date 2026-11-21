#pragma once

#include <stdint.h>

#if defined(_WIN32) || defined(__CYGWIN__)
    #if defined(FK_BUILDING_DLL)
        #define FK_API __declspec(dllexport)
    #else
        #define FK_API __declspec(dllimport)
    #endif
#else
    #define FK_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct FkChain FkChain;
typedef struct FkBatch FkBatch;
typedef struct FkChain2D FkChain2D;

enum {
    FK_JOINT_BALL = 0,
    FK_JOINT_GLOBAL_HINGE = 1,
    FK_JOINT_LOCAL_HINGE = 2
};

enum {
    FK_BASE_NONE = 0,
    FK_BASE_GLOBAL_ROTOR = 1,
    FK_BASE_LOCAL_ROTOR = 2,
    FK_BASE_GLOBAL_HINGE = 3,
    FK_BASE_LOCAL_HINGE = 4
};

FK_API const char* fk_version(void);
FK_API const char* fk_simd_backend(void);

FK_API FkChain* fk_chain_create(int32_t bone_count);
FK_API void fk_chain_destroy(FkChain* chain);
FK_API int32_t fk_chain_bone_count(const FkChain* chain);

FK_API void fk_chain_set_bones(FkChain* chain, const float* lengths, const float* starts_xyz, const float* ends_xyz);
FK_API void fk_chain_get_bones(const FkChain* chain, float* starts_xyz, float* ends_xyz);

FK_API void fk_chain_set_joint(FkChain* chain, int32_t bone, int32_t type,
                               float ball_constraint_deg,
                               const float* hinge_rotation_axis_xyz,
                               const float* hinge_reference_axis_xyz,
                               float hinge_clockwise_deg,
                               float hinge_anticlockwise_deg);

FK_API void fk_chain_set_base_constraint(FkChain* chain, int32_t type,
                                         const float* constraint_uv_xyz,
                                         const float* constraint_reference_uv_xyz,
                                         float ball_constraint_deg,
                                         float hinge_clockwise_deg,
                                         float hinge_anticlockwise_deg);

FK_API void fk_chain_set_fixed_base(FkChain* chain, int32_t fixed, const float* base_xyz);
FK_API void fk_chain_set_params(FkChain* chain, int32_t max_iterations, float min_iteration_change, float solve_distance_threshold);

FK_API float fk_chain_solve(FkChain* chain, float target_x, float target_y, float target_z);
FK_API float fk_chain_last_distance(const FkChain* chain);

FK_API FkBatch* fk_batch_create(int32_t chain_count, int32_t bone_count, const FkChain* config);
FK_API void fk_batch_destroy(FkBatch* batch);
FK_API int32_t fk_batch_chain_count(const FkBatch* batch);
FK_API int32_t fk_batch_bone_count(const FkBatch* batch);

FK_API void fk_batch_set_chain(FkBatch* batch, int32_t index, const float* lengths, const float* starts_xyz, const float* ends_xyz);
FK_API void fk_batch_get_chain(const FkBatch* batch, int32_t index, float* starts_xyz, float* ends_xyz);
FK_API void fk_batch_solve(FkBatch* batch, const float* targets_xyz, float* out_distances);

FK_API FkChain2D* fk_chain2d_create(int32_t bone_count);
FK_API void fk_chain2d_destroy(FkChain2D* chain);
FK_API void fk_chain2d_set_bones(FkChain2D* chain, const float* lengths, const float* starts_xy, const float* ends_xy);
FK_API void fk_chain2d_get_bones(const FkChain2D* chain, float* starts_xy, float* ends_xy);
FK_API void fk_chain2d_set_joint(FkChain2D* chain, int32_t bone, int32_t local_coordinates,
                                 float clockwise_deg, float anticlockwise_deg, const float* hinge_axis_xy);
FK_API void fk_chain2d_set_base_constraint(FkChain2D* chain, int32_t type, const float* constraint_uv_xy);
FK_API float fk_chain2d_solve(FkChain2D* chain, float target_x, float target_y);

#ifdef __cplusplus
}
#endif
