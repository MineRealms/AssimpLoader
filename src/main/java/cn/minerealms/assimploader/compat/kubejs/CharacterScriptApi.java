package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.character.client.CharacterScriptRuntime;
import cn.minerealms.assimploader.character.client.LocalPlayerCharacter;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Optional client policy API; registration never enables the player renderer implicitly. */
public final class CharacterScriptApi {
    static final CharacterScriptApi INSTANCE = new CharacterScriptApi();
    private CharacterScriptApi() {}

    public int apiVersion() {return 1;}
    public CharacterScriptApi register(String id, Consumer<CharacterActionFrame> controller) {
        checkThread();CharacterScriptRuntime.register(id,controller);return this;
    }
    public CharacterScriptApi unregister(String id) {checkThread();CharacterScriptRuntime.unregister(id);return this;}
    public CharacterScriptApi clear() {checkThread();CharacterScriptRuntime.clear();return this;}
    public CharacterScriptApi use(String id) {checkThread();CharacterScriptRuntime.use(id);return this;}
    public CharacterScriptApi useDefault() {checkThread();CharacterScriptRuntime.useDefault();return this;}
    public List<String> controllers() {checkThread();return CharacterScriptRuntime.controllers();}
    public String active() {checkThread();return CharacterScriptRuntime.active();}
    public String failure() {checkThread();return CharacterScriptRuntime.failure();}
    public CharacterScriptApi enable(boolean enabled) {
        checkThread();var s=LocalPlayerCharacter.settings();
        LocalPlayerCharacter.configure(new LocalPlayerCharacter.Settings(enabled,s.profile(),s.groundPlacement(),s.effects()));return this;
    }
    public CharacterScriptApi profile(String resource) {
        checkThread();var s=LocalPlayerCharacter.settings();
        LocalPlayerCharacter.configure(new LocalPlayerCharacter.Settings(s.enabled(),resource,s.groundPlacement(),s.effects()));return this;
    }
    public CharacterScriptApi reload() {checkThread();LocalPlayerCharacter.reload();return this;}
    public Map<String,Object> status() {
        checkThread();var d=LocalPlayerCharacter.diagnostics();var s=LocalPlayerCharacter.settings();
        return Map.of("enabled",s.enabled(),"profile",s.profile(),"status",d.status().name(),
                "model",d.model(),"failure",d.failure(),"controller",active()==null?"":active(),
                "scriptFailure",failure());
    }
    static void checkThread() {
        if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Character scripts require the client thread");
    }
}
