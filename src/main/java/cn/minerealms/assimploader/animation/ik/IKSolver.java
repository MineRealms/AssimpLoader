package cn.minerealms.assimploader.animation.ik;

/**
 * IKSolver - Abstract interface for IK solving strategies.
 *
 * Different solvers provide different trade-offs:
 * - TwoBoneIK: Fast analytical solution for 2-bone chains (arms/legs)
 * - FABRIK: Iterative, accurate for multi-bone chains
 * - CCD: Fast iterative approximation
 * - LookAt: Simple rotation constraint
 *
 * Phase 2: Basic Solvers
 */
public interface IKSolver {

    /**
     * Solve IK for a bone chain to reach the target.
     *
     * @param chain The IK chain to solve
     * @return true if solved successfully, false otherwise
     */
    boolean solve(IKChain chain);

    /**
     * Reset the solver state and restore bones to original transforms.
     *
     * @param chain The IK chain to reset
     */
    void reset(IKChain chain);

    /**
     * Get the solver type
     */
    Type getType();

    /**
     * Get solver name for debugging
     */
    default String getName() {
        return getType().name();
    }

    /**
     * IK Solver Types
     */
    enum Type {
        /**
         * Two-Bone IK - Fast analytical solution for 2-bone chains.
         * Best for: Arms, legs
         * Performance: ~0.01ms per chain
         * Accuracy: Exact
         */
        TWO_BONE,

        /**
         * FABRIK (Forward And Backward Reaching IK) - Iterative solver for multi-bone chains.
         * Best for: Spines, tails, tentacles
         * Performance: ~0.05-0.2ms per chain (5-15 iterations)
         * Accuracy: High (converges to solution)
         */
        FABRIK,

        /**
         * CCD (Cyclic Coordinate Descent) - Fast iterative approximation.
         * Best for: Real-time applications, many chains
         * Performance: ~0.03-0.1ms per chain (5-10 iterations)
         * Accuracy: Good (may not converge perfectly)
         */
        CCD,

        /**
         * Look-At - Simple rotation constraint to face target.
         * Best for: Head tracking, eye tracking
         * Performance: ~0.005ms per chain
         * Accuracy: Exact for rotation
         */
        LOOK_AT
    }
}
