package cn.minerealms.rigworks.compat;

import cn.minerealms.rigworks.compat.kubejs.CharacterScriptApi;
import cn.minerealms.rigworks.character.client.CharacterScriptRuntime;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** The player scripting binding lives in this optional mod, independently of the engine API. */
public final class RigWorksKubeJSPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.CLIENT) {
            CharacterScriptRuntime.prepareClientBindings();
            event.add("RigWorks", CharacterScriptApi.INSTANCE);
        }
    }
}
