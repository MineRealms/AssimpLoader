package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpMesh;
import org.lwjgl.assimp.Assimp;

/** Morphs bind geometry before skinning. No frame ever reuses a previously deformed base. */
public final class MorphDeformation {
    public record Geometry(float[] positions, float[] normals) {}
    private MorphDeformation() {}

    public static Geometry deform(AssimpMesh mesh, float[] weights) {
        float[] effective = effectiveWeights(mesh, weights);
        float[] positions = mesh.getVertices().clone();
        float[] normals = mesh.getNormals() == null ? null : mesh.getNormals().clone();
        boolean active = false;
        for (float weight : effective) if (weight != 0) active = true;
        if (!active) return new Geometry(positions, normals);
        for (int i = 0; i < effective.length; i++) {
            if (effective[i] != 0) mesh.getMorphTargets().get(i).accumulate(effective[i], positions, normals);
        }
        if (normals != null) for (int i = 0; i < normals.length; i += 3) {
            float length = (float) Math.sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2]);
            if (length > 1.0e-8f) { normals[i] /= length; normals[i + 1] /= length; normals[i + 2] /= length; }
            else { normals[i] = 0; normals[i + 1] = 1; normals[i + 2] = 0; }
        }
        return new Geometry(positions, normals);
    }

    /** Shared weight semantics for CPU and GPU; output never aliases the instance snapshot. */
    public static float[] effectiveWeights(AssimpMesh mesh, float[] weights) {
        if (weights.length != mesh.getMorphTargets().size()) throw new IllegalArgumentException("Morph weight count mismatch");
        double sum = 0;
        for (float weight : weights) {
            if (!Float.isFinite(weight)) throw new IllegalArgumentException("Non-finite morph weight");
            sum += Math.max(0, weight);
        }
        boolean normalized = mesh.getMorphMethod() == Assimp.aiMorphingMethod_MORPH_NORMALIZED
                || mesh.getMorphMethod() == Assimp.aiMorphingMethod_VERTEX_BLEND;
        double divisor = normalized && sum > 1 ? sum : 1;
        float[] effective = new float[weights.length];
        for (int i = 0; i < weights.length; i++) {
            effective[i] = normalized ? (float) (Math.max(0, weights[i]) / divisor) : weights[i];
        }
        return effective;
    }
}
