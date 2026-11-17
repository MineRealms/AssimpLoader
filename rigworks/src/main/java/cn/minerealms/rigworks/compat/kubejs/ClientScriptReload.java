package cn.minerealms.rigworks.compat.kubejs;

import cn.minerealms.rigworks.character.client.CharacterScriptRuntime;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Only the RigWorks registration registry is transactional; other script side effects remain KubeJS-owned. */
public final class ClientScriptReload {
    private ClientScriptReload() {}

    public static void reload(ScriptManager manager, ResourceManager resources) {
        if(manager.scriptType!=ScriptType.CLIENT){manager.reload(resources);return;}
        try(var registration=CharacterScriptRuntime.beginRegistrationReload()) {
            manager.reload(resources);
            registration.complete();
        }
    }
}
