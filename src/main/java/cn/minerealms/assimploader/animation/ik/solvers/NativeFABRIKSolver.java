package cn.minerealms.assimploader.animation.ik.solvers;

import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.nativeapi.fabrik.FabriKNative;
import cn.minerealms.assimploader.nativeapi.fabrik.FabriKLibrary;
import com.sun.jna.Pointer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * FABRIK solver backed by the bundled FabriK native library.
 *
 * <p>Mirrors {@link FABRIKSolver}'s bone-to-Caliko mapping onto {@code fk_chain_*}: world positions
 * become bone starts/ends, the root is pinned as a fixed base, solver parameters come from config,
 * and the solved directions are written back as local rotations with the chain weight applied.
 * The pure-Java {@link FABRIKSolver} is left untouched.
 */
public class NativeFABRIKSolver implements IKSolver, AutoCloseable {

    private Pointer nativeChain;
    private int nativeBoneCount;
    private float[] lengths;
    private float[] starts;
    private float[] ends;
    private final float[] base = new float[3];

    @Override
    public Type getType() {
        return Type.FABRIK;
    }

    @Override
    public String getName() {
        return "FABRIK(native)";
    }

    private int getMaxIterations() {
        return AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.COMMON.animIkMaxIterationsFABRIK, 10);
    }

    private float getDistanceThreshold() {
        return AssimpLoaderConfig.getOrDefault(() -> AssimpLoaderConfig.COMMON.animIkDistanceThreshold.get().floatValue(), 0.01f);
    }

    @Override
    public boolean solve(IKChain chain) {
        FabriKLibrary lib = FabriKNative.library();
        if (lib == null) {
            throw new IllegalStateException("NativeFABRIKSolver requires the FabriK native, but it is unavailable");
        }

        int count = chain.getBones().size();
        if (count < 2) {
            return false;
        }

        if (nativeChain == null || nativeBoneCount != count) {
            close();
            nativeChain = lib.fk_chain_create(count);
            if (nativeChain == null) throw new IllegalStateException("FabriK native could not allocate a chain");
            nativeBoneCount = count;
            lengths = new float[count];
            starts = new float[3 * count];
            ends = new float[3 * count];
        }

        for (int i = 0; i < count; i++) {
            Vector3f start = chain.getBones().get(i).getWorldPosition();
            starts[3 * i] = start.x;
            starts[3 * i + 1] = start.y;
            starts[3 * i + 2] = start.z;

            Vector3f end;
            if (i < count - 1) {
                end = chain.getBones().get(i + 1).getWorldPosition();
                lengths[i] = start.distance(end);
            } else {
                lengths[i] = 1.0f;
                end = new Vector3f(start).add(0.0f, 0.0f, 1.0f);
            }
            ends[3 * i] = end.x;
            ends[3 * i + 1] = end.y;
            ends[3 * i + 2] = end.z;
        }

        lib.fk_chain_set_bones(nativeChain, lengths, starts, ends);
        Vector3f base = chain.getBones().get(0).getWorldPosition();
        this.base[0] = base.x;
        this.base[1] = base.y;
        this.base[2] = base.z;
        lib.fk_chain_set_fixed_base(nativeChain, 1, this.base);
        lib.fk_chain_set_params(nativeChain, getMaxIterations(), 0.0001f, getDistanceThreshold());

        Vector3f target = chain.getTarget();
        lib.fk_chain_solve(nativeChain, target.x, target.y, target.z);
        lib.fk_chain_get_bones(nativeChain, starts, ends);

        applySolutionToBones(chain, starts, ends);
        return true;
    }

    private void applySolutionToBones(IKChain chain, float[] starts, float[] ends) {
        float weight = chain.getWeight();
        Vector3f currentDir = new Vector3f(0.0f, 0.0f, 1.0f);
        for (int i = 0; i < chain.getBones().size() - 1; i++) {
            Vector3f solvedDir = new Vector3f(
                    ends[3 * i] - starts[3 * i],
                    ends[3 * i + 1] - starts[3 * i + 1],
                    ends[3 * i + 2] - starts[3 * i + 2]);
            if (solvedDir.lengthSquared() < 1.0e-12f) {
                continue;
            }
            solvedDir.normalize();
            Quaternionf targetRotation = new Quaternionf().rotationTo(currentDir, solvedDir);
            Bone bone = chain.getBones().get(i);
            if (weight < 1.0f) {
                targetRotation.slerp(bone.getLocalRotation(), 1.0f - weight);
            }
            bone.setLocalRotation(targetRotation);
        }
    }

    @Override
    public void reset(IKChain chain) {
        close();
        for (Bone bone : chain.getBones()) {
            bone.setLocalRotation(new Quaternionf());
        }
    }

    @Override
    public void close() {
        FabriKLibrary lib = FabriKNative.library();
        if (lib != null && nativeChain != null) {
            lib.fk_chain_destroy(nativeChain);
        }
        nativeChain = null;
        nativeBoneCount = 0;
        lengths = null;
        starts = null;
        ends = null;
    }
}
