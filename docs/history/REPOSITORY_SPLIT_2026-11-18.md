# RigWorks 独立仓库拆分

2026-11-18，玩家模型应用迁往 [MineRealms/RigWorks](https://github.com/MineRealms/RigWorks)。引擎仓库保留全部旧历史，以普通新提交移除原 `rigworks/` 子项目。

| 仓库 | 负责内容 |
| --- | --- |
| AssimpLoader | 通用导入、材质、动画、IK、物理、GPU 渲染、模型工作台基础及独立 Showcase 示例 |
| RigWorks | 玩家渲染、输入到动作映射、装备编辑、角色配置、脚本策略和配置分发 |

RigWorks 在独立副本中提取 147 条相关及基础提交，保留旧包路径，核对作者/提交者与两种时间戳；原模块 136 个文件的提取 blob 一致。详见 [RigWorks 提取报告](https://github.com/MineRealms/RigWorks/blob/main/docs/history/REPOSITORY_EXTRACTION.md)。

RigWorks 通过固定提交的 `AssimpLoader/` Git 子模块依赖引擎，Gradle 源码依赖方向仅为 RigWorks → AssimpLoader。引擎本身不需要初始化 RigWorks，也不依赖其源码或测试资产。

## 文档与清理

当前指南放 `docs/guides/`，技术契约放 `docs/reference/`，示例放 `docs/showcase/`，路线图放 `docs/roadmap/`，过程报告移到 `docs/history/`。原 `docs/archive/` 继续保留。角色专属指南、截图、分发数据和建模测试件迁至 RigWorks。

根目录中旧游戏启动器、硬编码部署脚本、日志、快捷方式和生成的 bytecode/mapping 不再作为项目文件分发。本地旧启动器与日志保存到被忽略的 `build/local-archive/root-cleanup-2026-11-18/`；现有客户端没有被关闭，也没有删除其运行输出或世界。

## 验证

独立引擎 `gradlew.bat build` 已通过，包含单元测试、集成测试源码编译和引擎/Showcase JAR 边界校验。RigWorks 独立根工程 `gradlew.bat build` 已通过，对应角色测试和 JAR 边界也通过。源码运行配置另通过 `prepareRunClient` 检查，不以编译通过代替新客户端画面验收。
