package cn.minerealms.rigworks;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Client visual policy owned by RigWorks; no server configuration or simulation. */
public final class RigWorksConfig {
    public enum CharacterAnimationMode { VANILLA, SCRIPTED, CLIPS }
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;
    static {
        var builder = new ForgeConfigSpec.Builder();
        CLIENT = new ClientConfig(builder);
        CLIENT_SPEC = builder.build();
    }
    private RigWorksConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "rigworks-client.toml");
    }

    public static final class ClientConfig {
        public final ForgeConfigSpec.BooleanValue enabled, groundPlacement, effects, handContacts;
        public final ForgeConfigSpec.ConfigValue<String> profile;
        public final ForgeConfigSpec.EnumValue<CharacterAnimationMode> animationMode;
        public final ForgeConfigSpec.BooleanValue physicsEnabled, physicsWorldCollision, ragdollOnDeath;
        public final ForgeConfigSpec.IntValue physicsSampleTicks, physicsRadius;

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
            builder.pop();
        }
    }
}
