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

/** Development source set only; never packaged with the engine. */
//? if forge {
public final class AcceptanceKubeJSPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if(event.getType()==ScriptType.CLIENT && Boolean.getBoolean("assimp.kubejsSmoke"))
            event.add("AssimpAcceptance",new KubeJSSmokeTest.ProbeApi());
    }
}
//?}
//? if neoforge {
/*
// 2101 turned KubeJSPlugin into an interface and replaced BindingsEvent with the BindingRegistry
// record, so the same registration lands in a different shape.
public final class AcceptanceKubeJSPlugin implements KubeJSPlugin {
    @Override public void registerBindings(BindingRegistry registry) {
        if(registry.type()==ScriptType.CLIENT && Boolean.getBoolean("assimp.kubejsSmoke"))
            registry.add("AssimpAcceptance",new KubeJSSmokeTest.ProbeApi());
    }
}
*///?}
