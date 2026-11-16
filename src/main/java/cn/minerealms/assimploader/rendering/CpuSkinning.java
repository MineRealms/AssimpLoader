package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.MorphDeformation;
import cn.minerealms.assimploader.model.AssimpMesh;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Shader-pack-compatible fallback, shared across passes of one completed pose. */
public final class CpuSkinning {
    public record Geometry(float[] positions, float[] normals) {}
    private static final Map<PoseSnapshot, Map<AssimpMesh, Geometry>> CACHE = new WeakHashMap<>();
    private CpuSkinning() {}

    public static Geometry geometry(AnimationState state, AssimpMesh mesh) {
        return geometry(state == null ? null : state.getPoseSnapshot(), mesh);
    }

    public static Geometry geometry(PoseSnapshot snapshot, AssimpMesh mesh) {
        if (snapshot == null) {
            if (mesh.hasMorphTargets()) {
                var morph = MorphDeformation.deform(mesh, mesh.getDefaultMorphWeights());
                return new Geometry(morph.positions(), morph.normals());
            }
            return new Geometry(mesh.getVertices(), mesh.getNormals());
        }
        Map<AssimpMesh, Geometry> meshes = CACHE.computeIfAbsent(snapshot, ignored -> new IdentityHashMap<>());
        Geometry cached = meshes.get(mesh);
        if (cached != null) return cached;
        Matrix4f[] palette = snapshot.getBoneTransforms(mesh);
        float[] positions = mesh.getVertices(), normals = mesh.getNormals();
        if (snapshot.hasActiveMorphs(mesh)) {
            var morph = MorphDeformation.deform(mesh, snapshot.getMorphWeights(mesh));
            positions = morph.positions();
            normals = morph.normals();
        }
        if (palette == null) {
            Geometry geometry = new Geometry(positions, normals);
            meshes.put(mesh, geometry);
            return geometry;
        }
        var data = mesh.getSkinnedData();
        Geometry geometry = deform(positions, normals, data.getBoneIndices(), data.getBoneWeights(), palette);
        meshes.put(mesh, geometry);
        return geometry;
    }

    static Geometry deform(float[] positions, float[] normals, int[] indices, float[] weights, Matrix4f[] palette) {
        int vertices = positions.length / 3;
        int stride = vertices == 0 ? 0 : weights.length / vertices;
        float[] result = new float[positions.length], resultNormals = new float[positions.length];
        Matrix4f skin = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        for (int vertex = 0; vertex < vertices; vertex++) {
            skin.zero();
            float total = 0;
            for (int slot = 0; slot < stride; slot++) {
                int index = indices[vertex * stride + slot];
                float weight = weights[vertex * stride + slot];
                if (index < 0 || index >= palette.length || !Float.isFinite(weight) || weight <= 0) continue;
                Matrix4f bone = palette[index];
                skin.m00(skin.m00() + bone.m00() * weight).m01(skin.m01() + bone.m01() * weight).m02(skin.m02() + bone.m02() * weight)
                        .m10(skin.m10() + bone.m10() * weight).m11(skin.m11() + bone.m11() * weight).m12(skin.m12() + bone.m12() * weight)
                        .m20(skin.m20() + bone.m20() * weight).m21(skin.m21() + bone.m21() * weight).m22(skin.m22() + bone.m22() * weight)
                        .m30(skin.m30() + bone.m30() * weight).m31(skin.m31() + bone.m31() * weight).m32(skin.m32() + bone.m32() * weight);
                total += weight;
            }
            if (total <= 1.0e-8f) skin.identity();
            else {
                float inverse = 1.0f / total;
                skin.m00(skin.m00() * inverse).m01(skin.m01() * inverse).m02(skin.m02() * inverse)
                        .m10(skin.m10() * inverse).m11(skin.m11() * inverse).m12(skin.m12() * inverse)
                        .m20(skin.m20() * inverse).m21(skin.m21() * inverse).m22(skin.m22() * inverse)
                        .m30(skin.m30() * inverse).m31(skin.m31() * inverse).m32(skin.m32() * inverse).m33(1);
            }
            int p = vertex * 3;
            float x = positions[p], y = positions[p + 1], z = positions[p + 2];
            result[p] = skin.m00() * x + skin.m10() * y + skin.m20() * z + skin.m30();
            result[p + 1] = skin.m01() * x + skin.m11() * y + skin.m21() * z + skin.m31();
            result[p + 2] = skin.m02() * x + skin.m12() * y + skin.m22() * z + skin.m32();
            normal.set(skin);
            if (Math.abs(normal.determinant()) > 1.0e-12f) normal.invert().transpose();
            else normal.identity();
            float nx = normals == null ? 0 : normals[p], ny = normals == null ? 1 : normals[p + 1], nz = normals == null ? 0 : normals[p + 2];
            resultNormals[p] = normal.m00() * nx + normal.m10() * ny + normal.m20() * nz;
            resultNormals[p + 1] = normal.m01() * nx + normal.m11() * ny + normal.m21() * nz;
            resultNormals[p + 2] = normal.m02() * nx + normal.m12() * ny + normal.m22() * nz;
        }
        return new Geometry(result, resultNormals);
    }

    public static void clear() { CACHE.clear(); }
}
