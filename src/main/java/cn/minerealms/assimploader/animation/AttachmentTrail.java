package cn.minerealms.assimploader.animation;

import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded ribbon history, sampled once per simulation time; render passes only read it. */
public final class AttachmentTrail {
    public record Sample(double time, Vector3f start, Vector3f end) {
        public Sample { start = new Vector3f(start); end = new Vector3f(end); }
        @Override public Vector3f start() { return new Vector3f(start); }
        @Override public Vector3f end() { return new Vector3f(end); }
    }
    private final String startAttachment, endAttachment;
    private final int capacity;
    private final double lifetime;
    private final float[] color;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private double lastTime = -1;

    public AttachmentTrail(String start, String end, int capacity, double lifetime, float[] color) {
        if (start == null || end == null || capacity < 2 || capacity > 4096 || !Double.isFinite(lifetime) || lifetime <= 0 || color.length != 4)
            throw new IllegalArgumentException("Invalid trail settings");
        for (float value : color) if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid trail color");
        this.startAttachment = start; this.endAttachment = end;
        this.capacity = capacity; this.lifetime = lifetime; this.color = color.clone();
    }
    public String startAttachment() { return startAttachment; }
    public String endAttachment() { return endAttachment; }
    public float[] color() { return color.clone(); }
    public double lifetime() { return lifetime; }
    public List<Sample> samples() { return List.copyOf(samples); }

    public void sample(double time, Vector3f start, Vector3f end) {
        if (!Double.isFinite(time) || time < 0 || !start.isFinite() || !end.isFinite()) throw new IllegalArgumentException("Invalid trail sample");
        if (time < lastTime) reset();
        if (time == lastTime) return;
        lastTime = time;
        while (!samples.isEmpty() && (samples.size() >= capacity || time - samples.peekFirst().time() > lifetime)) samples.removeFirst();
        samples.addLast(new Sample(time, start, end));
    }
    public void reset() { samples.clear(); lastTime = -1; }
}
