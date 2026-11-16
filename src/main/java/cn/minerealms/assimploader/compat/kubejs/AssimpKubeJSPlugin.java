package cn.minerealms.assimploader.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Loaded by KubeJS only when it is installed, and only on the physical client. */
public final class AssimpKubeJSPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if(event.getType()==ScriptType.CLIENT) {
            event.add("Assimp",new AssimpScriptApi());
            if(Boolean.getBoolean("assimp.kubejsSmoke")) event.add("AssimpAcceptance",new KubeJSSmokeTest.ProbeApi());
        }
    }
}
