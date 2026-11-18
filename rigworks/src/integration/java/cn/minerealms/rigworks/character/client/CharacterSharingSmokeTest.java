package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.RigWorksConfig;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterPackageBinary;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import cn.minerealms.rigworks.character.core.CharacterVisualPackage;
import cn.minerealms.rigworks.character.script.AvatarScriptBundle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in real-peer acceptance. Coordination files carry assertions only, never runtime model state. */
@Mod.EventBusSubscriber(modid=RigWorks.MODID,value=Dist.CLIENT)
public final class CharacterSharingSmokeTest {
    private static final String ROLE=System.getProperty("charactersynctest.role","");
    private static final boolean ENABLED=ROLE.equals("owner")||ROLE.equals("viewer");
    private static final long TIMEOUT=Math.max(30,Long.getLong("charactersynctest.timeoutSeconds",180))*1000;
    private static final String AVATAR="rigworks:acceptance",CONTROLLER="rigworks:acceptance";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static boolean finished,opening,preferencesCaptured,oldUpload,oldRemote,oldPause,positioning,positioned,capturing;
    private static int phase,phaseTicks;
    private static long started,firstCalls,disabledCalls,firstRevision,progressWritten;
    private static String token="",profile="",firstHash="",firstSource="",secondHash="";
    private static UUID owner;
    private static Path coordination,authorDirectory;
    private static RigWorksConfig.CharacterAnimationMode oldMode;
    private static CharacterVisualConfig original,edited;
    private static JsonObject firstExpected,secondExpected;

    private CharacterSharingSmokeTest(){}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED||finished||capturing||event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            if(started==0) {
                started=System.currentTimeMillis();
                String directory=System.getProperty("charactersynctest.coordination","");
                require(!directory.isBlank(),"Set charactersynctest.coordination to the shared test directory");
                coordination=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(coordination);
            }
            if(System.currentTimeMillis()-progressWritten>5000) {
                progressWritten=System.currentTimeMillis();
                var progress=new LinkedHashMap<String,Object>();progress.put("phase",phase);progress.put("role",ROLE);
                progress.put("screen",mc.screen==null?"":mc.screen.getClass().getName());
                progress.put("local",LocalPlayerCharacter.diagnostics());progress.put("settings",LocalPlayerCharacter.settings());
                progress.put("sharing",CharacterVisualSharing.status());progress.put("download",CharacterPackageClient.status());
                progress.put("remote",RemotePlayerCharacters.diagnostics());if(owner!=null)progress.put("behavior",CharacterAvatarBehavior.diagnostics(owner));
                atomicJson(coordination.resolve(ROLE+"-progress.json"),progress);
            }
            require(System.currentTimeMillis()-started<TIMEOUT,"Two-client acceptance timed out at "+ROLE+" phase "+phase);
            if(mc.player==null||mc.level==null) {
                if(ROLE.equals("owner")&&!opening&&mc.screen instanceof TitleScreen) {
                    String world=System.getProperty("charactersynctest.world","");
                    require(!world.isBlank(),"Owner requires an existing disposable charactersynctest.world");
                    opening=true;mc.createWorldOpenFlows().loadLevel(new TitleScreen(),world);
                }
                if(ROLE.equals("viewer")&&!opening&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null
                        &&Files.isRegularFile(coordination.resolve("owner-ready.json"))) {
                    opening=true;
                    String address="127.0.0.1:"+Integer.getInteger("charactersynctest.port",25575);
                    ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("RigWorks LAN acceptance",address,true),false);
                }
                return;
            }
            if(mc.getOverlay()!=null||mc.screen!=null)return;
            if(!preferencesCaptured) {
                preferencesCaptured=true;oldUpload=CharacterVisualSharing.uploadEnabled();oldRemote=CharacterVisualSharing.remoteEnabled();
                oldMode=LocalPlayerCharacter.animationMode();oldPause=mc.options.pauseOnLostFocus;mc.options.pauseOnLostFocus=false;
                CharacterVisualSharing.remoteEnabled(true);CharacterVisualSharing.uploadEnabled(ROLE.equals("owner"));
            }
            phaseTicks++;
            if(ROLE.equals("owner")){positionViewer(mc);owner(mc);}else viewer(mc);
        }catch(Exception|LinkageError failure){complete(mc,failure);}
    }

    private static void owner(Minecraft mc)throws IOException {
        switch(phase) {
            case 0 -> {
                var state=LocalPlayerCharacter.diagnostics();require(state.status()!=LocalPlayerCharacter.Status.FAILED,state.toString());
                if(state.status()!=LocalPlayerCharacter.Status.READY)return;
                require(LocalPlayerCharacter.settings().enabled(),"Owner character is disabled");
                original=LocalPlayerCharacter.currentVisualConfig();if(original==null)return;
                profile=LocalPlayerCharacter.settings().profile();owner=mc.player.getUUID();token=UUID.randomUUID().toString();
                require(CharacterAvatarBehavior.repository().forProfile(profile).isEmpty(),"Use a disposable author repository without an existing package for "+profile);
                authorDirectory=CharacterAvatarBehavior.repository().directory().resolve("acceptance").toAbsolutePath().normalize();
                require(!Files.exists(authorDirectory),"Disposable test author folder already exists: "+authorDirectory);
                writeAuthor(1);CharacterAvatarBehavior.refresh();
                var server=mc.getSingleplayerServer();require(server!=null,"Owner must open a disposable integrated world");
                // Disposable LAN acceptance uses offline dev identities, independent of Mojang service latency.
                server.setUsesAuthentication(false);
                int port=Integer.getInteger("charactersynctest.port",25575);
                require(server.isPublished()||server.publishServer(GameType.CREATIVE,false,port),"Cannot publish LAN server on "+port);
                add("lanPublished",Map.of("port",port,"owner",owner.toString()));advance();
            }
            case 1 -> {
                var source=source(1);if(source==null)return;
                LocalPlayerCharacter.setAnimationMode(RigWorksConfig.CharacterAnimationMode.SCRIPTED);
                CharacterVisualSharing.changed();advance();
            }
            case 2 -> {
                if(LocalPlayerCharacter.diagnostics().status()!=LocalPlayerCharacter.Status.READY)return;
                var source=source(1);if(source==null)return;
                var visual=LocalPlayerCharacter.currentVisualConfig();if(visual==null)return;
                var bundle=source.toPackage(visual);if(!acceptedLocally(bundle,source))return;
                firstHash=bundle.hash();firstSource=source.hash();firstRevision=CharacterVisualSharing.acceptedRevision();
                firstExpected=expected(bundle,source,1);writeMarker("owner-ready.json",firstExpected);
                add("ownerV1Accepted",firstExpected);advance();
            }
            case 3 -> {
                if(!acknowledged("viewer-restored.json",firstHash))return;
                edited=editConfiguration(original);writeAuthor(2);CharacterAvatarBehavior.refresh();
                CharacterVisualSharing.preview(profile,edited);CharacterVisualSharing.changed();advance();
            }
            case 4 -> {
                var source=source(2);if(source==null)return;
                var bundle=source.toPackage(edited);if(!acceptedLocally(bundle,source))return;
                require(CharacterVisualSharing.acceptedRevision()>firstRevision,"Second package did not advance accepted revision");
                require(!bundle.hash().equals(firstHash)&&!source.hash().equals(firstSource),"Second authored version did not change content identity");
                secondHash=bundle.hash();secondExpected=expected(bundle,source,2);writeMarker("owner-v2.json",secondExpected);
                add("ownerV2Accepted",secondExpected);advance();
            }
            case 5 -> {if(acknowledged("viewer-v2.json",secondHash))complete(mc,null);}
            default -> throw new IllegalStateException("Invalid owner acceptance phase");
        }
    }

    private static void viewer(Minecraft mc)throws IOException {
        switch(phase) {
            case 0 -> {
                JsonObject expected=readMarker("owner-ready.json");if(expected==null)return;
                token=expected.get("token").getAsString();owner=UUID.fromString(expected.get("owner").getAsString());profile=expected.get("profile").getAsString();
                require(!owner.equals(mc.player.getUUID()),"Owner and viewer require distinct usernames/UUIDs");
                require(CharacterAvatarBehavior.repository().forProfile(profile).isEmpty(),"Viewer must have the model resource pack but no avatar author package");
                firstExpected=expected;firstHash=expected.get("packageHash").getAsString();firstSource=expected.get("sourceHash").getAsString();advance();
            }
            case 1 -> {
                if(!verifyRemote(mc,firstExpected))return;
                firstCalls=invocations();writeAck("viewer-v1.json",firstHash);
                add("viewerV1Installed",Map.of("packageHash",firstHash,"invocations",firstCalls));
                CharacterVisualSharing.remoteEnabled(false);advance();
            }
            case 2 -> {
                if(phaseTicks<8)return;
                var player=mc.level.players().stream().filter(value->value.getUUID().equals(owner)).findFirst().orElse(null);
                require(player==null||RemotePlayerCharacters.view(player,0)==null,"Display opt-out retained a remote binding");
                require(!Boolean.TRUE.equals(CharacterAvatarBehavior.diagnostics(owner).get("running")),"Display opt-out left the remote script running");
                disabledCalls=invocations();advance();
            }
            case 3 -> {
                if(phaseTicks<12)return;
                require(invocations()==disabledCalls,"Remote script continued executing while other avatars were disabled");
                require(CharacterPackageClient.current(owner)!=null,"Display opt-out discarded its verified cached package");
                CharacterVisualSharing.remoteEnabled(true);add("viewerOptOut",Map.of("pausedInvocations",disabledCalls));advance();
            }
            case 4 -> {
                if(!verifyRemote(mc,firstExpected)||invocations()<=disabledCalls)return;
                writeAck("viewer-restored.json",firstHash);add("viewerReenabled",Map.of("invocations",invocations()));advance();
            }
            case 5 -> {
                var next=readMarker("owner-v2.json");if(next==null||!token.equals(next.get("token").getAsString()))return;
                secondExpected=next;secondHash=next.get("packageHash").getAsString();
                require(!secondHash.equals(firstHash),"Owner did not announce a changed package");advance();
            }
            case 6 -> {
                if(!verifyRemote(mc,secondExpected))return;
                require(!CharacterVisualSharing.accepted(owner).digest().equals(firstExpected.get("visualHash").getAsString()),"Equipment/root edits were not synchronized");
                writeAck("viewer-v2.json",secondHash);add("viewerV2Installed",Map.of("packageHash",secondHash,"invocations",invocations()));
                var target=mc.level.players().stream().filter(value->value.getUUID().equals(owner)).findFirst().orElseThrow();
                double dx=target.getX()-mc.player.getX(),dy=target.getEyeY()-mc.player.getEyeY(),dz=target.getZ()-mc.player.getZ();
                float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
                mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
                advance();
            }
            case 7 -> {
                if(phaseTicks<40)return;
                capturing=true;String name="character-sharing-"+token+"-viewer.png";
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->mc.execute(()-> {
                    add("viewerScreenshot",Map.of("file",name));complete(mc,null);
                }));
            }
            default -> throw new IllegalStateException("Invalid viewer acceptance phase");
        }
    }

    private static boolean verifyRemote(Minecraft mc,JsonObject expected)throws IOException {
        String hash=expected.get("packageHash").getAsString();var bundle=CharacterPackageClient.current(owner);var config=CharacterVisualSharing.accepted(owner);
        if(bundle==null||config==null||!hash.equals(bundle.hash())||!config.digest().equals(expected.get("visualHash").getAsString()))return false;
        require(bundle.visual().equals(config),"Installed package and accepted visual configuration differ");
        var appearance=CharacterVisualSharing.selections().get(owner);
        if(appearance==null||!appearance.enabled()||!appearance.mode().equals("SCRIPTED")||appearance.revision()<expected.get("appearanceRevision").getAsLong())return false;
        var player=mc.level.players().stream().filter(value->value.getUUID().equals(owner)).findFirst().orElse(null);if(player==null)return false;
        var view=RemotePlayerCharacters.view(player,0);if(view==null)return false;
        if(!view.profile().toJson().equals(config.profile().toJson())||!view.equipment().toJson().equals(config.equipment().toJson()))return false;
        var diagnostics=CharacterAvatarBehavior.diagnostics(owner);
        if(!expected.get("sourceHash").getAsString().equals(diagnostics.get("sourceHash"))||!Boolean.TRUE.equals(diagnostics.get("available"))
                ||!Boolean.TRUE.equals(diagnostics.get("running"))||invocations()<2)return false;
        require(String.valueOf(diagnostics.get("failure")).isEmpty(),"Remote script failed: "+diagnostics);
        require(CharacterAvatarBehavior.repository().forProfile(profile).isEmpty(),"Downloaded behavior leaked into author source discovery");
        Path root=mc.gameDirectory.toPath().resolve("kubejs/rigworks/cache");require(Files.isDirectory(root),"Verified package cache was not written");
        Path cached;
        try(var files=Files.walk(root,4)) {
            var list=files.filter(path->Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)).toList();
            require(list.stream().noneMatch(path->path.getFileName().toString().endsWith(".js")),"Downloaded plaintext JS was written to cache");
            cached=list.stream().filter(path->path.getFileName().toString().equals(hash+".bin")).findFirst().orElse(null);
        }
        if(cached==null)return false;
        require(CharacterPackageBinary.decode(Files.readAllBytes(cached)).hash().equals(hash),"Compressed on-disk package has wrong identity");
        return true;
    }

    private static boolean acceptedLocally(CharacterVisualPackage expected,AvatarScriptBundle source) {
        var installed=CharacterPackageClient.current(owner);var accepted=CharacterVisualSharing.accepted(owner);
        var appearance=CharacterVisualSharing.selections().get(owner);var diagnostic=CharacterAvatarBehavior.diagnostics(owner);
        return !CharacterVisualSharing.pending()&&installed!=null&&installed.hash().equals(expected.hash())&&expected.visual().equals(accepted)
                &&appearance!=null&&appearance.enabled()&&appearance.mode().equals("SCRIPTED")&&source.hash().equals(diagnostic.get("sourceHash"))
                &&Boolean.TRUE.equals(diagnostic.get("available"))&&Boolean.TRUE.equals(diagnostic.get("running"))&&((Number)diagnostic.get("invocations")).longValue()>=2;
    }
    private static AvatarScriptBundle source(int version) {
        var bundle=CharacterAvatarBehavior.repository().find(AVATAR).orElse(null);
        return bundle!=null&&bundle.mappings().get("acceptanceVersion").getAsInt()==version?bundle:null;
    }
    private static JsonObject expected(CharacterVisualPackage bundle,AvatarScriptBundle source,int version) {
        var value=new JsonObject();value.addProperty("token",token);value.addProperty("owner",owner.toString());value.addProperty("profile",profile);
        value.addProperty("version",version);value.addProperty("packageHash",bundle.hash());value.addProperty("sourceHash",source.hash());value.addProperty("visualHash",bundle.visual().digest());
        value.addProperty("configRevision",CharacterVisualSharing.acceptedRevision());value.addProperty("appearanceRevision",CharacterVisualSharing.selections().get(owner).revision());return value;
    }
    private static void writeAuthor(int version)throws IOException {
        Files.createDirectories(authorDirectory);var manifest=new JsonObject();manifest.addProperty("version",1);manifest.addProperty("id",AVATAR);
        manifest.addProperty("profile",profile);manifest.addProperty("controller",CONTROLLER);manifest.addProperty("mappings","mappings.json");
        var scripts=new com.google.gson.JsonArray();scripts.add("controller.js");manifest.add("scripts",scripts);
        var mappings=new JsonObject();mappings.addProperty("acceptanceVersion",version);
        String source="RigWorks.register('"+CONTROLLER+"', frame => { const s=frame.state(); s.calls=(s.calls||0)+1; if(frame.mappings.acceptanceVersion!=="+version+")throw new Error('Wrong acceptance version'); frame.defaultMotion(); });\n";
        Files.writeString(authorDirectory.resolve("manifest.json"),JSON.toJson(manifest),StandardCharsets.UTF_8);
        Files.writeString(authorDirectory.resolve("mappings.json"),JSON.toJson(mappings),StandardCharsets.UTF_8);
        Files.writeString(authorDirectory.resolve("controller.js"),source,StandardCharsets.UTF_8);
    }
    private static CharacterVisualConfig editConfiguration(CharacterVisualConfig base) {
        var equipment=base.equipment();require(equipment.rightHand()!=null&&equipment.leftHand()!=null,"Acceptance needs both equipment hand sockets");
        equipment=equipment.withHands(offset(equipment.rightHand(),.015f),offset(equipment.leftHand(),-.012f));
        var root=base.transform();var transform=new CharacterVisualConfig.Transform(root.x(),root.y(),root.z(),root.rx(),root.ry()+2,root.rz(),root.sx(),root.sy(),root.sz());
        return new CharacterVisualConfig(base.profile(),equipment,transform,base.modelHash(),base.rig(),base.physics(),base.options());
    }
    private static CharacterEquipmentDefinition.Mount offset(CharacterEquipmentDefinition.Mount mount,float value) {
        var p=mount.position();return new CharacterEquipmentDefinition.Mount(mount.node(),new CharacterEquipmentDefinition.Position(p.x()+value,p.y(),p.z()),mount.rotation(),mount.scale());
    }
    private static void positionViewer(Minecraft mc) {
        if(positioned||positioning||mc.getSingleplayerServer()==null||mc.player==null)return;
        var server=mc.getSingleplayerServer();UUID local=mc.player.getUUID();positioning=true;
        server.execute(()-> {
            boolean moved=false;var host=server.getPlayerList().getPlayer(local);
            if(host!=null)for(var peer:server.getPlayerList().getPlayers())if(peer!=host) {
                peer.teleportTo(host.serverLevel(),host.getX()+3,host.getY(),host.getZ(),host.getYRot()+180,0);moved=true;break;
            }
            boolean result=moved;mc.execute(()->{positioning=false;positioned|=result;});
        });
    }
    private static long invocations(){return ((Number)CharacterAvatarBehavior.diagnostics(owner).get("invocations")).longValue();}
    private static void advance(){phase++;phaseTicks=0;}
    private static void add(String name,Object details){CHECKS.add(Map.of("name",name,"details",details,"elapsedMillis",System.currentTimeMillis()-started));}
    private static boolean acknowledged(String name,String hash)throws IOException {
        var value=readMarker(name);return value!=null&&token.equals(value.get("token").getAsString())&&hash.equals(value.get("packageHash").getAsString());
    }
    private static void writeAck(String name,String hash)throws IOException {var data=new JsonObject();data.addProperty("token",token);data.addProperty("packageHash",hash);data.addProperty("invocations",invocations());writeMarker(name,data);}
    private static JsonObject readMarker(String name)throws IOException {
        Path path=coordination.resolve(name);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return null;
        require(Files.size(path)<=131072,"Acceptance coordination file exceeds budget");return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void writeMarker(String name,JsonObject value)throws IOException {atomicJson(coordination.resolve(name),value);}
    private static void atomicJson(Path target,Object value)throws IOException {
        Files.createDirectories(target.getParent());Path temporary=Files.createTempFile(target.getParent(),".character-sync-",".tmp");
        try {
            Files.writeString(temporary,JSON.toJson(value),StandardCharsets.UTF_8);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void complete(Minecraft mc,Throwable failure) {
        if(finished)return;finished=true;
        var result=new LinkedHashMap<String,Object>();result.put("success",failure==null);result.put("failure",failure==null?"":failure.toString());result.put("role",ROLE);
        result.put("token",token);result.put("phase",phase);result.put("owner",owner==null?"":owner.toString());result.put("profile",profile);result.put("checks",CHECKS);
        result.put("firstHash",firstHash);result.put("secondHash",secondHash);result.put("sharingStatus",CharacterVisualSharing.status());result.put("downloadStatus",CharacterPackageClient.status());
        result.put("remote",RemotePlayerCharacters.diagnostics());if(owner!=null)result.put("behavior",CharacterAvatarBehavior.diagnostics(owner));
        result.put("elapsedMillis",started==0?0:System.currentTimeMillis()-started);
        try {
            atomicJson(mc.gameDirectory.toPath().toAbsolutePath().resolve("character-sync-smoke-"+ROLE+"-result.json"),result);
            if(coordination!=null)atomicJson(coordination.resolve(ROLE+"-result.json"),result);
        }catch(Exception error){AssimpLoader.LOGGER.error("Cannot write two-client sharing acceptance result",error);}
        if(failure!=null&&preferencesCaptured) {
            try {
                CharacterVisualSharing.uploadEnabled(oldUpload);CharacterVisualSharing.remoteEnabled(oldRemote);mc.options.pauseOnLostFocus=oldPause;
                if(ROLE.equals("owner")){LocalPlayerCharacter.setAnimationMode(oldMode);if(original!=null)CharacterVisualSharing.preview(profile,original);}
            }catch(RuntimeException cleanup){AssimpLoader.LOGGER.warn("Cannot restore acceptance preferences",cleanup);}
        }
        if(failure==null)AssimpLoader.LOGGER.info("[CharacterSharingSmoke] {} SUCCESS; client/server remain open",ROLE);
        else AssimpLoader.LOGGER.error("[CharacterSharingSmoke] "+ROLE+" FAILED; client/server remain open",failure);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
