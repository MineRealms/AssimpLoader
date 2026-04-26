package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AssimpMesh → Photon MeshData 转换器
 *
 * 将 Assimp 加载的网格数据转换为 Photon 可用的 MeshData 格式。
 *
 * 转换内容：
 * - 顶点位置（Vector3f）
 * - 三角形面（从索引构建）
 * - 边（从三角形提取，自动去重）
 * - 面积和长度总和（用于粒子发射概率计算）
 *
 * 使用场景：
 * - Photon 粒子发射器可以从网格表面发射粒子
 * - 支持三种发射模式：Vertex（顶点）、Edge（边）、Triangle（三角形表面）
 *
 * 注意：此类使用反射调用 Photon 类，确保在没有 Photon 时不会加载
 */
public class AssimpToPhotonMeshConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("AssimpToPhotonMeshConverter");

    /**
     * 转换单个 AssimpMesh 到 Photon MeshData
     *
     * @param assimpMesh Assimp 网格数据
     * @return Photon MeshData 对象（通过反射创建）
     * @throws Exception 如果 Photon 类不可用或转换失败
     */
    public static Object convert(AssimpMesh assimpMesh) throws Exception {
        if (!PhotonCompat.isPhotonLoaded()) {
            throw new IllegalStateException("Photon is not loaded");
        }

        LOGGER.info("Converting mesh: {} ({} vertices, {} faces)",
                assimpMesh.getName(),
                assimpMesh.getVertexCount(),
                assimpMesh.getFaceCount());

        // 使用反射创建 MeshData 实例
        Class<?> meshDataClass = Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData");
        Object meshData = meshDataClass.getDeclaredConstructor().newInstance();

        // 设置网格名称
        meshDataClass.getField("meshName").set(meshData, assimpMesh.getName());

        // 获取 vertices, edges, triangles 列表
        @SuppressWarnings("unchecked")
        List<Vector3f> vertices = (List<Vector3f>) meshDataClass.getField("vertices").get(meshData);

        @SuppressWarnings("unchecked")
        List<Object> edges = (List<Object>) meshDataClass.getField("edges").get(meshData);

        @SuppressWarnings("unchecked")
        List<Object> triangles = (List<Object>) meshDataClass.getField("triangles").get(meshData);

        // 1. 转换顶点
        float[] assimpVertices = assimpMesh.getVertices();
        for (int i = 0; i < assimpVertices.length; i += 3) {
            vertices.add(new Vector3f(
                    assimpVertices[i],
                    assimpVertices[i + 1],
                    assimpVertices[i + 2]
            ));
        }

        LOGGER.debug("Converted {} vertices", vertices.size());

        // 2. 转换三角形（从索引）
        int[] indices = assimpMesh.getIndices();
        Class<?> triangleClass = Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData$Triangle");
        double sumArea = 0;

        for (int i = 0; i < indices.length; i += 3) {
            Vector3f v0 = vertices.get(indices[i]);
            Vector3f v1 = vertices.get(indices[i + 1]);
            Vector3f v2 = vertices.get(indices[i + 2]);

            // 创建 Triangle 对象
            Object triangle = triangleClass.getDeclaredConstructor(Vector3f.class, Vector3f.class, Vector3f.class)
                    .newInstance(v0, v1, v2);

            triangles.add(triangle);

            // 累加面积
            double area = (double) triangleClass.getField("area").get(triangle);
            sumArea += area;
        }

        meshDataClass.getDeclaredField("triangleSumArea").set(meshData, sumArea);
        LOGGER.debug("Converted {} triangles, total area: {}", triangles.size(), sumArea);

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

        Class<?> edgeClass = Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData$Edge");
        double sumLength = 0;

        for (EdgeKey edgeKey : edgeSet) {
            Vector3f v0 = vertices.get(edgeKey.i0);
            Vector3f v1 = vertices.get(edgeKey.i1);

            // 创建 Edge 对象
            Object edge = edgeClass.getDeclaredConstructor(Vector3f.class, Vector3f.class)
                    .newInstance(v0, v1);

            double length = (double) edgeClass.getField("length").get(edge);
            if (length > 0) {
                edges.add(edge);
                sumLength += length;
            }
        }

        meshDataClass.getDeclaredField("edgeSumLength").set(meshData, sumLength);
        LOGGER.debug("Converted {} edges, total length: {}", edges.size(), sumLength);

        LOGGER.info("Mesh conversion complete: {} vertices, {} triangles, {} edges",
                vertices.size(), triangles.size(), edges.size());

        return meshData;
    }

    /**
     * 转换整个 AssimpScene 的所有 Mesh
     *
     * @param scene Assimp 场景
     * @return Map<网格名称, MeshData对象>
     * @throws Exception 如果转换失败
     */
    public static Map<String, Object> convertScene(AssimpScene scene) throws Exception {
        if (!PhotonCompat.isPhotonLoaded()) {
            throw new IllegalStateException("Photon is not loaded");
        }

        LOGGER.info("Converting scene with {} meshes", scene.getMeshes().size());

        Map<String, Object> meshes = new LinkedHashMap<>();

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
                Object meshData = convert(mesh);
                meshes.put(finalName, meshData);
                LOGGER.info("Converted mesh: {}", finalName);
            } catch (Exception e) {
                LOGGER.error("Failed to convert mesh: {}", finalName, e);
                // 继续转换其他网格
            }
        }

        LOGGER.info("Scene conversion complete: {} meshes converted", meshes.size());
        return meshes;
    }

    /**
     * 辅助类：边的键（用于去重）
     * 确保 (i0, i1) 和 (i1, i0) 被视为同一条边
     */
    private static class EdgeKey {
        final int i0, i1;

        EdgeKey(int i0, int i1) {
            // 确保 i0 < i1，用于去重
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
