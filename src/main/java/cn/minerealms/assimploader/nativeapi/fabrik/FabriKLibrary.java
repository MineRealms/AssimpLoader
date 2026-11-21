package cn.minerealms.assimploader.nativeapi.fabrik;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/** JNA mirror of FabriKNative's {@code include/fabrik.h}. */
public interface FabriKLibrary extends Library {

    int FK_JOINT_BALL = 0;
    int FK_JOINT_GLOBAL_HINGE = 1;
    int FK_JOINT_LOCAL_HINGE = 2;

    int FK_BASE_NONE = 0;
    int FK_BASE_GLOBAL_ROTOR = 1;
    int FK_BASE_LOCAL_ROTOR = 2;
    int FK_BASE_GLOBAL_HINGE = 3;
    int FK_BASE_LOCAL_HINGE = 4;

    /** The batch solver stores distances in padded groups of four floats. */
    static int paddedDistanceLength(int chainCount) {
        return 4 * ((chainCount + 3) / 4);
    }

    String fk_version();

    String fk_simd_backend();

    Pointer fk_chain_create(int boneCount);

    void fk_chain_destroy(Pointer chain);

    int fk_chain_bone_count(Pointer chain);

    void fk_chain_set_bones(Pointer chain, float[] lengths, float[] startsXyz, float[] endsXyz);

    void fk_chain_get_bones(Pointer chain, float[] startsXyz, float[] endsXyz);

    void fk_chain_set_joint(Pointer chain, int bone, int type, float ballConstraintDeg,
                            float[] hingeRotationAxisXyz, float[] hingeReferenceAxisXyz,
                            float hingeClockwiseDeg, float hingeAnticlockwiseDeg);

    void fk_chain_set_base_constraint(Pointer chain, int type, float[] constraintUvXyz,
                                      float[] constraintReferenceUvXyz, float ballConstraintDeg,
                                      float hingeClockwiseDeg, float hingeAnticlockwiseDeg);

    void fk_chain_set_fixed_base(Pointer chain, int fixed, float[] baseXyz);

    void fk_chain_set_params(Pointer chain, int maxIterations, float minIterationChange,
                             float solveDistanceThreshold);

    float fk_chain_solve(Pointer chain, float targetX, float targetY, float targetZ);

    float fk_chain_last_distance(Pointer chain);

    Pointer fk_batch_create(int chainCount, int boneCount, Pointer config);

    void fk_batch_destroy(Pointer batch);

    int fk_batch_chain_count(Pointer batch);

    int fk_batch_bone_count(Pointer batch);

    void fk_batch_set_chain(Pointer batch, int index, float[] lengths, float[] startsXyz, float[] endsXyz);

    void fk_batch_get_chain(Pointer batch, int index, float[] startsXyz, float[] endsXyz);

    void fk_batch_solve(Pointer batch, float[] targetsXyz, float[] outDistances);

    Pointer fk_chain2d_create(int boneCount);

    void fk_chain2d_destroy(Pointer chain);

    void fk_chain2d_set_bones(Pointer chain, float[] lengths, float[] startsXy, float[] endsXy);

    void fk_chain2d_get_bones(Pointer chain, float[] startsXy, float[] endsXy);

    void fk_chain2d_set_joint(Pointer chain, int bone, int localCoordinates, float clockwiseDeg,
                              float anticlockwiseDeg, float[] hingeAxisXy);

    void fk_chain2d_set_base_constraint(Pointer chain, int type, float[] constraintUvXy);

    float fk_chain2d_solve(Pointer chain, float targetX, float targetY);
}
