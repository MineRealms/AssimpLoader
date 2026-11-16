package cn.minerealms.assimploader.animation;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded render-only history; the simulation remains the sole pose producer. */
final class RenderPoseInterpolator {
    private static final AtomicLong NEXT_FRAME = new AtomicLong(Long.MIN_VALUE);
    private PoseSnapshot previous;
    private PoseSnapshot current;
    private PoseSnapshot cached;
    private double durationTicks = 1;
    private int elapsedTicks;
    private float cachedAlpha = Float.NaN;

    void publish(PoseSnapshot snapshot, double deltaSeconds) {
        if (deltaSeconds <= 0 || current == null || current.getScene() != snapshot.getScene()) {
            reset(snapshot);
            return;
        }
        previous = current;
        current = snapshot;
        durationTicks = Math.max(1, deltaSeconds * 20);
        elapsedTicks = 0;
        cached = null;
        cachedAlpha = Float.NaN;
    }

    void advanceTick() {
        if (previous != null && elapsedTicks < durationTicks) elapsedTicks++;
    }

    void reset(PoseSnapshot snapshot) {
        previous = null;
        current = snapshot;
        cached = null;
        cachedAlpha = Float.NaN;
        elapsedTicks = 0;
    }

    PoseSnapshot sample(float partialTick) {
        if (!Float.isFinite(partialTick)) throw new IllegalArgumentException("Partial tick must be finite");
        if (previous == null) return current;
        float alpha = (float) Math.min(1, (elapsedTicks + Math.max(0, Math.min(1, partialTick))) / durationTicks);
        if (alpha == 0) return previous;
        if (alpha == 1) return current;
        if (cached == null || cachedAlpha != alpha) {
            cached = PoseSnapshot.interpolate(NEXT_FRAME.getAndIncrement(), previous, current, alpha);
            cachedAlpha = alpha;
        }
        return cached;
    }
}
