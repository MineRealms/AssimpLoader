package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.character.core.*;
import cn.minerealms.rigworks.network.CharacterAppearanceNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Accepted server state stays separate from editor drafts; saved changes publish when uploads are enabled. */
@Mod.EventBusSubscriber(modid=RigWorks.MODID,value=Dist.CLIENT)
public final class CharacterVisualSharing {
    private record Accepted(CharacterAppearance appearance,long revision,CharacterVisualConfig config,String digest){}
    private static final Map<UUID,Accepted> ACCEPTED=new HashMap<>();
    private static final Map<String,String> MODEL_HASHES=new ConcurrentHashMap<>();
    private static CharacterVisualConfig draft;
    private static String draftProfile,notice="";
    private static long sequence,pending;
    private static java.util.function.Function<CharacterVisualConfig,CharacterVisualPackage> packageFactory;
    private static Object observedAnimation;
    private static long observedScripts=-1,nextAuto,pendingStarted;
    private static boolean autoDirty=true;
    private static int autoFailures;
    private static String attemptedHash="";
    private CharacterVisualSharing(){}
    static {
        AssimpModelLoader.addCacheInvalidationListener(MODEL_HASHES::clear);
    }
    public static String status(){return notice;}
    public static boolean pending(){return pending!=0;}
    public static void packageFactory(java.util.function.Function<CharacterVisualConfig,CharacterVisualPackage> factory){packageFactory=factory;changed();}
    public static boolean uploadEnabled(){return cn.minerealms.assimploader.config.AssimpLoaderConfig.getOrDefault(cn.minerealms.rigworks.RigWorksConfig.CLIENT.uploadConfigurations::get,true);}
    public static void uploadEnabled(boolean value){
        cn.minerealms.rigworks.RigWorksConfig.update(edit->edit.set(cn.minerealms.rigworks.RigWorksConfig.CLIENT.uploadConfigurations,value));
        CharacterAppearanceNetwork.sharingEnabled(value);
        if(value)changed();else {pending=0;notice="Character configuration upload disabled";}
    }
    public static boolean remoteEnabled(){return cn.minerealms.assimploader.config.AssimpLoaderConfig.getOrDefault(cn.minerealms.rigworks.RigWorksConfig.CLIENT.remotePlayers::get,true);}
    public static void remoteEnabled(boolean value){
        cn.minerealms.rigworks.RigWorksConfig.update(edit->edit.set(cn.minerealms.rigworks.RigWorksConfig.CLIENT.remotePlayers,value));
        if(!value)RemotePlayerCharacters.reload();else CharacterPackageClient.retry();
    }
    public static void changed(){autoDirty=true;autoFailures=0;nextAuto=tick()+10;}
    public static long acceptedRevision() {
        var player=Minecraft.getInstance().player;var state=player==null?null:ACCEPTED.get(player.getUUID());return state==null?0:state.revision;
    }
    public static CharacterVisualConfig accepted(UUID id){var state=ACCEPTED.get(id);return state==null?null:state.config;}
    public static String acceptedDigest(UUID id){var state=ACCEPTED.get(id);return state==null?"":state.digest;}
    static Map<UUID,CharacterAppearance> selections() {
        var result=new HashMap<UUID,CharacterAppearance>();ACCEPTED.forEach((id,state)->result.put(id,state.appearance));return result;
    }
    public static CharacterVisualConfig forProfile(String profile) {
        if(profile.equals(draftProfile)&&draft!=null)return draft;
        var saved=CharacterVisualOverrides.load(profile);if(saved!=null)return saved;
        var player=Minecraft.getInstance().player;var state=player==null?null:ACCEPTED.get(player.getUUID());
        return state!=null&&state.appearance.profile().equals(profile)?state.config:null;
    }
    public static void preview(String profile,CharacterVisualConfig config) {
        if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Visual editing requires the client thread");
        stage(profile,config);
        if(LocalPlayerCharacter.settings().profile().equals(profile))LocalPlayerCharacter.applyVisualConfig(config);
    }
    public static void save(String profile,CharacterVisualConfig config)throws IOException {
        CharacterVisualOverrides.save(profile,config);preview(profile,config);changed();
    }
    public static void registerAsset(ResourceLocation id,cn.minerealms.assimploader.model.AssimpScene scene)throws IOException {
        if(scene.getSourceResource()==null) {
            if(scene.getSourcePath()==null)throw new IOException("Imported character has no persistent model source");
            CharacterVisualOverrides.asset(id.toString(),scene.getSourcePath());
        }
        AssimpModelLoader.registerModel(id,scene);MODEL_HASHES.clear();
    }
    static cn.minerealms.assimploader.model.AssimpScene loadModel(ResourceManager resources,ResourceLocation id)throws IOException {
        var file=CharacterVisualOverrides.asset(id.toString());
        return file==null?AssimpModelLoader.loadCachedResource(resources,id,cn.minerealms.assimploader.core.ModelImportMode.DYNAMIC)
                :AssimpModelLoader.loadCachedFile(AssimpModelLoader.canonicalIdentity(id),file,cn.minerealms.assimploader.core.ModelImportMode.DYNAMIC,true);
    }
    static void stage(String profile,CharacterVisualConfig config){new ResourceLocation(profile);draftProfile=profile;draft=java.util.Objects.requireNonNull(config);}
    static CharacterVisualConfig newerSaved(String profile,java.nio.file.Path project)throws IOException {
        return CharacterVisualOverrides.readNewer(profile,project);
    }
    static void refreshSaved() {
        draft=null;draftProfile=null;CharacterVisualOverrides.invalidate();MODEL_HASHES.clear();changed();
    }
    public static void discardPreview() {
        draft=null;draftProfile=null;LocalPlayerCharacter.reload();
    }
    public static boolean shareCurrent() {
        var diagnostic=LocalPlayerCharacter.diagnostics();
        if(diagnostic.status()!=LocalPlayerCharacter.Status.READY||!diagnostic.failure().isEmpty()) {
            notice="Current character edit has not applied; previous display is retained where available";return false;
        }
        var config=LocalPlayerCharacter.currentVisualConfig();
        if(config==null){notice="Character is not ready";return false;}
        return share(LocalPlayerCharacter.settings().profile(),config);
    }
    public static boolean share(String profile,CharacterVisualConfig config) {
        var client=Minecraft.getInstance();
        if(client.player==null||client.level==null){notice="Join a world before sharing";return false;}
        if(!uploadEnabled()){notice="Configuration upload is disabled";return false;}
        if(!CharacterAppearanceNetwork.available()){notice="Local only: this server has no RigWorks sharing channel";return false;}
        autoFailures=0;
        return send(profile,config,bundle(profile,config));
    }
    private static CharacterVisualPackage bundle(String profile,CharacterVisualConfig config) {
        CharacterVisualPackage bundle=packageFactory==null?null:packageFactory.apply(config);
        if(bundle==null) {
            var player=Minecraft.getInstance().player;
            var previous=player==null?null:CharacterPackageClient.current(player.getUUID());
            if(previous!=null&&previous.profile().equals(profile))bundle=new CharacterVisualPackage(profile,previous.controller(),config,previous.files());
        }
        if(bundle==null)bundle=new CharacterVisualPackage(profile,null,config,Map.of());
        if(!bundle.profile().equals(profile))throw new IllegalArgumentException("Package profile does not match the active profile");
        return bundle;
    }
    private static boolean send(String profile,CharacterVisualConfig config,CharacterVisualPackage bundle) {
        var client=Minecraft.getInstance();
        var appearance=new CharacterAppearance(true,profile,LocalPlayerCharacter.animationMode().name(),client.player.getAbilities().flying,0);
        long request=++sequence;
        CharacterPackageClient.local(bundle);
        cn.minerealms.rigworks.network.CharacterPackageNetwork.upload(appearance,bundle,acceptedRevision(),request);
        pending=request;pendingStarted=tick();attemptedHash=bundle.hash();notice="Waiting for server acceptance";return true;
    }
    public static boolean receiveCatalog(UUID id,CharacterAppearance appearance,long configRevision,long request,String digest) {
        if(configRevision<0||configRevision>appearance.revision())return false;
        if(digest==null||!digest.matches("[a-f0-9]{64}|"))return false;
        var old=ACCEPTED.get(id);
        var client=Minecraft.getInstance();
        if(client.player!=null&&client.player.getUUID().equals(id)&&request!=0&&request==pending) {
            pending=0;autoFailures=0;notice="Shared revision "+configRevision;
        }else if(client.player!=null&&client.player.getUUID().equals(id)&&request!=0&&pending==0&&request<=sequence) {
            notice="Server confirmed revision "+configRevision;
        }
        if(old!=null&&appearance.revision()<=old.appearance.revision())return false;
        if(old==null&&ACCEPTED.size()>=512)return false;
        CharacterVisualConfig config=old!=null&&old.appearance.profile().equals(appearance.profile())?old.config:null;
        ACCEPTED.put(id,new Accepted(appearance,configRevision,config,digest));
        RemotePlayerCharacters.receive(id,appearance);
        return true;
    }
    static void packageReady(UUID id,CharacterVisualPackage bundle) {
        var state=ACCEPTED.get(id);
        if(state==null||!state.appearance.profile().equals(bundle.profile())||!state.digest.equals(bundle.visual().digest()))
            throw new IllegalArgumentException("Package does not match the accepted server catalog");
        ACCEPTED.put(id,new Accepted(state.appearance,state.revision,bundle.visual(),state.digest));
        var local=Minecraft.getInstance().player;
        if(local!=null&&local.getUUID().equals(id)&&state.appearance.profile().equals(LocalPlayerCharacter.settings().profile())
                &&(draft==null||draft.equals(bundle.visual())))LocalPlayerCharacter.applyVisualConfig(bundle.visual());
    }
    public static void rejected(long request,String reason) {
        if(request!=0&&request!=pending)return;
        pending=0;notice="Sharing rejected: "+reason;
        autoFailures++;autoDirty=true;nextAuto=tick()+100;
        var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(Component.literal(notice),false);
    }
    private static long tick(){var client=Minecraft.getInstance();var player=client==null?null:client.player;return player==null?0:player.tickCount;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var client=Minecraft.getInstance();if(client.player==null||client.level==null)return;
        Object animation=LocalPlayerCharacter.currentAnimation();long scripts=CharacterScriptRuntime.revision();
        if(animation!=observedAnimation||scripts!=observedScripts){observedAnimation=animation;observedScripts=scripts;autoDirty=true;nextAuto=tick()+10;}
        if(!uploadEnabled()||!CharacterAppearanceNetwork.available()||animation==null)return;
        if(pending!=0){if(tick()-pendingStarted>600){pending=0;autoFailures++;autoDirty=true;nextAuto=tick()+100;notice="Server confirmation timed out; current catalog will be checked on retry";}return;}
        var diagnostic=LocalPlayerCharacter.diagnostics();
        if(diagnostic.status()!=LocalPlayerCharacter.Status.READY||!diagnostic.failure().isEmpty())return;
        if(!autoDirty||tick()<nextAuto)return;
        if(!acceptedDigest(client.player.getUUID()).isEmpty()&&CharacterPackageClient.current(client.player.getUUID())==null)return;
        try {
            var config=LocalPlayerCharacter.currentVisualConfig();if(config==null)return;
            var selected=bundle(LocalPlayerCharacter.settings().profile(),config);
            if(selected.hash().equals(CharacterPackageClient.wanted(client.player.getUUID()))){autoDirty=false;return;}
            if(!selected.hash().equals(attemptedHash))autoFailures=0;
            if(autoFailures>=3){autoDirty=false;return;}
            autoDirty=false;send(LocalPlayerCharacter.settings().profile(),config,selected);
        }catch(RuntimeException error){notice="Automatic sharing failed: "+error.getMessage();autoDirty=false;}
    }
    /** Called only on a bounded model-loading worker; hashing never runs in the render loop. */
    static String modelHash(ResourceManager resources,String model)throws IOException {
        String key=System.identityHashCode(resources)+":"+AssimpModelLoader.getCacheGeneration()+":"+model;
        String existing=MODEL_HASHES.get(key);if(existing!=null)return existing;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            var file=CharacterVisualOverrides.asset(model);
            try(var input=file==null?resources.open(new ResourceLocation(model)):java.nio.file.Files.newInputStream(file)) {
                byte[] buffer=new byte[65536];int count;
                while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            }
            String hash=HexFormat.of().formatHex(digest.digest());MODEL_HASHES.put(key,hash);return hash;
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static String cachedModelHash(ResourceManager resources,String model) {
        return MODEL_HASHES.get(System.identityHashCode(resources)+":"+AssimpModelLoader.getCacheGeneration()+":"+model);
    }
    static String resource(ResourceManager resources,String id)throws IOException {
        try(var input=resources.open(new ResourceLocation(id))) {
            byte[] bytes=input.readNBytes(CharacterVisualConfig.MAX_BYTES+1);
            if(bytes.length>CharacterVisualConfig.MAX_BYTES)throw new IOException("Character sidecar exceeds 128 KiB: "+id);
            String json=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            CharacterVisualConfig.parseBounded(json);return json;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACCEPTED.clear();draft=null;draftProfile=null;pending=0;notice="";sequence=0;MODEL_HASHES.clear();observedAnimation=null;observedScripts=-1;autoDirty=true;nextAuto=0;autoFailures=0;attemptedHash="";
    }
}
