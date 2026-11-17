package cn.minerealms.assimploader.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Development source set only; never packaged with the engine. */
public final class AcceptanceKubeJSPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if(event.getType()==ScriptType.CLIENT && Boolean.getBoolean("assimp.kubejsSmoke"))
            event.add("AssimpAcceptance",new KubeJSSmokeTest.ProbeApi());
    }
}
