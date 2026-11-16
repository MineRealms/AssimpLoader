package cn.minerealms.assimploader.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.character.core.CharacterProfile;
import cn.minerealms.assimploader.character.core.CharacterEquipmentDefinition;
import cn.minerealms.assimploader.character.core.PlayerAnimationController;
import cn.minerealms.assimploader.character.physics.BulletCharacterPhysics;
import cn.minerealms.assimploader.character.physics.CharacterPhysicsAsset;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.assimploader.client.MinecraftInstanceWorldBinding;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.AsyncModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.AttachmentTrailRenderer;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import com.mojang.math.Axis;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;

/** Optional local world renderer. All mutable state and animation owners live on the client thread. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class LocalPlayerCharacter {
    public static final String DEFAULT_PROFILE = "assimploader:models/character/block_player/profile.json";
    public enum Status { DISABLED, WAITING_FOR_PLAYER, LOADING, READY, FAILED }
    public record Settings(boolean enabled, String profile, boolean groundPlacement, boolean effects) {}
    public record Diagnostics(Status status, String profile, String model, String requestedAnimation,
                              String selectedAnimation, long renderedPasses, long fallbacks, String failure) {}

    private static volatile Settings settings = new Settings(false, DEFAULT_PROFILE, true, false);
    private static Settings appliedSettings;
    private static Binding active;
    private static LocalPlayer observedPlayer;
    private static Object observedLevel;
    private static Status status = Status.DISABLED;
    private static String failure = "";
    private static long request, loadingStarted, renderedPasses, fallbacks;
    private static boolean worldRender;
    private static Boolean ragdollOverride;
    private static final BaseAssimpRenderer RENDERER = new BaseAssimpRenderer() {
        @Override protected String getModelName() { return "local-player-character"; }
    };

    static {
        AssimpModelLoader.addCacheInvalidationListener(() -> Minecraft.getInstance().execute(LocalPlayerCharacter::reload));
    }

    private LocalPlayerCharacter() {}

    private static final class Binding implements AutoCloseable {
        final Object animationOwner = new Object();
        final LocalPlayer player;
        final CharacterProfile profile;
        final AssimpScene scene;
        final AnimationState animation;
        final PlayerAnimationController controller;
        final BulletCharacterPhysics physics;
        final CharacterPhysicsClientBinding physicsBinding;
        final CharacterRigDefinition.Installed rig;
        final CharacterEquipmentDefinition equipment;
        final MinecraftPlayerInputSampler inputs = new MinecraftPlayerInputSampler();

        Binding(LocalPlayer player, CharacterProfile profile, AssimpScene scene) {
            this.player = player;
            this.profile = profile;
            this.scene = scene;
            animation = ClientAnimationManager.getInstance().getOrCreateAnimationState(animationOwner, profile.model(), scene);
            if (animation == null) throw new IllegalArgumentException("Character needs a skeleton, animation or instance definition");
            try {
                controller = new PlayerAnimationController(profile, animation);
                var resources=Minecraft.getInstance().getResourceManager();
                if (profile.equipmentDefinition() != null) {
                    try (var reader = resources.openAsReader(new ResourceLocation(profile.equipmentDefinition()))) {
                        equipment = CharacterEquipmentDefinition.read(reader);
                    }
                } else equipment = CharacterEquipmentDefinition.defaults(profile);
                for (var mount : new CharacterEquipmentDefinition.Mount[]{equipment.rightHand(), equipment.leftHand()}) {
                    if (mount != null && scene.findNodeByName(mount.node()) == null)
                        throw new IllegalArgumentException("Unknown equipment node: " + mount.node());
                }
                if(profile.solverDefinition()!=null) {
                    try(var reader=resources.openAsReader(new ResourceLocation(profile.solverDefinition()))) {
                        rig=CharacterRigDefinition.read(reader).install(animation);
                    }
                } else rig=null;
                if(profile.physicsDefinition()!=null && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.character.physicsEnabled::get,true)) {
                    try(var reader=resources.openAsReader(new ResourceLocation(profile.physicsDefinition()))) {
                        physics=new BulletCharacterPhysics(CharacterPhysicsAsset.read(reader,scene),scene,(float)profile.scale());
                    }
                    physicsBinding=new CharacterPhysicsClientBinding(physics);
                    try {
                        physicsBinding.update(player,profile.modelYawDegrees());
                        animation.addModifier("character:physics",physics);
                    } catch(RuntimeException exception) {physics.close();throw exception;}
                } else {physics=null;physicsBinding=null;}
                if (animation.getModelInstance() != null) {
                    MinecraftInstanceWorldBinding.bind(animation.getModelInstance(), player,
                            value -> rotationScale(value.yBodyRot, profile),
                            value -> settings.groundPlacement(), value -> settings.effects());
                }
                controller.update(inputs.sample(player));
            } catch (IOException | RuntimeException exception) {
                ClientAnimationManager.getInstance().release(animationOwner);
                throw new IllegalArgumentException("Cannot initialize character solver/physics",exception);
            }
        }

        @Override public void close() {
            controller.close();
            ClientAnimationManager.getInstance().release(animationOwner);
        }
    }

    /** Current local playback is exposed for client diagnostics and character extensions, never server authority. */
    public static AnimationState currentAnimation() { return active == null ? null : active.animation; }
    public static ModelInstance currentInstance() { return active == null ? null : active.animation.getModelInstance(); }
    public static CharacterProfile currentProfile() { return active == null ? null : active.profile; }
    public static BulletCharacterPhysics currentPhysics() { return active == null ? null : active.physics; }
    public static CharacterRigDefinition.Installed currentRig() { return active == null ? null : active.rig; }
    public static Settings settings() { return settings; }
    public static void configure(Settings value) {
        java.util.Objects.requireNonNull(value);
        new ResourceLocation(value.profile());
        settings=value;
    }
    public static void setRagdollOverride(Boolean value) { ragdollOverride=value; }
    static Boolean ragdollOverride() { return ragdollOverride; }

    public static Diagnostics diagnostics() {
        var selection = active == null ? null : active.controller.lastSelection();
        return new Diagnostics(status, settings.profile(), active == null ? "" : active.profile.model(),
                selection == null || selection.requested() == null ? "" : selection.requested().key(),
                selection == null || selection.animation() == null ? "" : selection.animation(),
                renderedPasses, fallbacks, failure);
    }

    public static void refreshConfiguration() {
        var config = AssimpLoaderConfig.CLIENT.character;
        settings = new Settings(AssimpLoaderConfig.getOrDefault(config.enabled::get, false),
                AssimpLoaderConfig.getOrDefault(config.profile::get, DEFAULT_PROFILE),
                AssimpLoaderConfig.getOrDefault(config.groundPlacement::get, true),
                AssimpLoaderConfig.getOrDefault(config.effects::get, false));
    }

    /** Client-thread reset. A stale native decode may finish, but cannot publish or revive this owner. */
    public static void reload() {
        request++;
        retire();
        failure = "";
        status = settings.enabled() ? Status.WAITING_FOR_PLAYER : Status.DISABLED;
    }

    private static void retire() {
        if (active != null) active.close();
        active = null;
        ragdollOverride=null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        Settings current = settings;
        boolean resourceChanged = appliedSettings == null || appliedSettings.enabled() != current.enabled()
                || !appliedSettings.profile().equals(current.profile());
        appliedSettings = current;
        if (resourceChanged || observedPlayer != minecraft.player || observedLevel != minecraft.level) {
            observedPlayer = minecraft.player;
            observedLevel = minecraft.level;
            reload();
        }
        if (!current.enabled()) return;
        if (minecraft.player == null || minecraft.level == null) { status = Status.WAITING_FOR_PLAYER; return; }
        if (active != null && active.animation.isClosed()) reload();
        if (status == Status.WAITING_FOR_PLAYER) startLoad(minecraft.player);
        if (status == Status.LOADING && System.nanoTime() - loadingStarted > TimeUnit.SECONDS.toNanos(60)) {
            request++;
            fail("Character loading timed out; use /assimploader character reload to retry", null);
        }
        if (active == null || minecraft.isPaused()) return;
        try {
            if(active.physicsBinding!=null)active.physicsBinding.update(active.player,active.profile.modelYawDegrees());
            active.controller.update(active.inputs.sample(active.player));
            // The existing NORMAL-priority ClientAnimationManager tick advances simulation exactly once.
            ClientAnimationManager.getInstance().markRendered(active.animation, 0);
        } catch (RuntimeException exception) {
            fail("Character input or animation selection failed", exception);
        }
    }

    private static void startLoad(LocalPlayer player) {
        status = Status.LOADING;
        loadingStarted = System.nanoTime();
        long token = ++request;
        long generation = AssimpModelLoader.getCacheGeneration();
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var manager = minecraft.getResourceManager();
        String requestedProfile = settings.profile();
        try {
            ResourceLocation profileLocation = new ResourceLocation(requestedProfile);
            CharacterProfile profile;
            try (var reader = manager.openAsReader(profileLocation)) {
                char[] text = new char[65537];
                int size = 0, read;
                while (size < text.length && (read = reader.read(text, size, text.length - size)) != -1) size += read;
                if (size == text.length) throw new IOException("Character profile exceeds 64 KiB");
                profile = CharacterProfile.read(new StringReader(new String(text, 0, size)));
            }
            ResourceLocation model = new ResourceLocation(profile.model());
            AsyncModelLoader.decodeAsync("character:" + model + "@" + generation,
                    () -> AssimpModelLoader.loadCachedResource(manager, model, ModelImportMode.DYNAMIC))
                    .whenComplete((scene, exception) -> minecraft.execute(() -> {
                        if (token != request || !settings.enabled() || !settings.profile().equals(requestedProfile)
                                || minecraft.player != player || minecraft.level != level) return;
                        if (generation != AssimpModelLoader.getCacheGeneration()) { reload(); return; }
                        if (exception != null) { fail("Cannot load character model " + model, exception); return; }
                        try {
                            if (scene == null || scene.getMeshes().isEmpty()) throw new IOException("Character model contains no meshes");
                            ClientRenderResources.checkGeneration();
                            Binding replacement = new Binding(player, profile, scene);
                            retire();
                            active = replacement;
                            failure = "";
                            status = Status.READY;
                            AssimpLoader.LOGGER.info("Local character ready: {} ({})", model, settings.profile());
                        } catch (IOException | RuntimeException initializationFailure) {
                            fail("Cannot initialize character " + model, initializationFailure);
                        }
                    }));
        } catch (IOException | RuntimeException exception) {
            fail("Cannot load character profile " + settings.profile(), exception);
        }
    }

    private static void fail(String message, Throwable exception) {
        retire();
        failure = message;
        status = Status.FAILED;
        fallbacks++;
        if (exception == null) AssimpLoader.LOGGER.warn(message);
        else AssimpLoader.LOGGER.warn(message, exception);
    }

    @SubscribeEvent public static void renderTick(TickEvent.RenderTickEvent event) { worldRender = false; }

    @SubscribeEvent public static void renderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) worldRender = true;
        else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) worldRender = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderPlayer(RenderPlayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!settings.enabled() || !worldRender || active == null || event.getEntity() != minecraft.player
                || active.player != minecraft.player || minecraft.options.getCameraType().isFirstPerson()
                || active.player.isInvisible() || active.player.isSpectator() || active.player.isRemoved()) return;
        ClientRenderResources.checkGeneration();
        if (active == null || active.animation.isClosed()) return;
        var binding = active;
        var poses = event.getPoseStack();
        poses.pushPose();
        try {
            float yaw = Mth.rotLerp(event.getPartialTick(), binding.player.yBodyRotO, binding.player.yBodyRot);
            poses.mulPose(Axis.YP.rotationDegrees(binding.profile.modelYawDegrees() - yaw));
            float scale = (float) binding.profile.scale();
            poses.scale(scale, scale, scale);
            int overlay = LivingEntityRenderer.getOverlayCoords(binding.player, 0);
            var snapshot = binding.animation.getRenderPose(event.getPartialTick());
            if (!OculusEntityBufferCache.renderPosed(binding.animation, snapshot, poses, event.getPackedLight(), overlay, false)) {
                RENDERER.renderScene(binding.scene, binding.animation, poses, event.getMultiBufferSource(),
                        event.getPackedLight(), overlay, event.getPartialTick());
            }
            CharacterHeldItemRenderer.render(binding.player, binding.equipment, snapshot, poses,
                    event.getMultiBufferSource(), event.getPackedLight(), overlay);
            if (binding.animation.getModelInstance() != null) {
                var world = rotationScale(yaw, binding.profile);
                world.setTranslation((float) Mth.lerp(event.getPartialTick(), binding.player.xo, binding.player.getX()),
                        (float) Mth.lerp(event.getPartialTick(), binding.player.yo, binding.player.getY()),
                        (float) Mth.lerp(event.getPartialTick(), binding.player.zo, binding.player.getZ()));
                AttachmentTrailRenderer.render(binding.animation.getModelInstance(), poses, event.getMultiBufferSource(),
                        event.getPackedLight(), world);
            }
            renderedPasses++;
            event.setCanceled(true);
        } catch (RuntimeException exception) {
            fail("Character rendering failed; restored vanilla player", exception);
        } finally {
            poses.popPose();
        }
    }

    private static Matrix4f rotationScale(float bodyYaw, CharacterProfile profile) {
        return new Matrix4f().rotateY((float) Math.toRadians(profile.modelYawDegrees() - bodyYaw)).scale((float) profile.scale());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reload();
        observedPlayer = null;
        observedLevel = null;
        worldRender = false;
    }
    @SubscribeEvent public static void shutdown(GameShuttingDownEvent event) { reload(); }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimploader").then(Commands.literal("character")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(diagnostics().toString()), false);
                    if(currentPhysics()!=null)context.getSource().sendSuccess(()->Component.literal(currentPhysics().statistics().toString()),false);
                    return 1;
                }).then(Commands.literal("enable").executes(context -> {setPersistentEnabled(true);return 1;}))
                .then(Commands.literal("disable").executes(context -> {setPersistentEnabled(false);return 1;}))
                .then(Commands.literal("profile").then(Commands.argument("resource",net.minecraft.commands.arguments.ResourceLocationArgument.id()).executes(context->{
                    var config=AssimpLoaderConfig.CLIENT.character.profile;
                    config.set(net.minecraft.commands.arguments.ResourceLocationArgument.getId(context,"resource").toString());config.save();
                    refreshConfiguration();reload();return 1;
                })))
                .then(Commands.literal("ragdoll")
                    .then(Commands.literal("on").executes(context->{setRagdollOverride(true);return 1;}))
                    .then(Commands.literal("off").executes(context->{setRagdollOverride(false);return 1;}))
                    .then(Commands.literal("auto").executes(context->{setRagdollOverride(null);return 1;})))
                .then(Commands.literal("morph").then(Commands.argument("name",StringArgumentType.string())
                    .suggests((context,builder)->net.minecraft.commands.SharedSuggestionProvider.suggest(currentRig()==null?java.util.Set.of():currentRig().morphs().names(),builder))
                    .then(Commands.argument("weight",FloatArgumentType.floatArg(-4,4)).executes(context->{
                        if(currentRig()==null)return 0;
                        String name=StringArgumentType.getString(context,"name");
                        if(!currentRig().morphs().names().contains(name)) {context.getSource().sendFailure(Component.literal("Unknown character morph: "+name));return 0;}
                        currentRig().morphs().setWeight(name,FloatArgumentType.getFloat(context,"weight"));return 1;
                    }))))
                .then(Commands.literal("reload").executes(context -> {
                    refreshConfiguration();
                    reload();
                    context.getSource().sendSuccess(() -> Component.literal("Character resources will reload on the next client tick"), false);
                    return 1;
                }))));
    }

    private static void setPersistentEnabled(boolean enabled) {
        var config=AssimpLoaderConfig.CLIENT.character.enabled;config.set(enabled);config.save();
        refreshConfiguration();reload();
    }

    @Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void registerReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(LocalPlayerCharacter::reload));
        }
        @SubscribeEvent public static void loaded(ModConfigEvent.Loading event) { changed(event); }
        @SubscribeEvent public static void reloaded(ModConfigEvent.Reloading event) { changed(event); }
        private static void changed(ModConfigEvent event) {
            if (event.getConfig().getSpec() == AssimpLoaderConfig.CLIENT_SPEC) refreshConfiguration();
        }
    }
}
