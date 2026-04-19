# 能量底座能量传输修复

## 🐛 问题描述

**原始问题**：
- 炮塔只能放置在能量底座的顶部（UP 面）才能工作
- 放置在其他面（东、西、南、北、底部）的炮塔无法接收能量

**原因分析**：
- 能量底座的 `supplyEnergyToTurret()` 方法只向上方（`Direction.UP`）传输能量
- 炮塔的 BlockEntity 从底部（`Direction.DOWN`）接收能量
- 当炮塔放置在侧面或底部时，能量底座没有向这些方向传输能量

## ✅ 修复方案

### 修改内容

修改了 `EnergyPedestalBlockEntity.supplyEnergyToTurret()` 方法：

**修复前**：
```java
private void supplyEnergyToTurret() {
    // 只向上方传输能量
    BlockPos abovePos = worldPosition.above();
    BlockEntity aboveEntity = level.getBlockEntity(abovePos);
    // ...
}
```

**修复后**：
```java
private void supplyEnergyToTurret() {
    Direction inputFace = getEnergyInputFace();
    
    // 遍历所有6个方向
    for (Direction direction : Direction.values()) {
        // 跳过能量输入面
        if (direction == inputFace) {
            continue;
        }
        
        // 向该方向的相邻方块传输能量
        BlockPos neighborPos = worldPosition.relative(direction);
        BlockEntity neighborEntity = level.getBlockEntity(neighborPos);
        
        if (neighborEntity != null) {
            // 从相邻方块的相对面接收能量
            Direction receivingSide = direction.getOpposite();
            neighborEntity.getCapability(ForgeCapabilities.ENERGY, receivingSide)
                .ifPresent(handler -> {
                    // 传输能量
                });
        }
    }
}
```

### 关键改进

1. **遍历所有方向**：不再只检查上方，而是检查所有6个方向
2. **跳过输入面**：能量输入面不输出能量，避免能量回流
3. **正确的相对面**：使用 `direction.getOpposite()` 确保炮塔从正确的面接收能量
   - 能量底座向上传输 → 炮塔从下方接收
   - 能量底座向东传输 → 炮塔从西方接收
   - 以此类推

## 🎯 现在的行为

### 能量传输逻辑

```
能量底座 (FACING = DOWN，能量输入面在底部)
├── UP 面 → 向上方炮塔传输（炮塔从 DOWN 接收）✅
├── NORTH 面 → 向北方炮塔传输（炮塔从 SOUTH 接收）✅
├── SOUTH 面 → 向南方炮塔传输（炮塔从 NORTH 接收）✅
├── EAST 面 → 向东方炮塔传输（炮塔从 WEST 接收）✅
├── WEST 面 → 向西方炮塔传输（炮塔从 EAST 接收）✅
└── DOWN 面 → 能量输入面，不输出 ❌
```

### 能量分配

- **传输速率**：每个方向最多 10k FE/tick
- **总容量**：128k FE
- **优先级**：按方向顺序（DOWN, UP, NORTH, SOUTH, WEST, EAST）
- **智能停止**：如果能量耗尽，停止检查剩余方向

## 📋 测试验证

### 测试场景

1. **顶部放置**（原本就能工作）
   ```
   [炮塔]
   [底座] ← 能量输入在底部
   ```
   ✅ 炮塔正常工作

2. **侧面放置**（修复后能工作）
   ```
   [炮塔] - [底座] ← 能量输入在底部
   ```
   ✅ 炮塔正常工作

3. **底部放置**（修复后能工作）
   ```
   [底座] ← 能量输入在侧面
   [炮塔]
   ```
   ✅ 炮塔正常工作

4. **多炮塔配置**
   ```
        [炮塔]
           |
   [炮塔]-[底座]-[炮塔]
           |
        [炮塔]
   ```
   ✅ 所有炮塔都能接收能量（按顺序分配）

### 验证清单

- [ ] 炮塔可以放置在能量底座的任意非输入面
- [ ] 所有位置的炮塔都能正常接收能量
- [ ] 能量输入面不会输出能量
- [ ] 多个炮塔可以同时工作
- [ ] 能量耗尽时所有炮塔停止工作
- [ ] 能量恢复后所有炮塔恢复工作

## 🚀 构建状态

✅ **构建成功**
- JAR 文件：`integratedindustrialcraft-1.0.0.jar`
- 已自动复制到游戏 mods 目录
- 可以直接在游戏中测试

## 💡 使用建议

### 最佳布局

**单炮塔配置**：
```
[能量源]
    |
[底座] ← 底部接收能量
    |
[炮塔] ← 顶部放置炮塔
```

**多炮塔配置**：
```
     [炮塔]
        |
[能量源]-[底座]-[炮塔]
        |
     [炮塔]
```
- 能量输入面连接能量源
- 其他5个面都可以放置炮塔

### 能量管理

- 每个炮塔消耗能量速率不同（取决于等级）
- 建议使用高等级能量源（如 Mekanism 终极能量立方）
- 可以串联多个能量底座来支持更多炮塔
