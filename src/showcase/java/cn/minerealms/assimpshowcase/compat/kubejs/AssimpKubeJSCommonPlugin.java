package cn.minerealms.assimpshowcase.compat.kubejs;

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

/** Server plugin deliberately has no references to Minecraft client classes. */
//? if forge {
public final class AssimpKubeJSCommonPlugin extends KubeJSPlugin {
    @Override public void registerBindings(BindingsEvent event) {
        if(event.getType()==ScriptType.SERVER) event.add("AssimpServer",new AssimpServerApi());
    }
}
//?}
//? if neoforge {
/*
// 2101 turned KubeJSPlugin into an interface and replaced BindingsEvent with the BindingRegistry
// record, so the same registration lands in a different shape.
public final class AssimpKubeJSCommonPlugin implements KubeJSPlugin {
    @Override public void registerBindings(BindingRegistry registry) {
        if(registry.type()==ScriptType.SERVER) registry.add("AssimpServer",new AssimpServerApi());
    }
}
*///?}
