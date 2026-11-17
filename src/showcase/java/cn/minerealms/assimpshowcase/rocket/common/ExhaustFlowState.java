package cn.minerealms.assimpshowcase.rocket.common;

/** Immutable SI snapshot consumed by rendering; plume fractions are engine input assumptions. */
public record ExhaustFlowState(double massFlowKgS, double targetMassFlowKgS,
                               double exitVelocityMps, double exitTemperatureK,
                               double exitPressurePa, double exitDensityKgM3,
                               double ambientPressurePa, double ambientTemperatureK,
                               double nozzleWallTemperatureK, double thrustN,
                               double throttle, double ageSeconds, double exhaustEnergyWatts,
                               double sootFraction, double waterFraction, double normalizedMassFlow,
                               double exitMach, double mixingCoefficient, double gasRefractivityM3Kg,
                               double gravityMps2, double windXMps, double windZMps) {
    public ExhaustFlowState {
        finiteNonNegative(massFlowKgS, "mass flow");
        finiteNonNegative(targetMassFlowKgS, "target mass flow");
        bounded(exitVelocityMps, 0, 20_000, "exit velocity");
        bounded(exitTemperatureK, 100, 500_000, "exit temperature");
        bounded(exitPressurePa, 0, 1_000_000_000, "exit pressure");
        bounded(exitDensityKgM3, 0, 1_000_000, "exit density");
        bounded(ambientPressurePa, 1, 10_000_000, "ambient pressure");
        bounded(ambientTemperatureK, 100, 3_000, "ambient temperature");
        bounded(nozzleWallTemperatureK, 100, 6_000, "wall temperature");
        bounded(thrustN, -1_000_000_000_000_000_000.0, 1_000_000_000_000_000_000.0, "thrust");
        bounded(throttle, 0, 1, "throttle");
        finiteNonNegative(ageSeconds, "age");
        finiteNonNegative(exhaustEnergyWatts, "exhaust energy");
        bounded(sootFraction, 0, 1, "soot fraction");
        bounded(waterFraction, 0, 1, "water fraction");
        bounded(normalizedMassFlow, 0, 4, "normalized mass flow");
        bounded(exitMach, 0, 12, "exit Mach");
        bounded(mixingCoefficient, 0, 4, "mixing coefficient");
        bounded(gasRefractivityM3Kg, 0, 0.01, "gas refractivity");
        bounded(gravityMps2, 0, 100, "gravity");
        bounded(windXMps, -1_000, 1_000, "wind x");
        bounded(windZMps, -1_000, 1_000, "wind z");
    }

    public static ExhaustFlowState off(double ambientPressurePa, ExhaustProfile.Flow flow) {
        double ambient = flow.ambientTemperatureK();
        return new ExhaustFlowState(0, 0, 0, ambient, ambientPressurePa, 0,
                ambientPressurePa, ambient, ambient, 0, 0, 0, 0,
                flow.sootFraction(), flow.waterFraction(), 0, 0, flow.mixingCoefficient(),
                flow.gasRefractivityM3Kg(), flow.gravityMps2(), flow.windXMps(), flow.windZMps());
    }

    public static ExhaustFlowState interpolate(ExhaustFlowState a, ExhaustFlowState b, double partialTick) {
        if (!Double.isFinite(partialTick)) throw new IllegalArgumentException("partial tick must be finite");
        double t = Math.max(0, Math.min(1, partialTick));
        return new ExhaustFlowState(lerp(a.massFlowKgS, b.massFlowKgS, t),
                lerp(a.targetMassFlowKgS, b.targetMassFlowKgS, t),
                lerp(a.exitVelocityMps, b.exitVelocityMps, t),
                lerp(a.exitTemperatureK, b.exitTemperatureK, t),
                lerp(a.exitPressurePa, b.exitPressurePa, t),
                lerp(a.exitDensityKgM3, b.exitDensityKgM3, t),
                lerp(a.ambientPressurePa, b.ambientPressurePa, t),
                lerp(a.ambientTemperatureK, b.ambientTemperatureK, t),
                lerp(a.nozzleWallTemperatureK, b.nozzleWallTemperatureK, t),
                lerp(a.thrustN, b.thrustN, t), lerp(a.throttle, b.throttle, t),
                lerp(a.ageSeconds, b.ageSeconds, t), lerp(a.exhaustEnergyWatts, b.exhaustEnergyWatts, t),
                lerp(a.sootFraction, b.sootFraction, t), lerp(a.waterFraction, b.waterFraction, t),
                lerp(a.normalizedMassFlow, b.normalizedMassFlow, t), lerp(a.exitMach, b.exitMach, t),
                lerp(a.mixingCoefficient, b.mixingCoefficient, t),
                lerp(a.gasRefractivityM3Kg, b.gasRefractivityM3Kg, t),
                lerp(a.gravityMps2, b.gravityMps2, t), lerp(a.windXMps, b.windXMps, t),
                lerp(a.windZMps, b.windZMps, t));
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static void finiteNonNegative(double value, String name) {
        bounded(value, 0, Double.MAX_VALUE, name);
    }

    private static void bounded(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(name + " out of range");
    }
}
