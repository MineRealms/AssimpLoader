# EnhancedVisuals 模组联动指南

## 1. 直接 API 调用

### 1.1 获取 Handler 实例

```java
import team.creative.enhancedvisuals.common.handler.VisualHandlers;

// 获取指定 Handler
DamageHandler damageHandler = VisualHandlers.DAMAGE;
ExplosionHandler explosionHandler = VisualHandlers.EXPLOSION;
PotionHandler potionHandler = VisualHandlers.POTION;
SplashHandler splashHandler = VisualHandlers.SPLASH;
```

**所有可用的 Handler:**
- `VisualHandlers.EXPLOSION` - 爆炸效果
- `VisualHandlers.POTION` - 药水效果
- `VisualHandlers.SAND` - 沙子飞溅
- `VisualHandlers.SPLASH` - 入水效果
- `VisualHandlers.DAMAGE` - 伤害效果
- `VisualHandlers.SLENDER` - 瘦魔怪效果
- `VisualHandlers.SATURATION` - 饱食度效果
- `VisualHandlers.HEARTBEAT` - 心跳效果
- `VisualHandlers.UNDERWATER` - 水下效果
- `VisualHandlers.RAIN` - 雨天效果
- `VisualHandlers.HEALTH` - 生命值效果

### 1.2 调用 Handler 方法

```java
// DamageHandler 专用方法
damageHandler.clientHurt(); // 触发受伤视觉效果

// 通过 DamagePacket 触发
damageHandler.playerDamaged(player, damagePacket);
```

### 1.3 通过 VisualRegistry 获取

```java
import team.creative.enhancedvisuals.common.visual.VisualRegistry;
import team.creative.enhancedvisuals.api.VisualHandler;
import net.minecraft.resources.ResourceLocation;
import java.util.Map.Entry;

// 遍历所有已注册的 Handler
for (Entry<ResourceLocation, VisualHandler> entry : VisualRegistry.entrySet()) {
    ResourceLocation id = entry.getKey();
    VisualHandler handler = entry.getValue();
}

// 按名称获取指定 Handler
public static VisualHandler getHandler(String modid, String name) {
    ResourceLocation id = new ResourceLocation(modid, name);
    for (Entry<ResourceLocation, VisualHandler> entry : VisualRegistry.entrySet()) {
        if (entry.getKey().equals(id)) {
            return entry.getValue();
        }
    }
    return null;
}
```

---

## 2. 反射获取（跨模组联动）

### 2.1 反射获取 Handler 字段

```java
import java.lang.reflect.Field;

public static VisualHandler getHandlerByReflection(String handlerName) throws Exception {
    Class<?> handlersClass = Class.forName("team.creative.enhancedvisuals.common.handler.VisualHandlers");
    Field field = handlersClass.getField(handlerName);
    return (VisualHandler) field.get(null);
}

// 使用示例
VisualHandler damageHandler = getHandlerByReflection("DAMAGE");
```

### 2.2 反射获取 VisualRegistry

```java
import java.lang.reflect.Method;

public static Collection<VisualHandler> getAllHandlers() throws Exception {
    Class<?> registryClass = Class.forName("team.creative.enhancedvisuals.common.visual.VisualRegistry");
    Method handlersMethod = registryClass.getMethod("handlers");
    return (Collection<VisualHandler>) handlersMethod.invoke(null);
}
```

### 2.3 反射调用 VisualManager

```java
import team.creative.enhancedvisuals.client.VisualManager;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.creativecore.common.config.premade.IntMinMax;

// 添加视觉淡出效果
VisualManager.addVisualFadeOut(visualType, handler, new IntMinMax(10, 20));

// 添加粒子效果
VisualManager.addParticlesFadeOut(visualType, handler, 5, new IntMinMax(10, 20), true, null);

// 清除所有效果
VisualManager.clearEverything();
```

---

## 3. Forge Event 事件联动

### 3.1 发送自定义事件

```java
import team.creative.enhancedvisuals.api.event.SplashEvent;
import team.creative.creativecore.CreativeCore;

// 触发入水事件
SplashEvent event = new SplashEvent(player);
CreativeCore.loader().postForge(event);
```

### 3.2 监听 Forge 事件

```java
import net.minecraftforge.eventbus.api.Subscribe;
import net.minecraftforge.event.TickEvent;

// 在 FMLCommonSetupEvent 中注册
@Subscribe
public void onClientTick(TickEvent.ClientTickEvent event) {
    // 处理每 tick 逻辑
}
```

---

## 4. 触发伤害效果示例

```java
package com.example.mod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import team.creative.enhancedvisuals.common.handler.VisualHandlers;
import team.creative.enhancedvisuals.common.packet.DamagePacket;

public class EVIntegration {

    public static void triggerDamageVisual(Player player, DamageSource source, float damage) {
        DamagePacket packet = new DamagePacket(player, source, damage);
        VisualHandlers.DAMAGE.playerDamaged(player, packet);
    }

    public static void triggerHurtVisual(Player player) {
        VisualHandlers.DAMAGE.clientHurt();
    }
}
```

---

## 5. VisualType 创建自定义效果

```java
import team.creative.enhancedvisuals.api.type.VisualTypeOverlay;
import team.creative.enhancedvisuals.api.type.VisualTypeParticle;
import team.creative.creativecore.common.util.type.Color;

// 创建 Overlay 效果（屏幕叠加层）
VisualTypeOverlay effect = new VisualTypeOverlay("effect_name");

// 创建粒子效果
VisualTypeParticle particle = new VisualTypeParticle("particle_name");

// 带颜色的粒子效果
VisualTypeParticleColored colored = new VisualTypeParticleColored("name", new Color(255, 0, 0));

// 添加到 Handler 并触发
VisualManager.addVisualFadeOut(effect, handler, new IntMinMax(10, 30));
```

---

## 6. 配置检查

```java
// 检查 Handler 是否启用
if (handler.isEnabled(player)) {
    // 执行效果
}

// 获取配置的值
boolean enabled = handler.enabled;
float opacity = handler.opacity;
```

---

## 7. 安全检查

```java
// 检查模组是否加载
boolean isLoaded = CreativeCore.loader().isModLoaded("enhancedvisuals");

// 建议在调用前进行检查
if (isLoaded) {
    // 进行联动
}
```