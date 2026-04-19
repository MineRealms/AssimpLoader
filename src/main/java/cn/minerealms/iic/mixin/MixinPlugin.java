package cn.minerealms.iic.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin Plugin for conditional loading of mixins based on mod presence.
 *
 * <p>This plugin checks if optional mods are loaded before applying their mixins:
 * <ul>
 *   <li>EnhancedVisuals - Pollution visual effects integration</li>
 * </ul>
 */
public class MixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("IIC-MixinPlugin");
    private static final String ENHANCED_VISUALS_CLASS = "team.creative.enhancedvisuals.client.VisualManager";

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[IIC-MixinPlugin] Loading mixin plugin for package: {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Check if EnhancedVisuals mixins should be applied
        if (mixinClassName.contains(".enhancedvisuals.")) {
            boolean present = isClassPresent(ENHANCED_VISUALS_CLASS);
            LOGGER.info("[IIC-MixinPlugin] Checking EnhancedVisuals mixin: {} -> Target: {} -> Present: {}",
                mixinClassName, targetClassName, present);
            return present;
        }

        // Apply all other mixins by default
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // Not used
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains(".enhancedvisuals.")) {
            LOGGER.info("[IIC-MixinPlugin] Applying EnhancedVisuals mixin: {} to {}", mixinClassName, targetClassName);
            LOGGER.info("[IIC-MixinPlugin] Target class methods:");
            targetClass.methods.forEach(method -> {
                LOGGER.info("[IIC-MixinPlugin]   - {} {}", method.name, method.desc);
            });
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains(".enhancedvisuals.")) {
            LOGGER.info("[IIC-MixinPlugin] Successfully applied EnhancedVisuals mixin: {} to {}", mixinClassName, targetClassName);
            // Mark as loaded in tracker
            try {
                Class<?> trackerClass = Class.forName("cn.minerealms.iic.util.MixinLoadTracker");
                java.lang.reflect.Method markLoaded = trackerClass.getMethod("markLoaded", String.class);
                String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
                markLoaded.invoke(null, simpleName);
                LOGGER.info("[IIC-MixinPlugin] Marked {} as LOADED in tracker", simpleName);
            } catch (Exception e) {
                LOGGER.error("[IIC-MixinPlugin] Failed to mark mixin as loaded", e);
            }
        }
    }

    /**
     * Check if a class is present in the classpath.
     *
     * @param className fully qualified class name
     * @return true if class exists
     */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, MixinPlugin.class.getClassLoader());
            LOGGER.info("[IIC-MixinPlugin] Class found: {}", className);
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.warn("[IIC-MixinPlugin] Class not found: {}", className);
            return false;
        }
    }
}
