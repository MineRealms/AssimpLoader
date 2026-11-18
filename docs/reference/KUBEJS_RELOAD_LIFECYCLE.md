# KubeJS 重载与实例生命周期

通用模型控制通过 AssimpLoader 的可选 `Assimp` 客户端绑定提供。完整使用方式见 [KubeJS 动画教程](../guides/KUBEJS_ANIMATION_GUIDE.md)。

## 分开重载的内容

| 内容 | 操作 |
| --- | --- |
| KubeJS 全局客户端脚本 | `/kubejs reload client_scripts` |
| 资源包模型、贴图与 JSON 资源 | `F3+T`；等待导入完成后重新取得实例 handle |
| 已编译的 processor JSON | 资源刷新后显式 `reloadProcessor(id, resource)`，或重建实例 |
| 外置模型覆盖 | 配置目录后 `/assimploader reload`，见[加载生命周期](LOADING_FIXES.md) |
| Java / Mixin | 重新构建并重启客户端 |

同一实例上重复 `processor(id, 同一资源)` 命中缓存，不等价于强制读取文件。实例重建后旧 handle 不可继续使用。

## RigWorks 控制器事务

玩家控制器的注册、失败重载保留、原版回退、按玩家状态和共享动作包属于独立 RigWorks。其固定版本 KubeJS Mixin、真实 Rhino 闭包验证及边界在 [RigWorks 重载机制](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/KUBEJS_RELOAD_LIFECYCLE.md)。

AssimpLoader 不承诺回滚任意 KubeJS 全局变量、其他模组事件或失败脚本已经产生的外部副作用；不能把角色控制器事务的能力套用到整个 KubeJS 环境。
