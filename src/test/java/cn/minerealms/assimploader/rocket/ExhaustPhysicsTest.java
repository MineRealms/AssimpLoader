package cn.minerealms.assimploader.rocket;

import cn.minerealms.assimpshowcase.rocket.common.ExhaustFlowState;
import cn.minerealms.assimpshowcase.rocket.common.ExhaustPhysics;
import cn.minerealms.assimpshowcase.rocket.common.ExhaustProfile;
import cn.minerealms.assimpshowcase.rocket.common.RocketEngineState;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExhaustPhysicsTest {
    private static ExhaustProfile profile() { return ExhaustProfile.fromJson(new JsonObject()); }

    @Test void designNozzleMatchesConfiguredExitAndMassContinuity() {
        var p = profile();
        var design = ExhaustPhysics.design(p, 1.03425);
        double areaRatio = ExhaustPhysics.areaMachRatio(p.gas().exitMach(), p.gas().gamma());
        assertEquals(areaRatio, design.areaRatio(), 1e-12);
        assertEquals(design.exitAreaM2() / areaRatio, design.throatAreaM2(), 1e-12);
        assertEquals(design.nominalMassFlowKgS(),
                design.designExitDensityKgM3() * design.designExitVelocityMps() * design.exitAreaM2(), 1e-8);
        var full = ExhaustPhysics.flowState(p, 1.03425, 64, design.nominalMassFlowKgS(),
                design.nominalMassFlowKgS(), p.flow().ambientTemperatureK(), 1);
        assertEquals(p.gas().exitPressurePa(), full.exitPressurePa(), 1e-6);
        assertEquals(p.gas().exitTemperatureK(), full.exitTemperatureK(), 1e-8);
        assertEquals(full.exitPressurePa(), full.exitDensityKgM3()
                * p.gas().specificGasConstant() * full.exitTemperatureK(), 1e-8);
    }

    @Test void flowContinuityAndIdealGasStateHoldAcrossStartupAndOverdrive() {
        var p = profile();
        double radius = 1.03425;
        var design = ExhaustPhysics.design(p, radius);
        for (double load : new double[]{0.001, 0.08, 0.2, 0.55, 1, 1.5}) {
            var state = ExhaustPhysics.flowState(p, radius, 64,
                    design.nominalMassFlowKgS() * load, design.nominalMassFlowKgS() * load,
                    500, 2);
            assertEquals(state.massFlowKgS(),
                    state.exitDensityKgM3() * state.exitVelocityMps() * design.exitAreaM2(),
                    Math.max(1e-8, state.massFlowKgS() * 1e-10));
            assertEquals(state.exitPressurePa(), state.exitDensityKgM3()
                    * p.gas().specificGasConstant() * state.exitTemperatureK(), 1e-7);
            assertTrue(Double.isFinite(state.thrustN()));
            assertTrue(Double.isFinite(state.exhaustEnergyWatts()));
        }
    }

    @Test void zeroFlowHasNoJetEnergyOrThrustAndMatchedPressureHasNoDiamonds() {
        var p = profile();
        var zero = ExhaustPhysics.evaluate(p, 1, 64, 0);
        assertEquals(0, zero.massFlowKgS());
        assertEquals(0, zero.thrustN());
        assertEquals(0, zero.shockContrast());

        var a = p.atmosphere();
        var matched = new ExhaustProfile(p.gas(), new ExhaustProfile.Atmosphere(p.gas().exitPressurePa(), false,
                a.referenceHeightBlocks(), a.metresPerBlock(), a.scaleHeightMetres()), p.appearance());
        assertEquals(0, ExhaustPhysics.evaluate(matched, 1, 64, 1).shockContrast(), 1e-10);
        assertEquals(a.pressureAt(64), a.pressureAt(320));
        var atmosphere = new ExhaustProfile.Atmosphere(101325, true, 64, 1, 8500);
        assertEquals(101325 / Math.E, atmosphere.pressureAt(8564), 1e-8);
    }

    @Test void startAndStopResponseIsTimeBasedAndWallHasThermalInertia() {
        var p = profile();
        double radius = 1.03425;
        double nominal = RocketEngineState.nominalMassFlowKgS(p, radius);
        var coarse = new RocketEngineState();
        var fine = new RocketEngineState();
        for (int i = 0; i < 40; i++) coarse.tick(p, radius, 64, nominal, 0.05);
        for (int i = 0; i < 200; i++) fine.tick(p, radius, 64, nominal, 0.01);

        ExhaustFlowState hotCoarse = coarse.interpolate(1);
        ExhaustFlowState hotFine = fine.interpolate(1);
        assertEquals(hotCoarse.massFlowKgS(), hotFine.massFlowKgS(), 1e-8);
        assertEquals(hotCoarse.nozzleWallTemperatureK(), hotFine.nozzleWallTemperatureK(), 1.0);
        assertTrue(hotCoarse.nozzleWallTemperatureK() > p.flow().ambientTemperatureK() + 10);
        assertTrue(hotCoarse.exhaustEnergyWatts() > 0);

        coarse.tick(p, radius, 64, 0, 0.05);
        var stopping = coarse.interpolate(1);
        assertTrue(stopping.massFlowKgS() < hotCoarse.massFlowKgS());
        assertTrue(stopping.massFlowKgS() > 0);
        assertTrue(stopping.nozzleWallTemperatureK() > p.flow().ambientTemperatureK() + 10);
        // Residual chamber gas may still heat the wall while flow spools down.
        for (int i = 0; i < 100; i++) coarse.tick(p, radius, 64, 0, 0.05);
        var residualHeat = coarse.interpolate(1);
        for (int i = 0; i < 1_000; i++) coarse.tick(p, radius, 64, 0, 0.05);
        var cold = coarse.interpolate(1);
        assertEquals(0, cold.massFlowKgS());
        assertTrue(cold.nozzleWallTemperatureK() < residualHeat.nozzleWallTemperatureK());
        assertTrue(cold.nozzleWallTemperatureK() > p.flow().ambientTemperatureK());
    }

    @Test void interpolationAndLegacyThrottleApiRemainAvailable() {
        var p = profile();
        double radius = 1.03425;
        double nominal = RocketEngineState.nominalMassFlowKgS(p, radius);
        var state = new RocketEngineState();
        state.tick(p, radius, 64, nominal, 0.05);
        var halfway = state.interpolate(0.5);
        assertEquals((state.interpolate(0).massFlowKgS() + state.interpolate(1).massFlowKgS()) * 0.5,
                halfway.massFlowKgS(), 1e-8);
        assertThrows(IllegalArgumentException.class, () -> state.interpolate(Double.NaN));

        var legacyA = new RocketEngineState();
        var legacyB = new RocketEngineState();
        for (int i = 0; i < 20; i++) legacyA.tick(true, 0.05, 0.35);
        for (int i = 0; i < 100; i++) legacyB.tick(true, 0.01, 0.35);
        assertEquals(legacyA.throttle(), legacyB.throttle(), 1e-10);
        double before = legacyA.throttle();
        legacyA.tick(false, 0.05, 0.35);
        assertTrue(legacyA.throttle() < before && legacyA.throttle() > 0);
    }

    @Test void profileDefaultsAndInvalidInputsAreBounded() {
        var p = profile();
        assertTrue(p.flow().specificHeatJPerKgK() > p.gas().specificGasConstant());
        assertEquals(0.002, p.flow().sootFraction());
        assertThrows(IllegalArgumentException.class,
                () -> new ExhaustProfile.Gas(Double.NaN, 2400, 3, 1.22, 355));
        assertThrows(IllegalArgumentException.class, () -> ExhaustPhysics.evaluate(p, -1, 64, 1));
        assertThrows(IllegalArgumentException.class, () -> ExhaustPhysics.evaluate(p, 1, 64, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ExhaustProfile.fromJson(jsonWithFlow("soot_fraction", 0.9, "water_fraction", 0.2)));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketEngineState().tick(p, 1, 64, -1, 0.05));
        assertThrows(IllegalArgumentException.class,
                () -> new RocketEngineState().tick(p, 1, 64, 1, Double.POSITIVE_INFINITY));
    }

    private static JsonObject jsonWithFlow(String first, double firstValue, String second, double secondValue) {
        JsonObject root = new JsonObject();
        JsonObject flow = new JsonObject();
        flow.addProperty(first, firstValue);
        flow.addProperty(second, secondValue);
        root.add("flow", flow);
        return root;
    }
}
