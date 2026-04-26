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
        meshData.meshName = assimpMesh.getName();

        // 1. 转换顶点
        float[] assimpVertices = assimpMesh.getVertices();
        for (int i = 0; i < assimpVertices.length; i += 3) {
            meshData.vertices.add(new Vector3f(
                    assimpVertices[i],
                    assimpVertices[i + 1],
                    assimpVertices[i + 2]
            ));
        }

        LOGGER.debug("Converted {} vertices", meshData.vertices.size());

        // 2. 转换三角形（从索引）
        int[] indices = assimpMesh.getIndices();
        double sumArea = 0;

        for (int i = 0; i < indices.length; i += 3) {
            Vector3f v0 = meshData.vertices.get(indices[i]);
            Vector3f v1 = meshData.vertices.get(indices[i + 1]);
            Vector3f v2 = meshData.vertices.get(indices[i + 2]);

            MeshData.Triangle triangle = new MeshData.Triangle(v0, v1, v2);
            meshData.triangles.add(triangle);
            sumArea += triangle.area;
        }

        // Use reflection to set protected field
        try {
            Field triangleSumAreaField = MeshData.class.getDeclaredField("triangleSumArea");
            triangleSumAreaField.setAccessible(true);
            triangleSumAreaField.set(meshData, sumArea);
        } catch (Exception e) {
            LOGGER.error("Failed to set triangleSumArea", e);
        }

        LOGGER.debug("Converted {} triangles, total area: {}", meshData.triangles.size(), sumArea);

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

        double sumLength = 0;
        for (EdgeKey edgeKey : edgeSet) {
            Vector3f v0 = meshData.vertices.get(edgeKey.i0);
            Vector3f v1 = meshData.vertices.get(edgeKey.i1);

            MeshData.Edge edge = new MeshData.Edge(v0, v1);
            if (edge.length > 0) {
                meshData.edges.add(edge);
                sumLength += edge.length;
            }
        }

        // Use reflection to set protected field
        try {
            Field edgeSumLengthField = MeshData.class.getDeclaredField("edgeSumLength");
            edgeSumLengthField.setAccessible(true);
            edgeSumLengthField.set(meshData, sumLength);
        } catch (Exception e) {
            LOGGER.error("Failed to set edgeSumLength", e);
        }

        LOGGER.debug("Converted {} edges, total length: {}", meshData.edges.size(), sumLength);

        LOGGER.info("Mesh conversion complete: {} vertices, {} triangles, {} edges",
                meshData.vertices.size(), meshData.triangles.size(), meshData.edges.size());

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
