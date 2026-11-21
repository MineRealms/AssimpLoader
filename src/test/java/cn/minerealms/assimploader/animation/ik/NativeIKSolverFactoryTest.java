package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.ik.solvers.FABRIKSolver;
import cn.minerealms.assimploader.animation.ik.solvers.NativeFABRIKSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeIKSolverFactoryTest {
    @Test
    void fabrikSelectsThePureJavaSolverWhenTheGateIsOff() {
        IKSolver solver = IKManager.createSolver(IKSolver.Type.FABRIK, false);
        assertTrue(solver instanceof FABRIKSolver);
        assertFalse(solver instanceof NativeFABRIKSolver);
        assertEquals(IKSolver.Type.FABRIK, solver.getType());
    }

    @Test
    void enabledFabrikNeverSilentlyFallsBackToJava() {
        try {
            IKSolver solver = IKManager.createSolver(IKSolver.Type.FABRIK, true);
            assertTrue(solver instanceof NativeFABRIKSolver,
                    "an enabled native gate must not fall back to the Java solver");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("nativeIkEnabled"));
        }
    }

    @Test
    void otherSolverTypesAreUnaffectedByTheGate() {
        assertTrue(IKManager.createSolver(IKSolver.Type.TWO_BONE, true) instanceof
                cn.minerealms.assimploader.animation.ik.solvers.TwoBoneIKSolver);
        assertTrue(IKManager.createSolver(IKSolver.Type.CCD, true) instanceof
                cn.minerealms.assimploader.animation.ik.solvers.CCDSolver);
        assertTrue(IKManager.createSolver(IKSolver.Type.LOOK_AT, true) instanceof
                cn.minerealms.assimploader.animation.ik.solvers.LookAtSolver);
    }
}
