package cn.minerealms.assimploader.mixin.photon;

import cn.minerealms.assimploader.compat.OptionalIntegrationGuards;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Missing optional jars are rejected before Mixin resolves handler signatures or target classes. */
public final class PhotonMixinPlugin implements IMixinConfigPlugin {
    private final Map<String, Boolean> presence = new HashMap<>();

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return OptionalIntegrationGuards.shouldApplyPhotonMixin(targetClassName, mixinClassName, name ->
                presence.computeIfAbsent(name, key -> OptionalIntegrationGuards.classResourcePresent(getClass().getClassLoader(), key)));
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
