package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.config.RuntimePerformancePolicy;

/** Per-instance evaluation cadence. Skipped ticks retain their full playback time. */
final class AnimationCadence {
    private final int phase;
    private long lastRenderedTick;
    private double distanceSquared;
    private double pendingSeconds;
    private boolean rendered;

    AnimationCadence(int phase) { this.phase = phase; }

    void rendered(long tick, double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0) {
            throw new IllegalArgumentException("Render distance must be finite and non-negative");
        }
        // An owner can be submitted to several passes/views in the same tick.
        this.distanceSquared = rendered && lastRenderedTick == tick
                ? Math.min(this.distanceSquared, distanceSquared) : distanceSquared;
        lastRenderedTick = tick;
        rendered = true;
    }

    int interval(long tick, RuntimePerformancePolicy policy, int assetInterval) {
        int interval = policy.nearUpdateTicks();
        if (rendered) {
            if (tick - lastRenderedTick > policy.visibilityGraceTicks()) interval = policy.hiddenUpdateTicks();
            else if (distanceSquared >= policy.farDistance() * policy.farDistance()) interval = policy.farUpdateTicks();
        }
        return Math.max(interval, assetInterval);
    }

    float step(long tick, float seconds, RuntimePerformancePolicy policy, int assetInterval, boolean paused) {
        if (paused) { pendingSeconds = 0; return 0; }
        pendingSeconds += seconds;
        int interval = interval(tick, policy, assetInterval);
        if (interval > 1 && Math.floorMod(tick + phase, interval) != 0) return 0;
        float elapsed = (float) pendingSeconds;
        pendingSeconds = 0;
        return elapsed;
    }

    void resetTime() { pendingSeconds = 0; }
}
