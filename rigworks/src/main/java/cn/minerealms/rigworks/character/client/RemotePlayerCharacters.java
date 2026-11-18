package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.assimploader.client.MinecraftInstanceWorldBinding;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.AsyncModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.RigWorksConfig;
import cn.minerealms.rigworks.character.core.CharacterAppearance;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import cn.minerealms.rigworks.character.core.PlayerAnimationController;
import cn.minerealms.rigworks.network.CharacterAppearanceNetwork;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-owned remote playback, bounded by visibility distance and owner count. */
@Mod.EventBusSubscriber(modid = RigWorks.MODID, value = Dist.CLIENT)
public final class RemotePlayerCharacters {
    private static final Map<UUID, CharacterAppearance> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Binding> BINDINGS = new HashMap<>();
    private static final Map<UUID, Long> PENDING = new HashMap<>();
    private static final Map<UUID, Long> RETRY_AFTER = new HashMap<>();
    private static final Map<UUID,String> FAILURES=new HashMap<>();
    private static final java.util.Set<UUID> RELOAD_REQUESTED=new java.util.HashSet<>();
    private static final BaseAssimpRenderer RENDERER = new BaseAssimpRenderer() {
        @Override protected String getModelName() { return "remote-player-character"; }
    };
    private static Object level;
    private static CharacterAppearance announced;
    private static Boolean announcedUpload;
    private static long request, localRevision, nextAnnounce;
    private static long draws;

    private RemotePlayerCharacters() {}
    static {
        AssimpModelLoader.addCacheInvalidationListener(() -> Minecraft.getInstance().execute(RemotePlayerCharacters::reload));
    }

    public static void receive(UUID player, CharacterAppearance appearance) {
        var currentLevel = Minecraft.getInstance().level;
        if (level != currentLevel) { changeLevel(currentLevel); }
        var old = SELECTIONS.get(player);
        if (old != null && appearance.revision() <= old.revision()) return;
        if (SELECTIONS.size() >= 512 && old == null) return;
        SELECTIONS.put(player, appearance);
        var binding=BINDINGS.get(player);
        if (!appearance.enabled() || old!=null&&!old.profile().equals(appearance.profile()))retire(player);
        else if(binding!=null&&!java.util.Objects.equals(binding.config,CharacterVisualSharing.accepted(player)))RELOAD_REQUESTED.add(player);
        RETRY_AFTER.remove(player);
    }

    /** Client-only explicit assignment also permits previews on servers without the optional relay. */
    public static void assign(UUID player, String profile, String mode) {
        receive(player, new CharacterAppearance(true, profile, mode, false,
                Math.max(++localRevision, SELECTIONS.containsKey(player) ? SELECTIONS.get(player).revision() + 1 : 1)));
    }

    public static LocalPlayerCharacter.RenderView view(AbstractClientPlayer player, float partialTick) {
        Binding binding = BINDINGS.get(player.getUUID());
        if (binding == null || binding.player != player || binding.animation.isClosed()) return null;
        return new LocalPlayerCharacter.RenderView(player, binding.profile, binding.scene, binding.animation,
                binding.equipment, binding.animation.getRenderPose(partialTick));
    }
    static boolean hasUsableBinding(UUID owner) {
        var binding=BINDINGS.get(owner);var client=Minecraft.getInstance();var selection=SELECTIONS.get(owner);
        return binding!=null&&selection!=null&&selection.enabled()&&selection.profile().equals(binding.profileId)
                &&binding.player.level()==client.level&&client.level!=null&&client.level.getPlayerByUUID(owner)==binding.player
                &&!binding.player.isRemoved()&&!binding.animation.isClosed()&&binding.generation==AssimpModelLoader.getCacheGeneration();
    }

    public static Map<String, Object> diagnostics() {
        return Map.of("selections", SELECTIONS.size(), "bindings", BINDINGS.size(), "pending", PENDING.size(), "draws", draws,"failures",Map.copyOf(FAILURES));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (level != client.level) { changeLevel(client.level); }
        if (client.level == null || client.player == null) return;
        boolean upload=CharacterVisualSharing.uploadEnabled();
        if(announcedUpload==null||announcedUpload!=upload) {CharacterAppearanceNetwork.sharingEnabled(upload);announcedUpload=upload;announced=null;}
        if (client.player.tickCount >= nextAnnounce) {
            nextAnnounce = client.player.tickCount + 5;
            var settings = LocalPlayerCharacter.settings();
            var selected = new CharacterAppearance(settings.enabled(), settings.profile(), LocalPlayerCharacter.animationMode().name(),
                    client.player.getAbilities().flying, ++localRevision);
            if (upload&&!selected.sameSelection(announced)) {
                CharacterAppearanceNetwork.sendSelection(selected);
                announced = selected;
            }
        }
        if (!AssimpLoaderConfig.getOrDefault(RigWorksConfig.CLIENT.remotePlayers::get, true)) { reload(); return; }
        int radius = AssimpLoaderConfig.getIntOrDefault(RigWorksConfig.CLIENT.remoteDistance, 96);
        int limit = AssimpLoaderConfig.getIntOrDefault(RigWorksConfig.CLIENT.remotePlayerLimit, 32);
        var wanted = client.level.players().stream().filter(player -> player != client.player && !player.isRemoved())
                .filter(player -> player.distanceToSqr(client.player) <= (double) radius * radius)
                .filter(player -> SELECTIONS.containsKey(player.getUUID()) && SELECTIONS.get(player.getUUID()).enabled())
                .filter(player -> CharacterVisualSharing.acceptedDigest(player.getUUID()).isEmpty()||CharacterVisualSharing.accepted(player.getUUID())!=null||hasUsableBinding(player.getUUID()))
                .sorted(Comparator.comparingDouble(player -> player.distanceToSqr(client.player))).limit(limit).toList();
        var retained = wanted.stream().map(AbstractClientPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        PENDING.keySet().removeIf(id -> !retained.contains(id));
        for (var id : java.util.List.copyOf(BINDINGS.keySet())) {
            var binding = BINDINGS.get(id);
            if (!retained.contains(id) || binding.player.isRemoved() || binding.player.level() != client.level
                    || binding.animation.isClosed() || client.level.getPlayerByUUID(id) != binding.player) retire(id);
        }
        for (var player : wanted) {
            Binding binding = BINDINGS.get(player.getUUID());
            if (binding == null) {
                if (!PENDING.containsKey(player.getUUID()) && client.player.tickCount >= RETRY_AFTER.getOrDefault(player.getUUID(), 0L))
                    load(player, SELECTIONS.get(player.getUUID()));
                continue;
            }
            if((RELOAD_REQUESTED.contains(player.getUUID())||!java.util.Objects.equals(binding.config,CharacterVisualSharing.accepted(player.getUUID())))
                    &&!PENDING.containsKey(player.getUUID())&&client.player.tickCount>=RETRY_AFTER.getOrDefault(player.getUUID(),0L)) {
                load(player,SELECTIONS.get(player.getUUID()));
                binding=BINDINGS.get(player.getUUID());if(binding==null)continue;
            }
            if (client.isPaused()) continue;
            try {
                var appearance = SELECTIONS.get(player.getUUID());
                var input = binding.inputs.sample(player, appearance.flying());
                if (binding.contacts != null) binding.contacts.prepare(player,binding.visualOptions.handContacts());
                if ("VANILLA".equals(appearance.mode())) {
                    binding.avatar.reset();
                    binding.controller.reset(); binding.vanilla.update(player, cn.minerealms.rigworks.character.core.VanillaPoseSelection.ALL, appearance.flying());
                } else if("SCRIPTED".equals(appearance.mode())&&binding.avatar.present()) {
                    binding.vanilla.disable();
                    if(!binding.avatar.run(input))binding.vanilla.update(player,binding.controller.updateCompatible(input),appearance.flying());
                } else {
                    binding.avatar.reset();
                    binding.vanilla.update(player, binding.controller.updateCompatible(input), appearance.flying());
                }
                if(binding.physicsBinding!=null)binding.physicsBinding.update(player,binding.profile.modelYawDegrees());
            } catch (RuntimeException error) {
                AssimpLoader.LOGGER.warn("Remote character failed; retaining vanilla player {}", player.getUUID(), error);
                RETRY_AFTER.put(player.getUUID(), (long) client.player.tickCount + 200); retire(player.getUUID());
            }
        }
    }

    private static void load(AbstractClientPlayer player, CharacterAppearance appearance) {
        Minecraft client = Minecraft.getInstance();
        long token = ++request, generation = AssimpModelLoader.getCacheGeneration();
        var expectedLevel = client.level;
        var resources = client.getResourceManager();
        PENDING.put(player.getUUID(), token);
        try {
            CharacterProfile profile;
            CharacterVisualConfig config=CharacterVisualSharing.accepted(player.getUUID());
            if(config!=null)profile=config.profile();
            else try (var reader = resources.openAsReader(new ResourceLocation(appearance.profile()))) {
                char[] text = new char[65537]; int size = 0, read;
                while (size < text.length && (read = reader.read(text, size, text.length - size)) != -1) size += read;
                if (size == text.length) throw new IOException("Character profile exceeds 64 KiB");
                profile = CharacterProfile.read(new java.io.StringReader(new String(text, 0, size)));
            }
            var model = new ResourceLocation(profile.model());
            AsyncModelLoader.decodeAsync("character:" + model + "@" + generation,
                    () -> {
                        String hash=CharacterVisualSharing.modelHash(resources,profile.model());
                        if(config!=null&&!hash.equals(config.modelHash()))throw new IOException("Remote character asset hash mismatch: "+model);
                        return CharacterVisualSharing.loadModel(resources,model);
                    })
                    .whenComplete((scene, error) -> client.execute(() -> {
                        if (!java.util.Objects.equals(PENDING.get(player.getUUID()), token)) return;
                        PENDING.remove(player.getUUID());
                        var current = SELECTIONS.get(player.getUUID());
                        if (client.level != expectedLevel || client.player == null || player.isRemoved()
                                || client.level.getPlayerByUUID(player.getUUID()) != player || current == null || !current.enabled()
                                || !current.profile().equals(appearance.profile()) || !current.mode().equals(appearance.mode())
                                || !java.util.Objects.equals(config,CharacterVisualSharing.accepted(player.getUUID()))
                                || generation != AssimpModelLoader.getCacheGeneration()) return;
                        if (error != null) { failedLoad(player, appearance, error); return; }
                        try {
                            if (scene == null || scene.getMeshes().isEmpty()) throw new IOException("Character has no meshes");
                            ClientRenderResources.checkGeneration();
                            Binding replacement=new Binding(player, profile, scene, config,appearance.profile());
                            Binding previous=BINDINGS.put(player.getUUID(),replacement);if(previous!=null)previous.close();
                            RELOAD_REQUESTED.remove(player.getUUID());
                            FAILURES.remove(player.getUUID());
                            CharacterPackageClient.applicationReady(player.getUUID());
                        } catch (IOException | RuntimeException failure) { failedLoad(player, appearance, failure); }
                    }));
        } catch (IOException | RuntimeException error) {
            PENDING.remove(player.getUUID()); failedLoad(player, appearance, error);
        }
    }

    private static void failedLoad(AbstractClientPlayer player, CharacterAppearance appearance, Throwable error) {
        var local = Minecraft.getInstance().player;
        RETRY_AFTER.put(player.getUUID(), local == null ? 200L : (long) local.tickCount + 200);
        String reason=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
        CharacterPackageClient.applicationFailed(player.getUUID(),reason);
        FAILURES.put(player.getUUID(),CharacterPackageClient.applicationStatus(player.getUUID()));
        AssimpLoader.LOGGER.warn("Cannot load remote profile {} for {}; {}", appearance.profile(),player.getUUID(),FAILURES.get(player.getUUID()),error);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        if (player == Minecraft.getInstance().player || !LocalPlayerCharacter.isWorldRender()
                || player.isInvisible() || player.isSpectator()) return;
        var view = view(player, event.getPartialTick());
        if (view == null || view.snapshot() == null) return;
        var poses = event.getPoseStack(); poses.pushPose();
        try {
            var offset = VanillaPlayerPoseDriver.renderOffset(player);
            poses.translate(offset.x, offset.y, offset.z);
            float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
            poses.mulPose(Axis.YP.rotationDegrees(view.profile().modelYawDegrees() - yaw));
            float scale = (float) view.profile().scale(); poses.scale(scale, scale, scale);
            var binding=BINDINGS.get(player.getUUID());var config=binding==null?null:binding.config;
            if(config!=null)LocalPlayerCharacter.applyVisualRoot(poses,config.transform());
            int overlay = net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(player, 0);
            if (!OculusEntityBufferCache.renderPosed(view.animation(), view.snapshot(), poses, event.getPackedLight(), overlay, false))
                RENDERER.renderScene(view.scene(), view.animation(), poses, event.getMultiBufferSource(), event.getPackedLight(), overlay, event.getPartialTick());
            CharacterHeldItemRenderer.render(player, view.profile(), view.equipment(), view.snapshot(), poses, event.getMultiBufferSource(), event.getPackedLight(), overlay);
            LocalPlayerCharacter.renderEquipmentLayers(view, poses, event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
            ClientAnimationManager.getInstance().markRendered(view.animation(), player.distanceToSqr(Minecraft.getInstance().player));
            draws++; event.setCanceled(true);
        } catch (RuntimeException error) {
            retire(player.getUUID()); failedLoad(player, SELECTIONS.get(player.getUUID()), error);
        } finally { poses.popPose(); }
    }

    private static Matrix4f rotation(AbstractClientPlayer player, CharacterProfile profile,CharacterVisualConfig config) {
        var matrix=new Matrix4f().rotateY((float) Math.toRadians(profile.modelYawDegrees() - player.yBodyRot)).scale((float) profile.scale());
        if(config!=null)matrix.mul(config.transform().matrix());return matrix;
    }
    private static void retire(UUID id) {
        PENDING.remove(id);
        RELOAD_REQUESTED.remove(id);
        var binding = BINDINGS.remove(id); if (binding != null) binding.close();
    }
    public static void reload() {
        request++; PENDING.clear(); BINDINGS.values().forEach(Binding::close); BINDINGS.clear(); RETRY_AFTER.clear();RELOAD_REQUESTED.clear();
    }
    public static void reload(UUID id){if(hasUsableBinding(id)){PENDING.remove(id);RELOAD_REQUESTED.add(id);}else retire(id);RETRY_AFTER.remove(id);FAILURES.remove(id);}
    private static void clear() { reload(); SELECTIONS.clear(); FAILURES.clear(); announced = null; announcedUpload=null;nextAnnounce = 0; }
    private static void changeLevel(Object value){clear();level=value;if(value!=null)SELECTIONS.putAll(CharacterVisualSharing.selections());}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); level = null; }

    private static final class Binding implements AutoCloseable {
        final AbstractClientPlayer player;
        final String profileId;
        final long generation=AssimpModelLoader.getCacheGeneration();
        final CharacterProfile profile;
        final AssimpScene scene;
        final AnimationState animation;
        final PlayerAnimationController controller;
        final VanillaPlayerPoseDriver vanilla;
        final CharacterEquipmentDefinition equipment;
        final CharacterHandContacts contacts;
        final CharacterVisualConfig config;
        final CharacterVisualConfig.Options visualOptions;
        final cn.minerealms.assimploader.character.physics.BulletCharacterPhysics physics;
        final CharacterPhysicsClientBinding physicsBinding;
        final CharacterRigDefinition.Installed rig;
        final OwnerAvatarSession avatar;
        final MinecraftPlayerInputSampler inputs = new MinecraftPlayerInputSampler();

        Binding(AbstractClientPlayer player, CharacterProfile profile, AssimpScene scene, CharacterVisualConfig config,String profileId) throws IOException {
            this.player = player; this.profile = profile; this.scene = scene;this.profileId=profileId;
            this.config=config;
            visualOptions=config==null?new CharacterVisualConfig.Options(LocalPlayerCharacter.handContactsEnabled(),LocalPlayerCharacter.physicsEnabled(),true,false):config.options();
            animation = ClientAnimationManager.getInstance().getOrCreateAnimationState(this, profile.model(), scene);
            if (animation == null) throw new IOException("Character needs a skeleton or animation");
            try {
                controller = new PlayerAnimationController(profile, animation);
                vanilla = new VanillaPlayerPoseDriver(profile, animation);
                var resources = Minecraft.getInstance().getResourceManager();
                if(config!=null&&!config.modelHash().equals(CharacterVisualSharing.cachedModelHash(resources,profile.model())))
                    throw new IOException("Remote character model hash differs from server accepted version");
                if(config!=null)equipment=config.equipment();
                else if (profile.equipmentDefinition() != null) {
                    try (var reader = resources.openAsReader(new ResourceLocation(profile.equipmentDefinition()))) {
                        equipment = CharacterEquipmentDefinition.read(reader);
                    }
                } else equipment = CharacterEquipmentDefinition.defaults(profile);
                var mounts=new java.util.ArrayList<CharacterEquipmentDefinition.Mount>();
                mounts.add(equipment.rightHand());mounts.add(equipment.leftHand());mounts.addAll(equipment.sockets().values());
                for(var rule:equipment.overrides())if(rule.mount()!=null)mounts.add(rule.mount());
                for(var mount:mounts)if(mount!=null&&scene.findNodeByName(mount.node())==null)
                    throw new IllegalArgumentException("Unknown equipment node: "+mount.node());
                if(config!=null&&config.rig()!=null)rig=CharacterRigDefinition.read(new java.io.StringReader(config.rig())).install(animation);
                else if (config==null&&profile.solverDefinition() != null) {
                    try (var reader = resources.openAsReader(new ResourceLocation(profile.solverDefinition()))) {
                        rig=CharacterRigDefinition.read(reader).install(animation);
                    }
                }else rig=null;
                contacts = equipment.grips().isEmpty() ? null : new CharacterHandContacts(profile, equipment, scene);
                if (contacts != null) animation.addModifier("rigworks:hand_contacts", contacts);
                String physicsSource=config!=null?config.physics():profile.physicsDefinition()==null?null:CharacterVisualSharing.resource(resources,profile.physicsDefinition());
                if(physicsSource!=null&&visualOptions.physics()) {
                    physics=new cn.minerealms.assimploader.character.physics.BulletCharacterPhysics(
                            cn.minerealms.assimploader.character.physics.CharacterPhysicsAsset.read(new java.io.StringReader(physicsSource),scene),scene,(float)profile.scale());
                    physicsBinding=new CharacterPhysicsClientBinding(physics);animation.addModifier("character:physics",physics);
                }else{physics=null;physicsBinding=null;}
                avatar=new OwnerAvatarSession(player,profileId,profile,animation,rig,contacts,
                        ()->{controller.reset();vanilla.update(player,cn.minerealms.rigworks.character.core.VanillaPoseSelection.ALL,inputs.sample(player).flying());},
                        ()->{var input=inputs.sample(player);vanilla.update(player,controller.updateCompatible(input),input.flying());});
                var instance = animation.getModelInstance();
                if (instance != null) MinecraftInstanceWorldBinding.bind(instance, player, value -> rotation(value, profile,config),
                        value -> visualOptions.groundPlacement(), value -> visualOptions.effects());
                vanilla.update(player, true);
            } catch (IOException | RuntimeException error) {
                ClientAnimationManager.getInstance().release(this); throw error;
            }
        }
        @Override public void close() {
            avatar.close();
            controller.close(); vanilla.close(); ClientAnimationManager.getInstance().release(this);
        }
    }
}
