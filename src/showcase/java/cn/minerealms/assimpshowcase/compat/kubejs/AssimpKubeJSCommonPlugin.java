package cn.minerealms.assimpshowcase.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Server plugin deliberately has no references to Minecraft client classes. */
public final class AssimpKubeJSCommonPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if(event.getType()==ScriptType.SERVER) event.add("AssimpServer",new AssimpServerApi());
    }
}
