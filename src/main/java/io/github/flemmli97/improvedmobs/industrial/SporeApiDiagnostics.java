package io.github.flemmli97.improvedmobs.industrial;

import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

/**
 * Spore API 诊断工具
 * 用于检测 Spore mod 的实际 API 方法
 */
public class SporeApiDiagnostics {

    /**
     * 诊断 Spore API 并输出所有可用方法
     */
    public static void diagnoseSporeApi(ServerLevel level) {
        if (!SporeIntegration.isSporeLoaded()) {
            System.out.println("[ImprovedMobs] Spore mod not loaded, skipping diagnostics");
            return;
        }

        System.out.println("=== Spore API Diagnostics ===");

        try {
            // 1. 获取 SporeSavedData 类
            Class<?> sporeSavedDataClass = Class.forName("com.Harbinger.Spore.ExtremelySusThings.SporeSavedData");
            System.out.println("[OK] Found SporeSavedData class: " + sporeSavedDataClass.getName());

            // 2. 获取数据实例
            Method getMethod = sporeSavedDataClass.getMethod("get", ServerLevel.class);
            Object data = getMethod.invoke(null, level);

            if (data == null) {
                System.out.println("[WARN] SporeSavedData.get() returned null - data not initialized yet?");
                System.out.println("       This is normal for new worlds or if Spore hasn't started yet");
                return;
            }

            System.out.println("[OK] Got SporeSavedData instance: " + data.getClass().getName());

            // 3. 列出所有公共方法
            System.out.println("\n=== Available Methods ===");
            Method[] methods = data.getClass().getMethods();
            for (Method method : methods) {
                // 只显示 Spore 相关的方法（排除 Object 的方法）
                if (method.getDeclaringClass() != Object.class &&
                    !method.getName().startsWith("lambda$") &&
                    method.getParameterCount() == 0) {

                    try {
                        Object result = method.invoke(data);
                        System.out.printf("  %s() -> %s (type: %s)%n",
                                method.getName(),
                                result,
                                result != null ? result.getClass().getSimpleName() : "null");
                    } catch (Exception e) {
                        System.out.printf("  %s() -> [Error: %s]%n",
                                method.getName(),
                                e.getMessage());
                    }
                }
            }

            // 4. 测试 Proto 实体方法
            System.out.println("\n=== Testing Proto Entity Methods ===");
            if (data != null) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> hiveminds = (java.util.List<Object>) data.getClass().getMethod("getHiveminds").invoke(data);

                    if (hiveminds != null && !hiveminds.isEmpty()) {
                        System.out.println("  Found " + hiveminds.size() + " Hiveminds, testing first one:");
                        Object firstProto = hiveminds.get(0);

                        testProtoMethod(firstProto, "getBiomass");
                        testProtoMethod(firstProto, "getHosts");
                        testProtoMethod(firstProto, "getChunkId");
                        testProtoMethod(firstProto, "getEmerge_tick");

                        // 测试位置
                        if (firstProto instanceof net.minecraft.world.entity.Entity entity) {
                            System.out.printf("  ✓ Position: %s%n", entity.blockPosition());
                        }
                    } else {
                        System.out.println("  No Hiveminds found in world");
                    }
                } catch (Exception e) {
                    System.out.println("  Error testing Proto methods: " + e.getMessage());
                }
            }

            // 5. 计算总数据
            System.out.println("\n=== Calculated Total Data ===");
            try {
                int totalBiomass = SporeIntegration.getTotalBiomass(level);
                int totalHosts = SporeIntegration.getTotalHosts(level);
                float intensity = SporeIntegration.calculateInfectionIntensity(level);

                System.out.printf("  Total Biomass: %d%n", totalBiomass);
                System.out.printf("  Total Hosts: %d%n", totalHosts);
                System.out.printf("  Infection Intensity: %.1f%%%n", intensity);
            } catch (Exception e) {
                System.out.println("  Error calculating totals: " + e.getMessage());
            }

        } catch (ClassNotFoundException e) {
            System.out.println("[ERROR] SporeSavedData class not found: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            System.out.println("[ERROR] get() method not found: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[ERROR] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== End of Diagnostics ===\n");
    }

    /**
     * 测试 Proto 实体方法
     */
    private static void testProtoMethod(Object proto, String methodName) {
        try {
            Method method = proto.getClass().getMethod(methodName);
            Object result = method.invoke(proto);
            System.out.printf("  ✓ %s() = %s%n", methodName, result);
        } catch (NoSuchMethodException e) {
            System.out.printf("  ✗ %s() - Method not found%n", methodName);
        } catch (Exception e) {
            System.out.printf("  ✗ %s() - Error: %s%n", methodName, e.getMessage());
        }
    }
}
