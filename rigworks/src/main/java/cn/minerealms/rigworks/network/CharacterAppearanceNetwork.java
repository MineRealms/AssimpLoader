package cn.minerealms.rigworks.network;

import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.character.core.CharacterAppearance;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import cn.minerealms.rigworks.character.core.CharacterVisualLedger;
import cn.minerealms.rigworks.character.core.CharacterVisualPackage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Optional, owner-authorized visual configuration relay; vanilla owns gameplay and movement. */
@Mod.EventBusSubscriber(modid=RigWorks.MODID)
public final class CharacterAppearanceNetwork {
    private static final String VERSION="2";
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(RigWorks.MODID,"appearance"),
            ()->VERSION,CharacterAppearanceNetwork::compatible,CharacterAppearanceNetwork::compatible);
    private static final Map<UUID,Long> LAST_UPDATE=new HashMap<>();
    private static final Map<UUID,Selection> METADATA=new HashMap<>();
    private static final java.util.Set<UUID> PRIVATE=new java.util.HashSet<>();
    private static final CharacterCatalogRedelivery REDELIVERY=new CharacterCatalogRedelivery();
    private CharacterAppearanceNetwork(){}
    private static boolean compatible(String version){return VERSION.equals(version)||NetworkRegistry.ABSENT.equals(version)||NetworkRegistry.ACCEPTVANILLA.equals(version);}
    public static void register() {
        CHANNEL.messageBuilder(Selection.class,0,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b)->write(b,p.appearance)).decoder(b->new Selection(read(b)))
                .consumerMainThread(CharacterAppearanceNetwork::select).add();
        CHANNEL.messageBuilder(Update.class,1,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b)->{b.writeUUID(p.player);write(b,p.appearance);b.writeVarLong(p.configRevision);b.writeVarLong(p.request);
                    b.writeUtf(p.digest,64);b.writeUtf(p.packageHash,64);b.writeUUID(p.serverIdentity);})
                .decoder(b->new Update(b.readUUID(),read(b),b.readVarLong(),b.readVarLong(),b.readUtf(64),b.readUtf(64),b.readUUID()))
                .consumerMainThread(CharacterAppearanceNetwork::update).add();
        CHANNEL.messageBuilder(Rejection.class,3,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p,b)->{b.writeVarLong(p.request);b.writeUtf(p.reason,512);})
                .decoder(b->new Rejection(b.readVarLong(),b.readUtf(512)))
                .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->
                        cn.minerealms.rigworks.character.client.CharacterVisualSharing.rejected(p.request,p.reason))).add();
        CharacterPackageNetwork.register(CHANNEL);
        CHANNEL.messageBuilder(SharingPolicy.class,10,NetworkDirection.PLAY_TO_SERVER)
                .encoder((p,b)->b.writeBoolean(p.enabled)).decoder(b->new SharingPolicy(b.readBoolean()))
                .consumerMainThread((p,c)->policy(c.get().getSender(),p.enabled)).add();
    }
    public static boolean available() {
        var connection=net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection!=null&&CHANNEL.isRemotePresent(connection.getConnection());
    }
    public static void sendSelection(CharacterAppearance value){if(available())CHANNEL.sendToServer(new Selection(value));}
    public static void sharingEnabled(boolean enabled){if(available())CHANNEL.sendToServer(new SharingPolicy(enabled));}
    static boolean sharingAllowed(ServerPlayer player){return !PRIVATE.contains(player.getUUID());}
    private static void policy(ServerPlayer sender,boolean enabled) {
        if(sender==null)return;
        if(enabled){PRIVATE.remove(sender.getUUID());return;}
        PRIVATE.add(sender.getUUID());METADATA.remove(sender.getUUID());CharacterPackageNetwork.withdraw(sender);
        var data=CharacterVisualSavedData.get(sender.server);var old=data.ledger.get(sender.getUUID());
        if(data.packages.remove(sender.getUUID())!=null)data.setDirty();CharacterPackageNetwork.refreshPins(sender.server);
        if(old==null)return;
        if(old!=null&&!old.appearance().enabled()&&old.appearance().profile().equals("rigworks:unshared.json")&&old.config()==null)return;
        var hidden=new CharacterAppearance(false,"rigworks:unshared.json","VANILLA",false,0);
        var accepted=data.ledger.metadata(sender.getUUID(),hidden);data.packages.remove(sender.getUUID());data.setDirty();CharacterPackageNetwork.refreshPins(sender.server);broadcast(sender,accepted,0);
    }
    public static boolean share(CharacterAppearance appearance,CharacterVisualConfig config,long expected,long request) {
        if(!available())return false;
        CharacterPackageNetwork.upload(appearance,new CharacterVisualPackage(appearance.profile(),null,config,Map.of()),expected,request);return true;
    }
    private static void select(Selection packet,Supplier<NetworkEvent.Context> supplied) {
        ServerPlayer sender=supplied.get().getSender();if(sender!=null&&sharingAllowed(sender))METADATA.put(sender.getUUID(),packet);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        long now=server.overworld().getGameTime();
        for(var pending:REDELIVERY.due(server.getTickCount(),32)) {
            var viewer=server.getPlayerList().getPlayer(pending.key().viewer());
            var owner=server.getPlayerList().getPlayer(pending.key().owner());
            var current=CharacterVisualSavedData.get(server).ledger.get(pending.key().owner());
            if(viewer!=pending.connection()||owner==null||current==null||viewer!=owner&&viewer.level()!=owner.level()) {
                REDELIVERY.sent(pending.key());continue;
            }
            send(viewer,owner.getUUID(),current,pending.request());
        }
        for(UUID id:java.util.List.copyOf(METADATA.keySet())) {
            if(now-LAST_UPDATE.getOrDefault(id,Long.MIN_VALUE/2)<4)continue;
            ServerPlayer sender=server.getPlayerList().getPlayer(id);Selection packet=METADATA.remove(id);if(sender==null)continue;
            var data=CharacterVisualSavedData.get(server);var old=data.ledger.get(id);
            if(old!=null&&packet.appearance.sameSelection(old.appearance()))continue;
            try {
                var accepted=data.ledger.metadata(id,packet.appearance);
                if(!packet.appearance.enabled()||old!=null&&!old.appearance().profile().equals(packet.appearance.profile()))data.packages.remove(id);
                data.setDirty();CharacterPackageNetwork.refreshPins(server);LAST_UPDATE.put(id,now);broadcast(sender,accepted,0);
            }catch(RuntimeException e){reject(sender,0,message(e));}
        }
    }
    private static String message(RuntimeException e){String s=e.getMessage();return s==null?"Invalid visual configuration":s.substring(0,Math.min(512,s.length()));}
    static void reject(ServerPlayer player,long request,String reason) {
        var current=CharacterVisualSavedData.get(player.server).ledger.get(player.getUUID());
        if(current!=null)send(player,player.getUUID(),current,0);
        try {if(CHANNEL.isRemotePresent(player.connection.connection))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Rejection(request,reason));}
        catch(RuntimeException error){cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Cannot deliver character rejection to {}",player.getUUID(),error);}
    }
    private static void broadcast(ServerPlayer owner,CharacterVisualLedger.Entry entry,long request) {
        for(var viewer:owner.server.getPlayerList().getPlayers())if(viewer==owner||viewer.level()==owner.level())send(viewer,owner.getUUID(),entry,viewer==owner?request:0);
    }
    private static void send(ServerPlayer viewer,UUID owner,CharacterVisualLedger.Entry entry,long request) {
        var key=new CharacterCatalogRedelivery.Key(viewer.getUUID(),owner);
        long response=REDELIVERY.request(key,viewer,request);
        try {
            if(CHANNEL.isRemotePresent(viewer.connection.connection))CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),
                    new Update(owner,entry.appearance(),entry.configRevision(),response,entry.config()==null?"":entry.config().digest(),
                            CharacterVisualSavedData.get(viewer.server).packages.getOrDefault(owner,""),CharacterVisualSavedData.get(viewer.server).identity));
            REDELIVERY.sent(key);
        }catch(RuntimeException error) {
            boolean queued=REDELIVERY.failed(key,viewer,entry.appearance().revision(),response,viewer.server.getTickCount());
            cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Character catalog delivery failed for viewer {} owner {}; {}",
                    viewer.getUUID(),owner,queued?"queued bounded retry":"retry budget exhausted",error);
        }
    }
    private static void update(Update packet,Supplier<NetworkEvent.Context> supplied) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{
            try {
                cn.minerealms.rigworks.character.client.CharacterPackageClient.server(packet.serverIdentity);
                if(cn.minerealms.rigworks.character.client.CharacterVisualSharing.receiveCatalog(packet.player,packet.appearance,packet.configRevision,packet.request,packet.digest))
                    cn.minerealms.rigworks.character.client.CharacterPackageClient.offer(packet.player,packet.packageHash);
            }catch(RuntimeException e){cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Invalid server character configuration; retaining vanilla",e);}
        });
    }
    static void acceptPackage(ServerPlayer sender,CharacterAppearance appearance,CharacterVisualPackage bundle,long expected,long request) {
        if(!sharingAllowed(sender))throw new IllegalArgumentException("Character upload is disabled for this connection");
        if(!appearance.profile().equals(bundle.profile()))throw new IllegalArgumentException("Package profile and selection differ");
        var data=CharacterVisualSavedData.get(sender.server);
        var accepted=data.ledger.commit(sender.getUUID(),appearance,bundle.visual(),expected);
        data.packages.put(sender.getUUID(),bundle.hash());data.setDirty();METADATA.remove(sender.getUUID());CharacterPackageNetwork.refreshPins(sender.server);
        broadcast(sender,accepted,request);
    }
    private static void snapshots(ServerPlayer viewer) {
        var ledger=CharacterVisualSavedData.get(viewer.server).ledger;
        for(var entry:ledger.snapshot().entrySet()) {
            var owner=viewer.server.getPlayerList().getPlayer(entry.getKey());
            if(owner!=null&&(owner==viewer||owner.level()==viewer.level()))send(viewer,entry.getKey(),entry.getValue(),0);
        }
        var self=ledger.get(viewer.getUUID());if(self!=null)broadcast(viewer,self,0);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer player)snapshots(player);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer player)snapshots(player);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer player)snapshots(player);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer viewer&&event.getTarget() instanceof ServerPlayer target) {
            var entry=CharacterVisualSavedData.get(viewer.server).ledger.get(target.getUUID());if(entry!=null)send(viewer,target.getUUID(),entry,0);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){UUID id=event.getEntity().getUUID();LAST_UPDATE.remove(id);METADATA.remove(id);PRIVATE.remove(id);REDELIVERY.disconnect(id);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){LAST_UPDATE.clear();METADATA.clear();PRIVATE.clear();REDELIVERY.clear();}
    private static void write(FriendlyByteBuf b,CharacterAppearance a){b.writeBoolean(a.enabled());b.writeUtf(a.profile(),256);b.writeUtf(a.mode(),16);b.writeBoolean(a.flying());b.writeVarLong(a.revision());}
    private static CharacterAppearance read(FriendlyByteBuf b){return new CharacterAppearance(b.readBoolean(),b.readUtf(256),b.readUtf(16),b.readBoolean(),b.readVarLong());}
    private record Selection(CharacterAppearance appearance){}
    private record SharingPolicy(boolean enabled){}
    private record Update(UUID player,CharacterAppearance appearance,long configRevision,long request,String digest,String packageHash,UUID serverIdentity){}
    private record Rejection(long request,String reason){}
}
