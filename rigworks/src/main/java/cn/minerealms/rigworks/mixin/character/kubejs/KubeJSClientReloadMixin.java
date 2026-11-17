package cn.minerealms.rigworks.mixin.character.kubejs;

import cn.minerealms.rigworks.compat.kubejs.ClientScriptReload;
import dev.latvian.mods.kubejs.script.ScriptManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Pinned KubeJS standard initial/client-command reload entry; wrapper covers preload and thrown errors. */
@Pseudo
@Mixin(targets="dev.latvian.mods.kubejs.client.KubeJSClient",remap=false)
public abstract class KubeJSClientReloadMixin {
    @Redirect(method="reloadClientScripts",at=@At(value="INVOKE",target="Ldev/latvian/mods/kubejs/script/ScriptManager;reload(Lnet/minecraft/server/packs/resources/ResourceManager;)V"),remap=false)
    private static void rigworks$transactionalReload(ScriptManager manager,ResourceManager resources){ClientScriptReload.reload(manager,resources);}
}
