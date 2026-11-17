package cn.minerealms.assimpshowcase.rocket.common;

import com.google.gson.JsonObject;

/** SI gas inputs and calibrated appearance values; this is a reduced-order model, not CFD. */
public record ExhaustProfile(Gas gas, Atmosphere atmosphere, Appearance appearance, Flow flow) {
    public record Gas(double exitPressurePa, double exitTemperatureK, double exitMach,
                      double gamma, double specificGasConstant) {
        public Gas {
            range(exitPressurePa, 1, 100_000_000, "exit_pressure_pa");
            range(exitTemperatureK, 100, 10_000, "exit_temperature_k");
            range(exitMach, 1.01, 12, "exit_mach");
            range(gamma, 1.01, 1.67, "gamma");
            range(specificGasConstant, 50, 5_000, "specific_gas_constant");
        }
    }

    public record Atmosphere(double pressurePa, boolean altitudeDependent, double referenceHeightBlocks,
                             double metresPerBlock, double scaleHeightMetres) {
        public Atmosphere {
            range(pressurePa, 1, 10_000_000, "ambient_pressure_pa");
            range(referenceHeightBlocks, -30_000_000, 30_000_000, "reference_height_blocks");
            range(metresPerBlock, 0.001, 1_000, "metres_per_block");
            range(scaleHeightMetres, 1, 1_000_000, "scale_height_metres");
        }

        public double pressureAt(double heightBlocks) {
            if (!Double.isFinite(heightBlocks)) throw new IllegalArgumentException("non-finite height");
            if (!altitudeDependent) return pressurePa;
            double exponent = -(heightBlocks - referenceHeightBlocks) * metresPerBlock / scaleHeightMetres;
            return Math.max(1, Math.min(10_000_000, pressurePa * Math.exp(Math.max(-30, Math.min(30, exponent)))));
        }
    }

    public record Color(double r, double g, double b) {
        public Color {
            range(r, 0, 8, "color.r"); range(g, 0, 8, "color.g"); range(b, 0, 8, "color.b");
        }
    }

    public record Appearance(double lengthBlocks, double expansion, double axialDecay, double density,
                             double turbulence, double absorption, double scattering, double emission,
                             double shockContrast, Color color, double smokeDensity, double heatDistortion,
                             double lightRadiusBlocks, double lightIntensity, double responseSeconds) {
        public Appearance {
            range(lengthBlocks, 0.1, 128, "length_blocks");
            range(expansion, 0, 1, "expansion");
            range(axialDecay, 0, 10, "axial_decay");
            range(density, 0, 20, "density");
            range(turbulence, 0, 1, "turbulence");
            range(absorption, 0, 20, "absorption");
            range(scattering, 0, 20, "scattering");
            range(emission, 0, 200, "emission");
            range(shockContrast, 0, 2, "shock_contrast");
            if (color == null) throw new IllegalArgumentException("color is required");
            range(smokeDensity, 0, 10, "smoke_density");
            range(heatDistortion, 0, 0.02, "heat_distortion");
            range(lightRadiusBlocks, 0, 64, "light_radius_blocks");
            range(lightIntensity, 0, 100, "light_intensity");
            range(responseSeconds, 0.01, 10, "response_seconds");
        }
    }

    /** Inputs for heat storage, ambient transport, and explicit plume-mixture assumptions. */
    public record Flow(double specificHeatJPerKgK, double ambientTemperatureK,
                       double wallArealHeatCapacityJPerM2K, double wallHeatTransferWPerM2K,
                       double wallEmissivity, double mixingCoefficient, double sootFraction,
                       double waterFraction, double gravityMps2, double windXMps, double windZMps,
                       double gasRefractivityM3Kg, double maximumFlowRatio) {
        public Flow {
            range(specificHeatJPerKgK, 100, 1_000_000, "specific_heat_j_per_kg_k");
            range(ambientTemperatureK, 100, 3_000, "ambient_temperature_k");
            range(wallArealHeatCapacityJPerM2K, 10, 10_000_000, "wall_areal_heat_capacity_j_per_m2_k");
            range(wallHeatTransferWPerM2K, 0, 1_000_000, "wall_heat_transfer_w_per_m2_k");
            range(wallEmissivity, 0, 1, "wall_emissivity");
            range(mixingCoefficient, 0, 4, "mixing_coefficient");
            range(sootFraction, 0, 1, "soot_fraction");
            range(waterFraction, 0, 1, "water_fraction");
            range(gravityMps2, 0, 100, "gravity_mps2");
            range(windXMps, -1_000, 1_000, "wind_x_mps");
            range(windZMps, -1_000, 1_000, "wind_z_mps");
            range(gasRefractivityM3Kg, 0, 0.01, "gas_refractivity_m3_per_kg");
            range(maximumFlowRatio, 1, 4, "maximum_flow_ratio");
        }

        private static Flow defaults(Gas gas) {
            return new Flow(gas.gamma() * gas.specificGasConstant() / (gas.gamma() - 1),
                    288.15, 125_000, 4_000, 0.72, 0.35, 0.002, 0.18,
                    9.81, 0, 0, 0.00023, 1.5);
        }
    }

    public ExhaustProfile(Gas gas, Atmosphere atmosphere, Appearance appearance) {
        this(gas, atmosphere, appearance, Flow.defaults(gas));
    }

    public ExhaustProfile {
        if (gas == null || atmosphere == null || appearance == null || flow == null)
            throw new IllegalArgumentException("gas, atmosphere, appearance and flow are required");
        double idealCp = gas.gamma() * gas.specificGasConstant() / (gas.gamma() - 1);
        if (Math.abs(flow.specificHeatJPerKgK() - idealCp) / idealCp > 0.2)
            throw new IllegalArgumentException("specific_heat_j_per_kg_k must be consistent with gamma and R");
        if (flow.sootFraction() + flow.waterFraction() > 1)
            throw new IllegalArgumentException("soot and water mass fractions cannot sum above one");
    }

    public static ExhaustProfile fromJson(JsonObject json) {
        JsonObject g = object(json, "gas"), a = object(json, "atmosphere"), v = object(json, "appearance");
        JsonObject c = object(v, "color"), f = object(json, "flow");
        Gas gas = new Gas(number(g, "exit_pressure_pa", 180_000),
                number(g, "exit_temperature_k", 2400), number(g, "exit_mach", 3.0),
                number(g, "gamma", 1.22), number(g, "specific_gas_constant", 355));
        Flow defaults = Flow.defaults(gas);
        return new ExhaustProfile(gas,
                new Atmosphere(number(a, "ambient_pressure_pa", 101_325),
                        a.has("altitude_dependent") && a.get("altitude_dependent").getAsBoolean(),
                        number(a, "reference_height_blocks", 64), number(a, "metres_per_block", 1),
                        number(a, "scale_height_metres", 8500)),
                new Appearance(number(v, "length_blocks", 7), number(v, "expansion", 0.10),
                        number(v, "axial_decay", 0.22), number(v, "density", 1),
                        number(v, "turbulence", 0.65), number(v, "absorption", 0.18),
                        number(v, "scattering", 0.25), number(v, "emission", 18),
                        number(v, "shock_contrast", 0.7),
                        new Color(number(c, "r", 0.38), number(c, "g", 0.65), number(c, "b", 1)),
                        number(v, "smoke_density", 0.25), number(v, "heat_distortion", 0.001),
                        number(v, "light_radius_blocks", 9), number(v, "light_intensity", 3),
                        number(v, "response_seconds", 0.35)),
                new Flow(number(f, "specific_heat_j_per_kg_k", defaults.specificHeatJPerKgK()),
                        number(f, "ambient_temperature_k", defaults.ambientTemperatureK()),
                        number(f, "wall_areal_heat_capacity_j_per_m2_k", defaults.wallArealHeatCapacityJPerM2K()),
                        number(f, "wall_heat_transfer_w_per_m2_k", defaults.wallHeatTransferWPerM2K()),
                        number(f, "wall_emissivity", defaults.wallEmissivity()),
                        number(f, "mixing_coefficient", defaults.mixingCoefficient()),
                        number(f, "soot_fraction", defaults.sootFraction()),
                        number(f, "water_fraction", defaults.waterFraction()),
                        number(f, "gravity_mps2", defaults.gravityMps2()),
                        number(f, "wind_x_mps", defaults.windXMps()),
                        number(f, "wind_z_mps", defaults.windZMps()),
                        number(f, "gas_refractivity_m3_per_kg", defaults.gasRefractivityM3Kg()),
                        number(f, "maximum_flow_ratio", defaults.maximumFlowRatio())));
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static void range(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(name + " must be finite and in [" + min + ", " + max + "]");
    }
}
