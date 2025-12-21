package cn.minerealms.iic.mixin.client;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin to modify F3 Debug Screen information.
 *
 * <p>This mixin intercepts the right-side debug info (CPU/GPU lines) and allows custom modifications.
 */
@OnlyIn(Dist.CLIENT)
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    /**
     * 注入到 getSystemInformation() 方法，修改右侧调试信息（包括CPU和GPU行）
     *
     * <p>在方法返回前修改List中的字符串内容
     */
    @Inject(
        method = "getSystemInformation",
        at = @At("RETURN"),
        cancellable = false
    )
    private void iic$modifySystemInformation(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = cir.getReturnValue();

        // 遍历所有行，找到CPU和GPU相关的行并修改
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // 修改CPU行（格式：CPU: 24x Intel Core i9-13900K @ 5.5GHz）
            if (line.contains("CPU:")) {
                lines.set(i, modifyCpuLine(line));
            }

            // 修改GPU第一行（格式：Display: 1920x1080 (24:9) NVIDIA Corporation）
            if (line.contains("Display:") && line.contains("x") && (line.contains("NVIDIA") || line.contains("AMD") || line.contains("Intel"))) {
                lines.set(i, modifyGpuDisplayLine(line));

                // 修改GPU第二行（显卡型号，紧跟在Display行后面）
                if (i + 1 < lines.size()) {
                    lines.set(i + 1, modifyGpuModelLine(lines.get(i + 1)));
                }

                // 修改GPU第三行（OpenGL版本和驱动，再往后一行）
                if (i + 2 < lines.size()) {
                    String versionLine = lines.get(i + 2);
                    // 确保是OpenGL版本行（通常以数字开头，如 "4.6.0"）
                    if (versionLine.matches("^\\d+\\.\\d+.*")) {
                        lines.set(i + 2, modifyGpuVersionLine(versionLine));
                    }
                }
            }
        }
    }

    /**
     * 修改CPU行的内容
     * 格式：CPU: 112x Intel Xeon Platinum 8480+ @ 3.8GHz
     *
     * @param originalLine 原始的CPU信息行
     * @return 修改后的CPU信息行
     */
    private String modifyCpuLine(String originalLine) {
        // Intel Xeon Platinum 8480+: 56核112线程，基础频率2.0GHz，最大睿频3.8GHz
        return "CPU: 112x Intel(R) Xeon(R) Platinum 8480+ @ 3.8GHz";
    }

    /**
     * 修改GPU Display行的内容（第一行）
     * 格式：Display: 1920x1080 (24:9) NVIDIA Corporation
     * 保留原始分辨率和比例，只修改厂商名称
     *
     * @param originalLine 原始的Display信息行
     * @return 修改后的Display信息行
     */
    private String modifyGpuDisplayLine(String originalLine) {
        // 提取分辨率部分（保留原始分辨率）
        // 原格式示例：Display: 1920x1080 (24:9) NVIDIA Corporation
        String resolution = originalLine.substring(originalLine.indexOf("Display:") + 8).trim();
        int corpIndex = resolution.indexOf("Corporation");
        if (corpIndex == -1) {
            corpIndex = resolution.indexOf("AMD");
            if (corpIndex == -1) {
                corpIndex = resolution.indexOf("Intel");
            }
        }

        if (corpIndex > 0) {
            resolution = resolution.substring(0, corpIndex).trim();
        }

        return "Display: " + resolution + " NVIDIA Corporation";
    }

    /**
     * 修改GPU型号行的内容（第二行）
     * 格式：NVIDIA RTX PRO 6000/PCIe/GB100
     *
     * @param originalLine 原始的GPU型号信息行
     * @return 修改后的GPU型号信息行
     */
    private String modifyGpuModelLine(String originalLine) {
        // NVIDIA RTX PRO 6000 (Blackwell GB100架构)
        return "NVIDIA RTX PRO 6000/PCIe/GB100";
    }

    /**
     * 修改GPU版本/驱动行的内容（第三行）
     * 格式：4.6.0 NVIDIA 575.06
     * 保持OpenGL版本4.6.0不变，只修改驱动版本
     *
     * @param originalLine 原始的GPU版本信息行
     * @return 修改后的GPU版本信息行
     */
    private String modifyGpuVersionLine(String originalLine) {
        // OpenGL 4.6.0 + NVIDIA Blackwell专业卡Studio驱动575.06
        return "4.6.0 NVIDIA 575.06";
    }
}
