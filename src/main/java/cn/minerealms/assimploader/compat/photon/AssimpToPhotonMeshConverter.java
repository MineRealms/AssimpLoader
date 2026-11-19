package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * AssimpMesh → Photon MeshData 转换器
 * <p>
 * Photon 2.2.11 (the 1.21 build) encapsulated {@code MeshData}: {@code meshName}/{@code vertices}/
 * {@code triangles}/{@code edges} became private with getters that re-derive the geometry from the
 * model source. A freshly constructed {@code MeshData} points at {@code block/stone}, so the getters
 * would discard anything written through them; the NeoForge node therefore fills the fields
 * reflectively. The 1.20.1 build still exposes them as public fields.
 */
public class AssimpToPhotonMeshConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("AssimpToPhotonMeshConverter");

    /**
     * 转换单个 AssimpMesh 到 Photon MeshData
     */
    public static MeshData convert(AssimpMesh assimpMesh) {
        LOGGER.info("Converting mesh: {} ({} vertices, {} faces)",
                assimpMesh.getName(),
                assimpMesh.getVertexCount(),
                assimpMesh.getFaceCount());

        MeshData meshData = new MeshData();
        //? if forge {
        meshData.meshName = assimpMesh.getName();
        //?}

        // 1. 转换顶点
        List<Vector3f> vertices = new ArrayList<>();
        float[] assimpVertices = assimpMesh.getVertices();
        for (int i = 0; i < assimpVertices.length; i += 3) {
            vertices.add(new Vector3f(
                    assimpVertices[i],
                    assimpVertices[i + 1],
                    assimpVertices[i + 2]
            ));
        }

        // 2. 转换三角形（从索引）
        List<MeshData.Triangle> triangles = new ArrayList<>();
        int[] indices = assimpMesh.getIndices();
        double sumArea = 0;

        for (int i = 0; i < indices.length; i += 3) {
            Vector3f v0 = vertices.get(indices[i]);
            Vector3f v1 = vertices.get(indices[i + 1]);
            Vector3f v2 = vertices.get(indices[i + 2]);

            MeshData.Triangle triangle = new MeshData.Triangle(v0, v1, v2);
            triangles.add(triangle);
            sumArea += triangle.area;
        }

        // 3. 转换边（从三角形提取，去重）
        Set<EdgeKey> edgeSet = new HashSet<>();
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];

            edgeSet.add(new EdgeKey(i0, i1));
            edgeSet.add(new EdgeKey(i1, i2));
            edgeSet.add(new EdgeKey(i2, i0));
        }

        List<MeshData.Edge> edges = new ArrayList<>();
        double sumLength = 0;
        for (EdgeKey edgeKey : edgeSet) {
            Vector3f v0 = vertices.get(edgeKey.i0);
            Vector3f v1 = vertices.get(edgeKey.i1);

            MeshData.Edge edge = new MeshData.Edge(v0, v1);
            if (edge.length > 0) {
                edges.add(edge);
                sumLength += edge.length;
            }
        }

        store(meshData, vertices, triangles, edges, sumArea, sumLength);

        LOGGER.debug("Converted {} vertices", vertices.size());
        LOGGER.debug("Converted {} triangles, total area: {}", triangles.size(), sumArea);
        LOGGER.debug("Converted {} edges, total length: {}", edges.size(), sumLength);
        LOGGER.info("Mesh conversion complete: {} vertices, {} triangles, {} edges",
                vertices.size(), triangles.size(), edges.size());

        return meshData;
    }

    /**
     * 转换整个 AssimpScene 的所有 Mesh
     */
    public static Map<String, MeshData> convertAllMeshes(AssimpScene scene) {
        LOGGER.info("Converting scene with {} meshes", scene.getMeshes().size());

        Map<String, MeshData> meshes = new LinkedHashMap<>();

        for (int i = 0; i < scene.getMeshes().size(); i++) {
            AssimpMesh mesh = scene.getMeshes().get(i);
            String name = mesh.getName();

            // 如果名称为空，使用索引
            if (name == null || name.isEmpty()) {
                name = "Mesh_" + i;
            }

            // 避免重名
            String finalName = name;
            int suffix = 1;
            while (meshes.containsKey(finalName)) {
                finalName = name + "_" + suffix;
                suffix++;
            }

            try {
                MeshData meshData = convert(mesh);
                meshes.put(finalName, meshData);
                LOGGER.info("Converted mesh: {}", finalName);
            } catch (Exception e) {
                LOGGER.error("Failed to convert mesh: {}", finalName, e);
            }
        }

        LOGGER.info("Scene conversion complete: {} meshes converted", meshes.size());
        return meshes;
    }

    private static void store(MeshData meshData, List<Vector3f> vertices, List<MeshData.Triangle> triangles,
                              List<MeshData.Edge> edges, double sumArea, double sumLength) {
        //? if forge {
        meshData.vertices.addAll(vertices);
        meshData.triangles.addAll(triangles);
        meshData.edges.addAll(edges);
        try {
            Field triangleSumAreaField = MeshData.class.getDeclaredField("triangleSumArea");
            triangleSumAreaField.setAccessible(true);
            triangleSumAreaField.set(meshData, sumArea);
        } catch (Exception e) {
            LOGGER.error("Failed to set triangleSumArea", e);
        }
        try {
            Field edgeSumLengthField = MeshData.class.getDeclaredField("edgeSumLength");
            edgeSumLengthField.setAccessible(true);
            edgeSumLengthField.set(meshData, sumLength);
        } catch (Exception e) {
            LOGGER.error("Failed to set edgeSumLength", e);
        }
        //?}
        //? if neoforge {
        /*
        setField(meshData, "vertices", vertices);
        setField(meshData, "triangles", triangles);
        setField(meshData, "edges", edges);
        setField(meshData, "triangleSumArea", sumArea);
        setField(meshData, "edgeSumLength", sumLength);
        // `ensureLoaded` compares the cached mesh against `source.getMesh()` and rebuilds from it
        // when they differ; a fresh MeshData resolves to block/stone, which would wipe the lists
        // above on the next getter call. Cache the same instance so the comparison short-circuits.
        try {
            Object source = getField(meshData, "source");
            if (source instanceof com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource modelSource) {
                setField(meshData, "derivedFrom", modelSource.getMesh());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not cache the MeshData source mesh; previews may fall back to the default model", e);
        }
        *///?}
    }

    // Reflective access is fatal here by design: a `MeshData` whose geometry silently stayed at the
    // default `block/stone` is worse than a loud failure, so the checked exceptions are folded into a
    // runtime one instead of propagating through the caller.
    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot write MeshData field " + name, e);
        }
    }

    private static Object getField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read MeshData field " + name, e);
        }
    }

    /**
     * 辅助类：边的键（用于去重）
     */
    private static class EdgeKey {
        final int i0, i1;

        EdgeKey(int i0, int i1) {
            if (i0 < i1) {
                this.i0 = i0;
                this.i1 = i1;
            } else {
                this.i0 = i1;
                this.i1 = i0;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EdgeKey)) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return i0 == edgeKey.i0 && i1 == edgeKey.i1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(i0, i1);
        }
    }
}
