package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 测试 AssimpToPhotonMeshConverter
 *
 * 用法：
 * 1. 确保 Photon 模组已加载
 * 2. 在游戏中运行此测试
 * 3. 检查日志输出
 */
public class MeshConverterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("MeshConverterTest");

    /**
     * 测试转换简单立方体
     */
    public static void testSimpleCube() {
        if (!PhotonCompat.isPhotonLoaded()) {
            LOGGER.warn("Photon not loaded, skipping test");
            return;
        }

        LOGGER.info("=== Testing Simple Cube Conversion ===");

        try {
            // 加载测试 FBX 文件（假设存在）
            Path testFile = Paths.get("test_models/cube.fbx");
            AssimpScene scene = AssimpModelLoader.loadModel(testFile);

            // 转换所有 Mesh
            Map<String, Object> meshes = AssimpToPhotonMeshConverter.convertScene(scene);

            LOGGER.info("Conversion successful: {} meshes", meshes.size());

            // 验证数据
            for (Map.Entry<String, Object> entry : meshes.entrySet()) {
                LOGGER.info("Mesh: {}", entry.getKey());
                // TODO: 验证顶点数、三角形数、边数
            }

            // 清理
            scene.release();

            LOGGER.info("=== Test Passed ===");

        } catch (Exception e) {
            LOGGER.error("Test failed", e);
        }
    }

    /**
     * 测试转换复杂 FBX 模型
     */
    public static void testComplexModel() {
        if (!PhotonCompat.isPhotonLoaded()) {
            LOGGER.warn("Photon not loaded, skipping test");
            return;
        }

        LOGGER.info("=== Testing Complex Model Conversion ===");

        try {
            // 加载复杂模型
            Path testFile = Paths.get("test_models/complex.fbx");
            AssimpScene scene = AssimpModelLoader.loadModel(testFile);

            LOGGER.info("Scene loaded: {} meshes", scene.getMeshes().size());

            // 转换
            long startTime = System.currentTimeMillis();
            Map<String, Object> meshes = AssimpToPhotonMeshConverter.convertScene(scene);
            long duration = System.currentTimeMillis() - startTime;

            LOGGER.info("Conversion completed in {} ms", duration);
            LOGGER.info("Converted {} meshes", meshes.size());

            // 清理
            scene.release();

            LOGGER.info("=== Test Passed ===");

        } catch (Exception e) {
            LOGGER.error("Test failed", e);
        }
    }
}
