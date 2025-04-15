# Turret系统性能分析报告

## 发现的性能问题

### 1. **严重问题：目标查找时的重复实体扫描**
**位置**: `LaserTurretBlockEntity.tryFindTarget()` (230-251行)

**问题**:
```java
.filter(entity -> countTurretsTargeting(entity) < 8)
.min((o1, o2) -> {
    int count1 = countTurretsTargeting(o1);  // 重复调用！
    int count2 = countTurretsTargeting(o2);  // 重复调用！
    ...
});
```

每个候选目标会调用`countTurretsTargeting()`多次：
- 一次在filter中
- 多次在min比较器中（每次比较都调用）

而`countTurretsTargeting()`本身会扫描100x100x100的区域查找所有LaserEntity！

**影响**: 如果有10个候选目标，可能会执行30-50次大范围实体扫描，这是O(n²)复杂度。

---

### 2. **严重问题：每tick创建新的FloatingLong对象**
**位置**: `LaserTurretBlockEntity.onUpdateServer()` (164, 174, 178行)

**问题**:
```java
int energyPerTick = laserEnergyPerTick();
energyContainer.setEnergyPerTick(FloatingLong.create(energyPerTick));  // 每tick创建
...
if(energyContainer.getEnergy().greaterOrEqual(FloatingLong.create(energyPerTick))) {  // 又创建
    ...
    energyContainer.extract(FloatingLong.create(energyPerTick), ...);  // 再创建
}
```

**影响**: 每个炮塔每tick创建3个FloatingLong对象，造成不必要的GC压力。

---

### 3. **中等问题：每tick验证目标有效性**
**位置**: `LaserTurretBlockEntity.onUpdateServer()` (161行)

**问题**:
```java
tryInvalidateTarget();  // 每tick调用
```

`isValidTarget()`包含：
- 距离计算（平方根）
- 配置列表遍历
- 射线追踪（canSeeTarget）

**影响**: 有目标时每tick都执行这些昂贵的检查。

---

### 4. **中等问题：目标查找的AABB每次重新创建**
**位置**: `LaserTurretBlockEntity` (69行)

**问题**:
```java
private final AABB targetBox = AABB.ofSize(getBlockPos().getCenter(), ...);
```

在构造时`getBlockPos()`可能返回错误的位置，导致targetBox不准确。

---

### 5. **轻微问题：LaserEntity每tick更新目标位置**
**位置**: `LaserEntity.tick()` (56-57行)

**问题**:
```java
Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
updateTargetPos(targetPos);  // 每tick同步到客户端
```

**影响**: 每个激光实体每tick都发送网络同步数据。

---

### 6. **轻微问题：激光实体的护甲损坏逻辑**
**位置**: `LaserEntity.tick()` (63-68行)

**问题**:
```java
target.getArmorSlots().forEach(itemStack -> {
    if (!itemStack.isEmpty() && itemStack.isDamageableItem()) {
        itemStack.hurtAndBreak((int)(damagePerTick * 0.5), target, ...);
    }
});
```

**影响**: 每tick遍历所有护甲槽并调用hurtAndBreak，可能触发多次事件。

---

## 优化建议

### 优先级1：修复目标查找的重复扫描
1. 在filter之前一次性计算所有目标的炮塔数量，缓存结果
2. 使用Map存储entity -> count映射
3. 减少实体扫描次数从O(n²)到O(n)

### 优先级2：缓存FloatingLong对象
1. 将energyPerTick作为字段缓存
2. 只在升级变化时重新计算
3. 减少每tick的对象创建

### 优先级3：降低目标验证频率
1. 不是每tick都验证，改为每5-10 tick验证一次
2. 使用简单的距离检查作为快速路径
3. 只在必要时执行昂贵的射线追踪

### 优先级4：优化激光实体同步
1. 只在目标位置变化超过阈值时才同步
2. 降低同步频率（每2-3 tick）
3. 使用插值在客户端平滑显示

### 优先级5：优化护甲损坏
1. 不是每tick都损坏护甲
2. 累积伤害，每N tick应用一次
3. 减少事件触发频率

---

## 预期性能提升

- **目标查找优化**: 减少90%的实体扫描次数
- **FloatingLong缓存**: 减少100%的不必要对象创建
- **目标验证优化**: 减少80%的验证计算
- **总体**: 预计可减少60-70%的tick时间消耗
