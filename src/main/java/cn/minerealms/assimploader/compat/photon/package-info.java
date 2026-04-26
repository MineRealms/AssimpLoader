package cn.minerealms.assimploader.compat.photon;

/**
 * Photon 兼容层包
 *
 * 此包包含 AssimpLoader 与 Photon 集成的所有代码。
 *
 * 核心组件：
 * - PhotonCompat: 兼容层入口，负责条件加载
 * - AssimpToPhotonMeshConverter: Mesh 数据转换器
 * - AssimpToPhotonMaterialConverter: Material 数据转换器
 * - FBXEmitterExtractor: 发射器数据提取器
 *
 * 设计原则：
 * 1. AssimpLoader 主动兼容 Photon（而非反向）
 * 2. 仅在 Photon 存在时加载集成代码
 * 3. 不影响没有 Photon 的用户
 * 4. 使用 Mixin 扩展 Photon 编辑器功能
 *
 * 工作流程：
 * 1. 用户在 Photon 编辑器中点击 "Import FBX Mesh"
 * 2. 选择 FBX 文件
 * 3. AssimpLoader 加载并解析 FBX
 * 4. 转换器将 Assimp 数据转换为 Photon 格式
 * 5. 数据添加到 Photon 资源管理器
 * 6. 用户可以在粒子发射器中使用导入的资源
 */
public class package-info {
}
