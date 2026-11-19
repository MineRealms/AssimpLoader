package cn.minerealms.assimploader.material;

import java.util.Objects;

/** Immutable Alpha policy baked once into an albedo variant, leaving packed PBR maps unchanged. */
public record MaterialAlpha(Mode mode, float factor, float cutoff) {
    public enum Mode { SOURCE, OPAQUE, MASK, BLEND }
    public static final MaterialAlpha SOURCE = new MaterialAlpha(Mode.SOURCE, 1, .1f);

    public MaterialAlpha {
        Objects.requireNonNull(mode);
        if (!Float.isFinite(factor) || factor < 0 || factor > 1
                || !Float.isFinite(cutoff) || cutoff < 0 || cutoff > 1)
            throw new IllegalArgumentException("Alpha factor and cutoff must be in [0,1]");
    }

    public boolean baked() { return mode == Mode.OPAQUE || mode == Mode.MASK; }

    /** ABGR input remains immutable and can still serve other materials sharing the source image. */
    public LabPbrPacker.Image apply(LabPbrPacker.Image source) {
        Objects.requireNonNull(source);
        if (!baked()) return source;
        int[] pixels = null;
        for (int y = 0; y < source.height(); y++) for (int x = 0; x < source.width(); x++) {
            int original = source.pixel(x, y);
            float alpha = ((original >>> 24) & 255) / 255f * factor;
            int coverage = mode == Mode.OPAQUE || alpha >= cutoff ? 255 : 0;
            if (coverage != (original >>> 24)) {
                if (pixels == null) pixels = source.pixels();
                pixels[y * source.width() + x] = original & 0x00ffffff | coverage << 24;
            }
        }
        return pixels == null ? source : new LabPbrPacker.Image(source.width(), source.height(), pixels);
    }
}
