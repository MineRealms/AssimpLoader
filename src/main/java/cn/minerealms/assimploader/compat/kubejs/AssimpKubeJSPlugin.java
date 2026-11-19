package cn.minerealms.assimploader.compat.kubejs;

//? if forge {
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
//?}
//? if neoforge {
/*
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
*///?}

/** Loaded by KubeJS only when it is installed, and only on the physical client. */
//? if forge {
public final class AssimpKubeJSPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if(event.getType()==ScriptType.CLIENT) {
            // Bindings are rebuilt before each CLIENT script load, including /kubejs reload client_scripts.
            cn.minerealms.assimploader.render.ScriptShaderParameters.requestReset();
            event.add("Assimp",new AssimpScriptApi());
        }
    }
}
//?}
//? if neoforge {
/*
// 2101 turned KubeJSPlugin into an interface and replaced BindingsEvent with the BindingRegistry
// record, so the same registration lands in a different shape.
public final class AssimpKubeJSPlugin implements KubeJSPlugin {
    @Override public void registerBindings(BindingRegistry registry) {
        if(registry.type()==ScriptType.CLIENT) {
            // Bindings are rebuilt before each CLIENT script load, including /kubejs reload client_scripts.
            cn.minerealms.assimploader.render.ScriptShaderParameters.requestReset();
            registry.add("Assimp",new AssimpScriptApi());
        }
    }
}
*///?}
