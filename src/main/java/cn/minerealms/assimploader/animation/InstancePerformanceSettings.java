package cn.minerealms.assimploader.animation;

import com.google.gson.JsonObject;

/** Asset budgets may reduce work; client-wide budgets remain upper limits. */
public record InstancePerformanceSettings(int updateIntervalTicks, boolean ik, boolean springBones,
        boolean particles, boolean trails, int maxParticleEmissions, int maxTrailSamples) {
    public static final InstancePerformanceSettings DEFAULT =
            new InstancePerformanceSettings(1, true, true, true, true, 32, 4096);

    public InstancePerformanceSettings {
        if (updateIntervalTicks < 1 || updateIntervalTicks > 20 || maxParticleEmissions < 0
                || maxParticleEmissions > 32 || maxTrailSamples < 2 || maxTrailSamples > 4096) {
            throw new IllegalArgumentException("Invalid instance performance budget");
        }
    }

    static InstancePerformanceSettings read(JsonObject value) {
        ModelInstanceDefinition.keys(value, "updateIntervalTicks", "ik", "springBones", "particles",
                "trails", "maxParticleEmissions", "maxTrailSamples");
        return new InstancePerformanceSettings(integer(value, "updateIntervalTicks", 1),
                ModelInstanceDefinition.bool(value, "ik", true), ModelInstanceDefinition.bool(value, "springBones", true),
                ModelInstanceDefinition.bool(value, "particles", true), ModelInstanceDefinition.bool(value, "trails", true),
                integer(value, "maxParticleEmissions", 32), integer(value, "maxTrailSamples", 4096));
    }

    private static int integer(JsonObject object, String name, int fallback) {
        float value = ModelInstanceDefinition.number(object, name, fallback);
        if (value != (int) value) throw new IllegalArgumentException(name + " must be an integer");
        return (int) value;
    }
}
