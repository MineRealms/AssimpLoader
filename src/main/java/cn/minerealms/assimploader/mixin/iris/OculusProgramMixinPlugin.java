package cn.minerealms.assimploader.mixin.iris;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Never resolve optional Oculus classes on a vanilla or dedicated-server installation. */
public final class OculusProgramMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var mods = LoadingModList.get();
        var oculus = mods == null ? null : mods.getModFileById("oculus");
        return oculus != null && oculus.getMods().stream().anyMatch(mod -> mod.getModId().equals("oculus")
                && mod.getVersion().toString().equals("1.8.0"))
                && targetClassName.equals("net.irisshaders.iris.gl.program.Program");
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
