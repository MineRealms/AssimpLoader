package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.compat.OptionalIntegrationGuards;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Photon 兼容层入口
 * 检测 Photon 粒子编辑器；可选 Mixin 由 PhotonMixinPlugin 在早期检查。
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

        ModList mods = ModList.get();
        if (mods == null) return;
        INITIALIZED = true;

        // 检测 Photon 是否加载
        if (!OptionalIntegrationGuards.requiredPhotonMods(mods::isLoaded)) {
            LOGGER.info("Photon/LDLib editor dependencies not detected, skipping optional import menus");
            return;
        }

        try {
            // Check resources without initializing third-party client/editor classes during common setup.
            PHOTON_LOADED = OptionalIntegrationGuards.photonEditorAvailable(name ->
                    OptionalIntegrationGuards.classResourcePresent(PhotonCompat.class.getClassLoader(), name));
            if (PHOTON_LOADED) LOGGER.info("Photon particle editor detected; optional Assimp import menus are available");
            else LOGGER.warn("Photon/LDLib found but editor classes are incompatible; optional import menus disabled");
            // The manifest registers the config exactly once; PhotonMixinPlugin owns early target guards.
        } catch (RuntimeException | LinkageError e) {
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
