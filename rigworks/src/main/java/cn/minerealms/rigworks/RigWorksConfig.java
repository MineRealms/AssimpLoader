package cn.minerealms.rigworks;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.IConfigEvent;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import cn.minerealms.rigworks.config.RuntimeConfigFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Client visual policy owned by RigWorks; no server configuration or simulation. */
public final class RigWorksConfig {
    public enum CharacterAnimationMode { VANILLA, SCRIPTED, CLIPS }
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;
    private static RuntimeConfigFile runtime;
    static {
        var builder = new ForgeConfigSpec.Builder();
        CLIENT = new ClientConfig(builder);
        CLIENT_SPEC = builder.build();
    }
    private RigWorksConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "rigworks-client.toml");
    }

    public static synchronized void attachClient(ModConfig config, Executor clientExecutor) {
        if (config.getSpec() != CLIENT_SPEC) return;
        if (!(config.getConfigData() instanceof CommentedFileConfig file))
            throw new IllegalStateException("RigWorks client configuration is not file backed");
        try {
            runtime = new RuntimeConfigFile(file, CLIENT_SPEC::isCorrect, CLIENT_SPEC::correct,
                    CLIENT_SPEC::afterReload, () -> ModList.get().getModContainerById(RigWorks.MODID)
                            .orElseThrow().dispatchConfigEvent(IConfigEvent.reloading(config)));
            runtime.watch(clientExecutor, error -> org.apache.logging.log4j.LogManager.getLogger()
                    .warn("Could not reload RigWorks client configuration; retaining the last valid settings", error));
        } catch (IOException error) { throw new UncheckedIOException("Could not initialize RigWorks client settings", error); }
    }

    /** All in-game preference writes must use one complete-file transaction. */
    public static synchronized void update(Consumer<Edit> change) {
        if (runtime == null) throw new IllegalStateException("RigWorks client configuration has not loaded");
        try { runtime.update(config -> change.accept(new Edit(config))); }
        catch (IOException error) { throw new UncheckedIOException("Could not save RigWorks client settings", error); }
    }

    public static final class Edit {
        private final CommentedConfig config;
        private Edit(CommentedConfig config) { this.config = config; }
        public <T> Edit set(ForgeConfigSpec.ConfigValue<T> key, T value) {
            if (CLIENT_SPEC.getValues().getRaw(key.getPath()) != key)
                throw new IllegalArgumentException("Configuration key is not owned by RigWorks");
            config.set(key.getPath(), value);
            return this;
        }
    }

    public static final class ClientConfig {
        public final ForgeConfigSpec.BooleanValue enabled, groundPlacement, effects, handContacts;
        public final ForgeConfigSpec.ConfigValue<String> profile;
        public final ForgeConfigSpec.EnumValue<CharacterAnimationMode> animationMode;
        public final ForgeConfigSpec.BooleanValue physicsEnabled, physicsWorldCollision, ragdollOnDeath;
        public final ForgeConfigSpec.IntValue physicsSampleTicks, physicsRadius;
        public final ForgeConfigSpec.BooleanValue remotePlayers, firstPerson, equipmentLayers, uploadConfigurations;
        public final ForgeConfigSpec.IntValue remotePlayerLimit, remoteDistance;

        private ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Local third-person character display; server movement and hitboxes stay authoritative")
                    .push("character");
            enabled = builder.define("enabled", false);
            profile = builder.define("profile", "rigworks:models/character/block_player/profile.json");
            groundPlacement = builder.define("groundPlacement", true);
            effects = builder.define("effects", false);
            handContacts = builder.comment("Client visual support-hand contacts declared in equipment JSON")
                    .define("handContacts",false);
            animationMode = builder.comment("VANILLA: native poses; SCRIPTED: KubeJS policy; CLIPS: imported animation clips")
                    .defineEnum("animationMode", CharacterAnimationMode.VANILLA);
            physicsEnabled = builder.define("physicsEnabled", true);
            physicsWorldCollision = builder.comment("Observe only already loaded client block shapes")
                    .define("physicsWorldCollision", true);
            ragdollOnDeath = builder.define("ragdollOnDeath", true);
            physicsSampleTicks = builder.defineInRange("physicsSampleTicks", 4, 1, 20);
            physicsRadius = builder.defineInRange("physicsRadius", 2, 1, 4);
            remotePlayers = builder.comment("Render remote profiles announced by a RigWorks server; resources must already exist locally")
                    .define("remotePlayers", true);
            uploadConfigurations=builder.comment("Automatically share changed character JSON/script configuration after import, reload or save; never uploads FBX or textures")
                    .define("uploadConfigurations",true);
            remotePlayerLimit = builder.comment("Maximum resident remote character bindings")
                    .defineInRange("remotePlayerLimit", 32, 1, 128);
            remoteDistance = builder.comment("Remote character loading and display radius in blocks")
                    .defineInRange("remoteDistance", 96, 16, 256);
            firstPerson = builder.comment("Render custom rig arms through vanilla first-person item transforms")
                    .define("firstPerson", true);
            equipmentLayers = builder.comment("Draw vanilla equipment and cosmetic layers attached to the custom pose")
                    .define("equipmentLayers", true);
            builder.pop();
        }
    }
}
