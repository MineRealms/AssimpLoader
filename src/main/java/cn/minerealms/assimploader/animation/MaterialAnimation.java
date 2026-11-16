package cn.minerealms.assimploader.animation;

/** Per-instance shader-independent UV and tint parameters. No shared material or vertex is mutated. */
public record MaterialAnimation(float scrollU, float scrollV, float red, float green, float blue, float alpha,
                                float pulseFrequency, float pulseDepth) {
    public MaterialAnimation {
        for (float value : new float[]{scrollU, scrollV, red, green, blue, alpha, pulseFrequency, pulseDepth}) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite material animation");
        }
        if (red < 0 || green < 0 || blue < 0 || alpha < 0 || alpha > 1 || pulseFrequency < 0 || pulseDepth < 0 || pulseDepth > 1)
            throw new IllegalArgumentException("Invalid material animation range");
    }

    public Sample sample(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid material time");
        float pulse = (float)(1 - pulseDepth * (.5 - .5 * Math.cos(seconds * pulseFrequency * Math.PI * 2)));
        return new Sample((float)((seconds * scrollU) % 1), (float)((seconds * scrollV) % 1), red, green, blue, alpha * pulse);
    }

    public record Sample(float u, float v, float red, float green, float blue, float alpha) {}
}
