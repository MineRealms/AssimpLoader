package cn.minerealms.assimpshowcase.rocket.common;

/** Time-based static-stand flow and nozzle-wall thermal state. */
public final class RocketEngineState {
    private static final double STEFAN_BOLTZMANN = 5.670374419e-8;
    private double previousThrottle;
    private double throttle;
    private double previousLegacyThrottle;
    private double legacyThrottle;
    private double massFlowKgS;
    private double ageSeconds;
    private double wallTemperatureK = 288.15;
    private boolean initialized;
    private ExhaustFlowState previous = initialState();
    private ExhaustFlowState current = initialState();

    /** Advances the requested stand flow and its independent nozzle-wall temperature. */
    public void tick(ExhaustProfile profile, double nozzleRadiusBlocks, double altitudeBlocks,
                     double requestedMassFlowKgS, double dtSeconds) {
        if (profile == null || !Double.isFinite(altitudeBlocks)
                || !Double.isFinite(requestedMassFlowKgS) || requestedMassFlowKgS < 0
                || !Double.isFinite(dtSeconds) || dtSeconds < 0 || dtSeconds > 1)
            throw new IllegalArgumentException("invalid engine state input");

        ExhaustPhysics.Design design = ExhaustPhysics.design(profile, nozzleRadiusBlocks);
        double maxFlow = design.nominalMassFlowKgS() * profile.flow().maximumFlowRatio();
        double target = Math.min(requestedMassFlowKgS, maxFlow);
        double tau = profile.appearance().responseSeconds();
        double response = -Math.expm1(-dtSeconds / tau);
        previous = current;
        previousThrottle = throttle;
        double flowAtTickStart = massFlowKgS;
        massFlowKgS += (target - massFlowKgS) * response;
        if (massFlowKgS < maxFlow * 1e-12) massFlowKgS = 0;

        if (!initialized) {
            wallTemperatureK = profile.flow().ambientTemperatureK();
            initialized = true;
        }
        wallTemperatureK = advanceWallTemperature(profile, flowAtTickStart, target,
                design.nominalMassFlowKgS(), wallTemperatureK, dtSeconds, tau);
        double normalized = massFlowKgS / design.nominalMassFlowKgS();
        if (normalized > 1e-5) ageSeconds += dtSeconds;
        else ageSeconds = 0;

        current = ExhaustPhysics.flowState(profile, nozzleRadiusBlocks, altitudeBlocks,
                massFlowKgS, target, wallTemperatureK, ageSeconds);
        throttle = current.throttle();
    }

    public ExhaustFlowState interpolate(double partialTick) {
        return ExhaustFlowState.interpolate(previous, current, partialTick);
    }

    public static double nominalMassFlowKgS(ExhaustProfile profile, double nozzleRadiusBlocks) {
        return ExhaustPhysics.nominalMassFlowKgS(profile, nozzleRadiusBlocks);
    }

    /** Compatibility entry point for integrations that still expose a binary burn toggle. */
    public void tick(boolean burning, double dtSeconds, double responseSeconds) {
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0 || dtSeconds > 1
                || !Double.isFinite(responseSeconds) || responseSeconds <= 0)
            throw new IllegalArgumentException("invalid engine time step");
        previousLegacyThrottle = legacyThrottle;
        legacyThrottle += ((burning ? 1 : 0) - legacyThrottle) * -Math.expm1(-dtSeconds / responseSeconds);
        if (legacyThrottle < 0.0001) legacyThrottle = 0;
        if (legacyThrottle > 0.9999) legacyThrottle = 1;
        previousThrottle = previousLegacyThrottle;
        throttle = legacyThrottle;
    }

    public double previousThrottle() { return previousThrottle; }
    public double throttle() { return throttle; }

    private static double advanceWallTemperature(ExhaustProfile profile, double massFlow,
                                                double targetFlow, double nominalFlow,
                                                double wallTemperature, double dtSeconds,
                                                double flowResponseSeconds) {
        if (dtSeconds == 0) return wallTemperature;
        ExhaustProfile.Flow flow = profile.flow();
        ExhaustProfile.Gas gas = profile.gas();
        double ambient = flow.ambientTemperatureK();
        double designChamberTemperature = gas.exitTemperatureK()
                * (1 + 0.5 * (gas.gamma() - 1) * gas.exitMach() * gas.exitMach());
        int steps = Math.max(1, (int) Math.ceil(dtSeconds / 0.01));
        double stepSeconds = dtSeconds / steps;
        for (int i = 0; i < steps; i++) {
            double midpointSeconds = (i + 0.5) * stepSeconds;
            double midpointFlow = massFlow + (targetFlow - massFlow)
                    * -Math.expm1(-midpointSeconds / flowResponseSeconds);
            double load = Math.max(0, Math.min(flow.maximumFlowRatio(), midpointFlow / nominalFlow));
            double gasTemperature = ambient + (designChamberTemperature - ambient)
                    * Math.sqrt(Math.min(1, load));
            wallTemperature = advanceWallStep(flow, gasTemperature, load, wallTemperature, stepSeconds);
        }
        return wallTemperature;
    }

    private static double advanceWallStep(ExhaustProfile.Flow flow, double gasTemperature,
                                          double load, double wallTemperature, double dtSeconds) {
        double ambient = flow.ambientTemperatureK();
        double flowTransfer = flow.wallHeatTransferWPerM2K() * Math.pow(load, 0.8);
        double radiativeTransfer = flow.wallEmissivity() * STEFAN_BOLTZMANN
                * (wallTemperature + ambient) * (wallTemperature * wallTemperature + ambient * ambient);
        double totalTransfer = flowTransfer + radiativeTransfer;
        if (totalTransfer <= 1e-12) return wallTemperature;
        double equilibrium = (flowTransfer * gasTemperature + radiativeTransfer * ambient) / totalTransfer;
        double timeConstant = flow.wallArealHeatCapacityJPerM2K() / totalTransfer;
        double updated = equilibrium + (wallTemperature - equilibrium) * Math.exp(-dtSeconds / timeConstant);
        return Math.max(ambient, Math.min(6_000, updated));
    }

    private static ExhaustFlowState initialState() {
        double ambient = 288.15;
        return new ExhaustFlowState(0, 0, 0, ambient, 101_325, 0, 101_325, ambient,
                ambient, 0, 0, 0, 0, 0.002, 0.18, 0, 0, 0.35,
                0.00023, 9.81, 0, 0);
    }
}
