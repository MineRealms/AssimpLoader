package cn.minerealms.assimploader.animation;

import org.lwjgl.assimp.AIMeshMorphAnim;
import org.lwjgl.assimp.AIMeshMorphKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Copied sparse Assimp weights. Times use the parent clip's ticks. */
public final class MorphAnimationChannel {
    private final String meshName;
    private final List<Key> keys;

    public MorphAnimationChannel(AIMeshMorphAnim source) {
        meshName = source.mName().dataString();
        List<Key> copied = new ArrayList<>();
        for (int i = 0; i < source.mNumKeys(); i++) {
            AIMeshMorphKey key = source.mKeys().get(i);
            if (!Double.isFinite(key.mTime())) throw new IllegalArgumentException("Non-finite morph key time");
            int count = key.mNumValuesAndWeights();
            int[] indices = new int[count];
            float[] weights = new float[count];
            if (count > 0 && (key.mValues() == null || key.mWeights() == null))
                throw new IllegalArgumentException("Missing morph key data");
            for (int j = 0; j < count; j++) {
                indices[j] = key.mValues().get(j);
                weights[j] = (float) key.mWeights().get(j);
                if (indices[j] < 0 || !Float.isFinite(weights[j])) throw new IllegalArgumentException("Invalid morph key data");
            }
            copied.add(new Key(key.mTime(), indices, weights));
        }
        copied.sort(Comparator.comparingDouble(Key::time));
        keys = List.copyOf(copied);
    }

    public String getMeshName() { return meshName; }
    public int getKeyCount() { return keys.size(); }

    public float[] sample(double ticks, int targetCount) {
        if (!Double.isFinite(ticks) || targetCount < 0) throw new IllegalArgumentException("Invalid morph sampling arguments");
        if (keys.isEmpty()) return new float[targetCount];
        if (ticks <= keys.get(0).time()) return expand(keys.get(0), targetCount);
        for (int i = 1; i < keys.size(); i++) {
            Key right = keys.get(i), left = keys.get(i - 1);
            if (ticks >= right.time()) continue;
            float[] a = expand(left, targetCount), b = expand(right, targetCount);
            float alpha = (float) ((ticks - left.time()) / (right.time() - left.time()));
            for (int j = 0; j < a.length; j++) a[j] += (b[j] - a[j]) * alpha;
            return a;
        }
        return expand(keys.get(keys.size() - 1), targetCount);
    }

    private static float[] expand(Key key, int count) {
        float[] result = new float[count];
        for (int i = 0; i < key.indices().length; i++) {
            int index = key.indices()[i];
            if (index >= count) throw new IllegalArgumentException("Morph key target index out of range: " + index);
            result[index] = key.weights()[i];
        }
        return result;
    }

    private record Key(double time, int[] indices, float[] weights) {}
}
