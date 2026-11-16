package cn.minerealms.assimploader.animation;

/** Native-independent replacement streams, stored as deltas from the bind mesh. */
public final class MorphTarget {
    private final String name;
    private final float defaultWeight;
    private final float[] positions;
    private final float[] normals;

    public MorphTarget(String name, float defaultWeight, float[] positionDeltas, float[] normalDeltas) {
        if (!Float.isFinite(defaultWeight)) throw new IllegalArgumentException("Non-finite morph default weight");
        this.name = name;
        this.defaultWeight = defaultWeight;
        this.positions = positionDeltas == null ? null : positionDeltas.clone();
        this.normals = normalDeltas == null ? null : normalDeltas.clone();
        if ((positions != null && positions.length % 3 != 0) || (normals != null && normals.length % 3 != 0))
            throw new IllegalArgumentException("Invalid morph vector stream length");
        if (positions != null && normals != null && positions.length != normals.length)
            throw new IllegalArgumentException("Morph stream count mismatch");
        if (positions != null) for (float value : positions)
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite morph position");
        if (normals != null) for (float value : normals)
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite morph normal");
    }

    public String getName() { return name; }
    public float getDefaultWeight() { return defaultWeight; }
    public float[] copyPositionDeltas() { return positions == null ? null : positions.clone(); }
    public float[] copyNormalDeltas() { return normals == null ? null : normals.clone(); }

    void accumulate(float weight, float[] destinationPositions, float[] destinationNormals) {
        if ((positions != null && positions.length != destinationPositions.length)
                || (normals != null && destinationNormals != null && normals.length != destinationNormals.length))
            throw new IllegalArgumentException("Morph geometry count mismatch");
        if (positions != null) for (int i = 0; i < positions.length; i++) destinationPositions[i] += positions[i] * weight;
        if (normals != null && destinationNormals != null)
            for (int i = 0; i < normals.length; i++) destinationNormals[i] += normals[i] * weight;
    }
}
