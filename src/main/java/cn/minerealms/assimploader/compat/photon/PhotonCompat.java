package cn.minerealms.assimploader.compat.photon;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Photon 兼容层入口
 * 负责检测 Photon 是否存在并加载相应的 Mixin
 *
 * 设计原则：
 * - AssimpLoader 主动兼容 Photon
 * - 仅在 Photon 存在时加载集成代码
 * - 不影响没有 Photon 的用户
 */
public class PhotonCompat {

    private static final Logger LOGGER = LogManager.getLogger("AssimpLoader-PhotonCompat");
    private static boolean PHOTON_LOADED = false;
    private static boolean INITIALIZED = false;

    /**
     * 初始化 Photon 集成
     * 在 AssimpLoader 主类的构造函数中调用
     */
    public static void init() {
        if (INITIALIZED) {
            return;
        }

        INITIALIZED = true;

        // 检测 Photon 是否加载
        if (!ModList.get().isLoaded("photon")) {
            LOGGER.info("Photon not detected, skipping integration");
            return;
        }

        try {
            // 验证关键类是否存在
            Class.forName("com.lowdragmc.photon.client.fx.FX");
            Class.forName("com.lowdragmc.photon.gui.editor.MeshesResource");
            Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData");

            LOGGER.info("Photon detected, enabling integration");

            // 加载 Photon 兼容 Mixin
            Mixins.addConfiguration("mixins.photon_compat.json");

            PHOTON_LOADED = true;
            LOGGER.info("Photon integration enabled successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.warn("Photon mod found but required classes not available: {}", e.getMessage());
            PHOTON_LOADED = false;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Photon integration", e);
            PHOTON_LOADED = false;
        }
    }

    /**
     * 检查 Photon 是否已加载并可用
     */
    public static boolean isPhotonLoaded() {
        return PHOTON_LOADED;
    }

    /**
     * 获取 Photon 版本信息（用于调试）
     */
    public static String getPhotonVersion() {
        if (!PHOTON_LOADED) {
            return "Not Loaded";
        }

        try {
            return ModList.get().getModContainerById("photon")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unknown");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
