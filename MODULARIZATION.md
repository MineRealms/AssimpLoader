# ImprovedMobs 模块解耦分析

## 概述

目标：将 `industrial` 和 `mekanism_turrets` 从 improvedmobs 主项目中分离，形成独立 mod。

---

## 当前架构

```
ImprovedMobs (单一mod)
├── core/          - 核心逻辑 (AI, 难度系统等)
├── industrial/    - GTCEU + Spore 集成
├── mekanism_turrets/ - 激光炮塔
└── api/difficulty/ - 难度系统API (已解耦)
```

---

## 目标架构

```
新mod (如 improvedmobs-industrial)
├── 依赖: improvedmobs, gtceu, spore
├── 不依赖: mekanism_turrets
└── 打包时只包含industrial代码
```

---

## 解耦可行性分析

### 1. industrial 模块 ✅ 可完全解耦

**当前实现方式：**
- 通过 `DifficultyFetcher` 运行时注册 `DifficultyGetter` 实现
- 纯逻辑代码，无 mixin 修改
- 依赖 GTCEU 和 Spore 的类

**解耦方式：**
```java
// 在新mod中实现 DifficultyGetter
public class MyIndustrialDifficultyGetter implements DifficultyGetter {
    @Override
    public float getDifficulty(ServerLevel level, Vec3 pos) {
        // 你的难度计算逻辑
        return IndustrialDifficultyManager.getDifficultyFor(player);
    }

    @Override
    public Config.IntegrationType getType() {
        return Config.IntegrationType.ADD;
    }
}

// 在mod加载时注册
if (ModList.get().isLoaded("gtceu") && ModList.get().isLoaded("spore")) {
    DifficultyFetcher.add(
        new ResourceLocation("my_mod", "industrial"),
        new MyIndustrialDifficultyGetter()
    );
}
```

**优点：**
- 无需 mixin 注入
- 运行时动态检测 gtceu/spore 是否存在
- improvedmobs 核心会自动调用你的实现

**关键代码位置：**
- `api/difficulty/DifficultyFetcher.java` - 注册接口
- `api/difficulty/DifficultyGetter.java` - 实现接口
- `industrial/IndustrialDifficultyManager.java` - 难度管理

---

### 2. mekanism_turrets 模块 ✅ 可排除

**当前实现方式：**
- 在 `ImprovedMobsForge.java` 中直接注册方块、实体、物品
- 包含完整的激光炮塔功能（方块、实体、渲染、GUI）

**解耦方式：**
- 直接不编译这部分代码即可
- 打包时排除 `mekanism_turrets/` 目录

---

## Gradle 打包配置建议

在新 mod 的 `build.gradle` 中：

```groovy
// 排除 industrial 和 mekanism_turrets 源码
sourceSets {
    main {
        java {
            exclude 'io/github/flemmli97/improvedmobs/industrial/**'
            exclude 'io/github/flemmli97/improvedmobs/mekanism_turrets/**'
        }
    }
}

// 保留 api 包（需要 improvedmobs 核心）
implementation project(':improvedmobs')

// 依赖 gtceu 和 spore
implementation fg.deobf("com.gregtechceu.gtceu:gtceu-1.20.1:${gtceu_version}") { transitive = false }
implementation fg.deobf("curse.maven:spore-678295:7881767")
```

---

## 依赖关系

| 模块 | 当前依赖 | 解耦后依赖 | mixin 需求 |
|------|----------|------------|------------|
| industrial | improvedmobs | improvedmobs + gtceu + spore | ❌ 无 |
| mekanism_turrets | improvedmobs + mekanism | 无（排除） | 无 |

---

## 总结

1. **industrial 完全可解耦** - 通过 `DifficultyFetcher` 运行时注册，无需 mixin
2. **mekanism_turrets 可直接排除** - 不编译即可
3. **核心难度系统已有良好 API** - `DifficultyGetter` 接口设计合理
4. **运行时检测** - 使用 `ModList.get().isLoaded()` 判断 mod 是否存在

---

## 下一步

如需进一步实施，可以：
1. 创建新的 gradle module 或独立项目
2. 将 industrial 代码迁移到新项目
3. 配置依赖和打包排除规则
4. 测试运行时难度计算是否正常工作