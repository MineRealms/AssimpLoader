# AssimpLoader

## Modules

- `assimploader`: client model/animation/rendering engine, with no gameplay server registrations.
- [`rigworks`](rigworks/README.md): independent Gradle subproject and client player Mod, depending on the engine.
- `assimpshowcase`: separate content Mod from `src/showcase`, including server entities, blocks and vehicle networking.

`gradlew.bat build` builds all three JARs. `gradlew.bat runClient -PwithKubeJS`
loads all three in development, including Oculus by default.
The dependency boundaries and JSON/KubeJS design are documented in
[the split report](docs/MODULE_SPLIT_AND_DATA_DRIVEN_PLAN_2026-11-17.md).

Minecraft Forge 1.20.1 的 Assimp 模型加载、动画渲染和 LookDev 预览工具。

项目文档统一位于 [`docs/`](docs/README.md)：

- [项目概览与构建](docs/PROJECT_OVERVIEW.md)
- [快速开始](docs/QUICK_START.md)
- [PBR 资产与 FBX 接入规范](docs/PBR_ASSET_SPEC.md)
- [Oculus PBR 使用与截图验收](docs/PBR_VALIDATION_2026-11-13.md)
- [审计与修复记录](docs/AUDIT_2026-11-13.md)
- [历史文档归档](docs/archive/README.md)

项目许可见 [LICENSE](LICENSE)，依赖归属见 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。
