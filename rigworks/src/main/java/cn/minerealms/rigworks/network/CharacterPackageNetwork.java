package cn.minerealms.rigworks.network;

import cn.minerealms.rigworks.character.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded chunk transport. Downloaded script text is handed to a dedicated runtime, never global KubeJS. */
@Mod.EventBusSubscriber(modid=cn.minerealms.rigworks.RigWorks.MODID)
public final class CharacterPackageNetwork {
    private record Upload(Start start,CharacterPackageTransfer transfer,long deadline){}
    private record Processing(ServerPlayer sender,Start start){}
    private static final Map<UUID,Upload> UPLOADS=new HashMap<>();
    private static final Map<UUID,Long> DOWNLOADS=new HashMap<>();
    private static final Map<UUID,Long> LAST_UPLOAD=new HashMap<>();
    private static final Map<UUID,Processing> PROCESSING=new HashMap<>();
    private static final Map<Path,Set<String>> PINNED=new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),task->{Thread t=new Thread(task,"rigworks-package-server");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static SimpleChannel channel;
    private CharacterPackageNetwork(){}
    static void register(SimpleChannel value) {
        channel=value;
        channel.messageBuilder(Start.class,4,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b)->{b.writeBoolean(p.appearance.enabled());b.writeUtf(p.appearance.profile(),256);b.writeUtf(p.appearance.mode(),16);b.writeBoolean(p.appearance.flying());b.writeVarLong(p.expected);b.writeVarLong(p.request);b.writeUtf(p.hash,64);b.writeUtf(p.wireHash,64);b.writeVarInt(p.size);})
                .decoder(b->new Start(new CharacterAppearance(b.readBoolean(),b.readUtf(256),b.readUtf(16),b.readBoolean(),0),b.readVarLong(),b.readVarLong(),b.readUtf(64),b.readUtf(64),b.readVarInt()))
                .consumerMainThread((p,c)->start(c.get().getSender(),p)).add();
        channel.messageBuilder(UploadPart.class,5,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b)->{b.writeUtf(p.hash,64);b.writeVarInt(p.index);b.writeByteArray(p.bytes);})
                .decoder(b->new UploadPart(b.readUtf(64),b.readVarInt(),b.readByteArray(CharacterPackageTransfer.CHUNK_BYTES)))
                .consumerMainThread((p,c)->part(c.get().getSender(),p)).add();
        channel.messageBuilder(Request.class,6,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b)->b.writeUtf(p.hash,64)).decoder(b->new Request(b.readUtf(64)))
                .consumerMainThread((p,c)->download(c.get().getSender(),p.hash)).add();
        channel.messageBuilder(DownloadStart.class,7,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b)->{b.writeUtf(p.hash,64);b.writeUtf(p.wireHash,64);b.writeVarInt(p.size);}).decoder(b->new DownloadStart(b.readUtf(64),b.readUtf(64),b.readVarInt()))
                .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.minerealms.rigworks.character.client.CharacterPackageClient.begin(p.hash,p.wireHash,p.size))).add();
        channel.messageBuilder(DownloadPart.class,8,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b)->{b.writeUtf(p.hash,64);b.writeVarInt(p.index);b.writeByteArray(p.bytes);})
                .decoder(b->new DownloadPart(b.readUtf(64),b.readVarInt(),b.readByteArray(CharacterPackageTransfer.CHUNK_BYTES)))
                .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.minerealms.rigworks.character.client.CharacterPackageClient.part(p.hash,p.index,p.bytes))).add();
        channel.messageBuilder(DownloadError.class,9,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b)->{b.writeUtf(p.hash,64);b.writeUtf(p.reason,512);}).decoder(b->new DownloadError(b.readUtf(64),b.readUtf(512)))
                .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->cn.minerealms.rigworks.character.client.CharacterPackageClient.failed(p.hash,p.reason))).add();
    }
    public static void upload(CharacterAppearance appearance,CharacterVisualPackage bundle,long expected,long request) {
        byte[] bytes;
        try{bytes=CharacterPackageBinary.encode(bundle);}catch(java.io.IOException e){throw new IllegalArgumentException("Cannot compress character package",e);}
        channel.sendToServer(new Start(appearance,expected,request,bundle.hash(),CharacterVisualConfig.hash(bytes),bytes.length));
        for(int offset=0,index=0;offset<bytes.length;offset+=CharacterPackageTransfer.CHUNK_BYTES,index++)
            channel.sendToServer(new UploadPart(bundle.hash(),index,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+CharacterPackageTransfer.CHUNK_BYTES))));
    }
    public static void request(String hash){if(CharacterAppearanceNetwork.available())channel.sendToServer(new Request(hash));}
    private static void start(ServerPlayer sender,Start request) {
        if(sender==null)return;
        try {
            long now=sender.server.getTickCount();
            if(!CharacterAppearanceNetwork.sharingAllowed(sender))throw new IllegalArgumentException("Character upload is disabled for this connection");
            if(now-LAST_UPLOAD.getOrDefault(sender.getUUID(),Long.MIN_VALUE/2)<4)throw new IllegalArgumentException("Character package upload rate limit; retry");
            if(request.expected<0||request.request<1||!request.hash.matches("[a-f0-9]{64}")||PROCESSING.containsKey(sender.getUUID())||UPLOADS.size()>=32)
                throw new IllegalArgumentException("Character package upload queue is busy");
            var previous=UPLOADS.remove(sender.getUUID());if(previous!=null)CharacterAppearanceNetwork.reject(sender,previous.start.request,"Replaced by a newer package upload");
            var accepted=CharacterVisualSavedData.get(sender.server).ledger.get(sender.getUUID());
            if(request.expected!=(accepted==null?0:accepted.configRevision()))throw new IllegalArgumentException("Configuration changed on the server; retry after receiving the current version");
            UPLOADS.put(sender.getUUID(),new Upload(request,new CharacterPackageTransfer(request.wireHash,request.size),sender.server.getTickCount()+600L));
            LAST_UPLOAD.put(sender.getUUID(),now);
        }catch(RuntimeException e){CharacterAppearanceNetwork.reject(sender,request.request,reason(e));}
    }
    private static void part(ServerPlayer sender,UploadPart part) {
        if(sender==null)return;Upload upload=UPLOADS.get(sender.getUUID());if(upload==null)return;
        try {
            if(!upload.start.hash.equals(part.hash)||sender.server.getTickCount()>upload.deadline)throw new IllegalArgumentException("Stale character package upload");
            if(!upload.transfer.append(part.index,part.bytes))return;
            UPLOADS.remove(sender.getUUID());byte[] bytes=upload.transfer.finish();var job=new Processing(sender,upload.start);PROCESSING.put(sender.getUUID(),job);
            var server=sender.server;refreshPins(server);
            IO.execute(()-> {
                try {
                    var bundle=CharacterPackageBinary.decode(bytes);
                    if(!bundle.hash().equals(part.hash))throw new IllegalArgumentException("Noncanonical package identity");
                    writeCache(server,bundle);
                    server.execute(()-> {
                        if(PROCESSING.get(sender.getUUID())!=job)return;
                        try {
                            if(server.getPlayerList().getPlayer(sender.getUUID())==sender)
                                CharacterAppearanceNetwork.acceptPackage(sender,upload.start.appearance,bundle,upload.start.expected,upload.start.request);
                        }catch(RuntimeException e){CharacterAppearanceNetwork.reject(sender,upload.start.request,reason(e));}
                        finally {PROCESSING.remove(sender.getUUID(),job);refreshPins(server);}
                    });
                }catch(Exception e){server.execute(()->{
                    if(!PROCESSING.remove(sender.getUUID(),job))return;refreshPins(server);
                    if(server.getPlayerList().getPlayer(sender.getUUID())==sender)CharacterAppearanceNetwork.reject(sender,upload.start.request,reason(e));
                });}
            });
        }catch(RuntimeException e){UPLOADS.remove(sender.getUUID());PROCESSING.remove(sender.getUUID());refreshPins(sender.server);CharacterAppearanceNetwork.reject(sender,upload.start.request,reason(e));}
    }
    private static void download(ServerPlayer viewer,String hash) {
        if(viewer==null||!hash.matches("[a-f0-9]{64}"))return;
        var server=viewer.server;long now=server.getTickCount();
        if(now-DOWNLOADS.getOrDefault(viewer.getUUID(),Long.MIN_VALUE/2)<4){error(viewer,hash,"Character package download rate limit; retry");return;}
        DOWNLOADS.put(viewer.getUUID(),now);
        if(!CharacterVisualSavedData.get(server).packages.containsValue(hash)){error(viewer,hash,"Character package is no longer shared");return;}
        try {
            IO.execute(()-> {
                try {
                    var file=cacheRoot(server).resolve(hash+".bin");
                    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>CharacterPackageBinary.MAX_BYTES)throw new java.io.IOException("Shared character package is missing");
                    byte[] bytes=Files.readAllBytes(file);
                    if(!CharacterPackageBinary.decode(bytes).hash().equals(hash))throw new java.io.IOException("Shared character package failed its content hash");
                    server.execute(()-> {
                        if(server.getPlayerList().getPlayer(viewer.getUUID())!=viewer)return;
                        if(!CharacterVisualSavedData.get(server).packages.containsValue(hash)){error(viewer,hash,"Character package is no longer shared");return;}
                        send(viewer,new DownloadStart(hash,CharacterVisualConfig.hash(bytes),bytes.length));
                        for(int offset=0,index=0;offset<bytes.length;offset+=CharacterPackageTransfer.CHUNK_BYTES,index++)
                            send(viewer,new DownloadPart(hash,index,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+CharacterPackageTransfer.CHUNK_BYTES))));
                    });
                }catch(Exception e){server.execute(()->{if(server.getPlayerList().getPlayer(viewer.getUUID())==viewer)error(viewer,hash,reason(e));});}
            });
        }catch(RejectedExecutionException e){error(viewer,hash,"Character package IO queue is busy; retry");}
    }
    private static Path cacheRoot(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("data/rigworks/packages").toAbsolutePath().normalize();}
    static void refreshPins(MinecraftServer server) {
        var retained=new HashSet<>(CharacterVisualSavedData.get(server).packages.values());
        PROCESSING.values().stream().filter(job->job.sender.server==server).forEach(job->retained.add(job.start.hash));
        PINNED.put(cacheRoot(server),Set.copyOf(retained));
    }
    private static void writeCache(MinecraftServer server,CharacterVisualPackage bundle)throws java.io.IOException {
        Path root=cacheRoot(server);Files.createDirectories(root);
        if(!PINNED.containsKey(root))throw new java.io.IOException("Character package server stopped");
        byte[] bytes=CharacterPackageBinary.encode(bundle);Path target=root.resolve(bundle.hash()+".bin");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
            if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Files.size(target)<=CharacterPackageBinary.MAX_BYTES) {
                try{if(CharacterPackageBinary.decode(Files.readAllBytes(target)).hash().equals(bundle.hash()))return;}catch(Exception ignored){}
            }
            Files.delete(target);
        }
        long total=0;var candidates=new ArrayList<Path>();
        try(var list=Files.list(root)){for(Path file:list.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.bin")).toList()) {
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){total+=Files.size(file);candidates.add(file);}
        }}
        candidates.sort(Comparator.comparingLong(file->{try{return Files.getLastModifiedTime(file).toMillis();}catch(java.io.IOException e){return 0L;}}));
        for(Path old:candidates){
            if(total+bytes.length<=134217728)break;var retained=PINNED.get(root);
            if(retained==null)throw new java.io.IOException("Character package server stopped");
            if(retained.contains(old.getFileName().toString().substring(0,64)))continue;
            long size=Files.size(old);Files.delete(old);total-=size;
        }
        if(total+bytes.length>134217728)throw new java.io.IOException("Shared character package cache reached 128 MiB");
        Path temporary=Files.createTempFile(root,".package-",".tmp");
        try {
            Files.write(temporary,bytes);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,target);}
        }finally{Files.deleteIfExists(temporary);}
    }
    private static void send(ServerPlayer player,Object packet){channel.send(PacketDistributor.PLAYER.with(()->player),packet);}
    private static void error(ServerPlayer player,String hash,String reason){send(player,new DownloadError(hash,reason));}
    private static String reason(Exception error){String text=error.getMessage();return text==null?"Character package transfer failed":text.substring(0,Math.min(512,text.length()));}
    static void withdraw(ServerPlayer player){UPLOADS.remove(player.getUUID());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(var id:List.copyOf(UPLOADS.keySet()))if(server.getTickCount()>UPLOADS.get(id).deadline) {
            Upload old=UPLOADS.remove(id);ServerPlayer sender=server.getPlayerList().getPlayer(id);if(sender!=null)CharacterAppearanceNetwork.reject(sender,old.start.request,"Character package upload timed out");
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){UUID id=event.getEntity().getUUID();UPLOADS.remove(id);DOWNLOADS.remove(id);PROCESSING.remove(id);LAST_UPLOAD.remove(id);if(event.getEntity() instanceof ServerPlayer player)refreshPins(player.server);}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){UPLOADS.clear();DOWNLOADS.clear();PROCESSING.clear();LAST_UPLOAD.clear();PINNED.clear();}
    private record Start(CharacterAppearance appearance,long expected,long request,String hash,String wireHash,int size){}
    private record UploadPart(String hash,int index,byte[] bytes){}
    private record Request(String hash){}
    private record DownloadStart(String hash,String wireHash,int size){}
    private record DownloadPart(String hash,int index,byte[] bytes){}
    private record DownloadError(String hash,String reason){}
}
