package cn.minerealms.assimploader.mixin.iris;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Never resolve optional Oculus/Iris classes on a vanilla or dedicated-server installation. */
public final class OculusProgramMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!targetClassName.equals("net.irisshaders.iris.gl.program.Program")) return false;
        var mods = LoadingModList.get();
        if (mods == null) return false;
        for (String id : List.of("oculus", "iris")) {
            var file = mods.getModFileById(id);
            if (file != null && file.getMods().stream().anyMatch(mod ->
                    supportsShaderMod(mod.getModId(), mod.getVersion().toString()))) return true;
        }
        return false;
    }

    static boolean supportsShaderMod(String id, String version) {
        return id.equals("oculus") && version.equals("1.8.0")
                || id.equals("iris") && (version.equals("1.8.12")
                || version.startsWith("1.8.12+") || version.startsWith("1.8.12-"));
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
