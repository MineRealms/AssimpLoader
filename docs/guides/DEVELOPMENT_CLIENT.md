# AssimpLoader 开发客户端

根工程 `runClient` 加载引擎与独立的 AssimpShowcase 源码，不加载已迁走的 RigWorks。玩家角色开发请使用 [RigWorks 独立开发指南](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/DEVELOPMENT.md)。

## 启动和目录

```powershell
.\gradlew.bat runClient -PwithKubeJS --console=plain
```

工作目录默认 `run/`，可以用 `-PclientGameDir=<目录>` 选择隔离的测试实例。`-PclientWorld=<世界名>` 与 `-PclientServer=<地址>` 使用 Quick Play。多个客户端必须使用不同目录、存档和调试端口。

## 可选开发依赖

| 参数或依赖 | 行为 |
| --- | --- |
| Oculus / Embeddium | 开发客户端默认加入；`-PwithoutOculus` 关闭 |
| `-PwithKubeJS` | 加载固定版本 KubeJS、Rhino、Architectury |
| ModernUI | Maven 固定版本；Forge 模组经 `fg.deobf`，Core/Markdown 由 Minecraft library 配置提供 |
| `-Pf3TweakerJar=<绝对JAR路径>` | 本地 Maven 坐标 + `fg.deobf`，不使用不能正确重映射的裸文件依赖 |
| `-ProsettaRepo=<Maven仓库>` | 加载调试桥；监听本机，端口可用 `-ProsettaPort=<端口>` 指定 |
| `-PwithoutOptionalMods` | 禁止把可选 Photon / LDLib 放入开发运行时 |

本地路径可以通过命令行或用户 Gradle 配置提供，不写入仓库。F3Tweaker 的 JAR 应有 Minecraft 1.20.1 pack format 15 的根 `pack.mcmeta`，否则 Forge 会给出资源包元信息警告。

## 编译和运行入口检查

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat prepareRunClient --console=plain
```

第一条包含测试、集成测试源码编译和引擎/Showcase 产物检查；第二条准备开发运行数据但不启动新游戏。不需要为构建关闭已有客户端。

显式客户端测试包括 `clientKubeJsSmoke`、`clientBatchSmoke`、`clientHighBoneSmoke`、`clientComputeSmoke`、`clientRuntimeSmoke`、`clientWorkbenchSmoke` 等。各入口的属性和是否移动玩家/关闭测试客户端，须读取 `src/integration` 对应实现；使用复制的世界，不直接运行在正式存档。

KubeJS 实际操作见[动画报告与 Demo](KUBEJS_ANIMATION_GUIDE.md)，PBR 验证见[材质指南](OCULUS_PBR_USAGE.md)。
