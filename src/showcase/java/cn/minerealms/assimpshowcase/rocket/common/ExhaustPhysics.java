package cn.minerealms.assimpshowcase.rocket.common;

/** Reduced-order ideal-gas nozzle estimates; transitional flow is interpolated, not shock-solved. */
public final class ExhaustPhysics {
    private ExhaustPhysics() {}

    public record Design(double exitAreaM2, double throatAreaM2, double areaRatio,
                         double chamberPressurePa, double chamberTemperatureK,
                         double nominalMassFlowKgS, double designExitVelocityMps,
                         double designExitDensityKgM3) {}

    public record Sample(double ambientPressurePa, double exitDensityKgM3, double exitVelocityMps,
                         double massFlowKgS, double thrustN, double expansion, double shockContrast,
                         double shockSpacingBlocks, double densityScale) {}

    public static Design design(ExhaustProfile profile, double radiusBlocks) {
        validateRadius(profile, radiusBlocks);
        ExhaustProfile.Gas gas = profile.gas();
        double mach = gas.exitMach();
        double gamma = gas.gamma();
        double gasConstant = gas.specificGasConstant();
        double area = Math.PI * square(radiusBlocks * profile.atmosphere().metresPerBlock());
        double areaRatio = areaMachRatio(mach, gamma);
        double throatArea = area / areaRatio;
        double temperature0 = gas.exitTemperatureK() * (1 + 0.5 * (gamma - 1) * mach * mach);
        double pressure0 = gas.exitPressurePa() * Math.pow(temperature0 / gas.exitTemperatureK(), gamma / (gamma - 1));
        double chokedFactor = Math.sqrt(gamma / (gasConstant * temperature0))
                * Math.pow(2 / (gamma + 1), (gamma + 1) / (2 * (gamma - 1)));
        double nominalFlow = throatArea * pressure0 * chokedFactor;
        double exitVelocity = mach * Math.sqrt(gamma * gasConstant * gas.exitTemperatureK());
        double exitDensity = gas.exitPressurePa() / (gasConstant * gas.exitTemperatureK());
        if (!Double.isFinite(nominalFlow) || nominalFlow <= 0 || nominalFlow > 1_000_000_000)
            throw new IllegalArgumentException("profile and nozzle produce an unsupported nominal mass flow");
        return new Design(area, throatArea, areaRatio, pressure0, temperature0,
                nominalFlow, exitVelocity, exitDensity);
    }

    public static double nominalMassFlowKgS(ExhaustProfile profile, double radiusBlocks) {
        return design(profile, radiusBlocks).nominalMassFlowKgS();
    }

    public static ExhaustFlowState flowState(ExhaustProfile profile, double radiusBlocks,
                                             double altitudeBlocks, double massFlowKgS,
                                             double targetMassFlowKgS, double wallTemperatureK,
                                             double ageSeconds) {
        validateRadius(profile, radiusBlocks);
        if (!Double.isFinite(altitudeBlocks) || !Double.isFinite(massFlowKgS) || massFlowKgS < 0
                || !Double.isFinite(targetMassFlowKgS) || targetMassFlowKgS < 0
                || !Double.isFinite(wallTemperatureK) || !Double.isFinite(ageSeconds) || ageSeconds < 0)
            throw new IllegalArgumentException("invalid flow state input");

        Design design = design(profile, radiusBlocks);
        ExhaustProfile.Gas gas = profile.gas();
        ExhaustProfile.Flow flow = profile.flow();
        double ambientPressure = profile.atmosphere().pressureAt(altitudeBlocks);
        double ambientTemperature = flow.ambientTemperatureK();
        double normalized = Math.min(flow.maximumFlowRatio(), massFlowKgS / design.nominalMassFlowKgS());
        double throttle = Math.max(0, Math.min(1, normalized));
        double boundedMassFlow = Math.min(massFlowKgS, design.nominalMassFlowKgS() * flow.maximumFlowRatio());

        if (boundedMassFlow <= 1e-10) {
            return new ExhaustFlowState(0, targetMassFlowKgS, 0, ambientTemperature, ambientPressure, 0,
                    ambientPressure, ambientTemperature, wallTemperatureK, 0, 0, ageSeconds, 0,
                    flow.sootFraction(), flow.waterFraction(), 0, 0, flow.mixingCoefficient(),
                    flow.gasRefractivityM3Kg(), flow.gravityMps2(), flow.windXMps(), flow.windZMps());
        }

        double gamma = gas.gamma();
        double gasConstant = gas.specificGasConstant();
        double machDesign = gas.exitMach();
        double loadingForHeat = Math.min(1, normalized);
        double chamberTemperature = ambientTemperature
                + (design.chamberTemperatureK() - ambientTemperature) * Math.sqrt(loadingForHeat);
        double sonicTemperature = chamberTemperature / (1 + 0.5 * (gamma - 1));
        double sonicExitCapacity = ambientPressure * design.exitAreaM2()
                * Math.sqrt(gamma / (gasConstant * sonicTemperature));
        double subsonicMach = solveSubsonicMach(boundedMassFlow, ambientPressure, design.exitAreaM2(),
                chamberTemperature, gamma, gasConstant);
        double blendStart = Math.min(sonicExitCapacity, design.nominalMassFlowKgS() * 0.8);
        double blendEnd = Math.max(blendStart + 1e-9, design.nominalMassFlowKgS());
        double supersonicBlend = smoothstep(blendStart, blendEnd, boundedMassFlow);
        double mach = subsonicMach + (machDesign - subsonicMach) * supersonicBlend;
        double exitTemperature = chamberTemperature / (1 + 0.5 * (gamma - 1) * mach * mach);
        double velocity = mach * Math.sqrt(gamma * gasConstant * exitTemperature);
        double density = boundedMassFlow / (design.exitAreaM2() * velocity);
        double pressure = density * gasConstant * exitTemperature;
        double thrust = boundedMassFlow * velocity + (pressure - ambientPressure) * design.exitAreaM2();
        double exhaustEnergy = boundedMassFlow * (flow.specificHeatJPerKgK()
                * Math.max(0, exitTemperature - ambientTemperature) + 0.5 * velocity * velocity);

        return new ExhaustFlowState(boundedMassFlow, targetMassFlowKgS, velocity, exitTemperature,
                pressure, density, ambientPressure, ambientTemperature, wallTemperatureK, thrust,
                throttle, ageSeconds, exhaustEnergy, flow.sootFraction(), flow.waterFraction(),
                normalized, mach, flow.mixingCoefficient(), flow.gasRefractivityM3Kg(),
                flow.gravityMps2(), flow.windXMps(), flow.windZMps());
    }

    public static Sample evaluate(ExhaustProfile profile, double radiusBlocks, double altitudeBlocks, double throttle) {
        if (!Double.isFinite(throttle) || throttle < 0 || throttle > 1)
            throw new IllegalArgumentException("invalid throttle");
        Design design = design(profile, radiusBlocks);
        ExhaustFlowState state = flowState(profile, radiusBlocks, altitudeBlocks,
                design.nominalMassFlowKgS() * throttle, design.nominalMassFlowKgS() * throttle,
                profile.flow().ambientTemperatureK(), 0);
        double mismatch = state.massFlowKgS() == 0 ? 0
                : Math.log(Math.max(state.exitPressurePa(), 1) / state.ambientPressurePa());
        double shockGate = smoothstep(1.05, 1.5, state.exitMach());
        double shock = profile.appearance().shockContrast()
                * (1 - Math.exp(-Math.abs(mismatch))) * shockGate * state.throttle();
        double expansion = profile.appearance().expansion()
                * clamp(1 + 0.30 * mismatch, 0.35, 2.5);
        double spacing = state.exitMach() <= 1 ? 0 : Math.max(radiusBlocks * 0.8,
                Math.min(64, 2.6 * radiusBlocks * Math.sqrt(state.exitMach() * state.exitMach() - 1)));
        double densityScale = design.designExitDensityKgM3() == 0 ? 0
                : profile.appearance().density() * state.exitDensityKgM3() / design.designExitDensityKgM3();
        return new Sample(state.ambientPressurePa(), state.exitDensityKgM3(), state.exitVelocityMps(),
                state.massFlowKgS(), state.thrustN(), expansion, shock, spacing,
                Math.max(0, Math.min(20, densityScale)));
    }

    public static double areaMachRatio(double mach, double gamma) {
        if (!Double.isFinite(mach) || mach < 1 || !Double.isFinite(gamma) || gamma <= 1)
            throw new IllegalArgumentException("area ratio requires Mach >= 1 and gamma > 1");
        double factor = 2 / (gamma + 1) * (1 + 0.5 * (gamma - 1) * mach * mach);
        return Math.pow(factor, (gamma + 1) / (2 * (gamma - 1))) / mach;
    }

    private static double solveSubsonicMach(double targetFlow, double pressure, double area,
                                            double temperature0, double gamma, double gasConstant) {
        double low = 0;
        double high = 1;
        for (int i = 0; i < 60; i++) {
            double mach = (low + high) * 0.5;
            double temperature = temperature0 / (1 + 0.5 * (gamma - 1) * mach * mach);
            double flow = pressure * area * mach * Math.sqrt(gamma / (gasConstant * temperature));
            if (flow < targetFlow) low = mach;
            else high = mach;
        }
        return (low + high) * 0.5;
    }

    private static void validateRadius(ExhaustProfile profile, double radiusBlocks) {
        if (profile == null || !Double.isFinite(radiusBlocks) || radiusBlocks <= 0 || radiusBlocks > 64)
            throw new IllegalArgumentException("invalid profile or nozzle radius");
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double square(double value) { return value * value; }
}
