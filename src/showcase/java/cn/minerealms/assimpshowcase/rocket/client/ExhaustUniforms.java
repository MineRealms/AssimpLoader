package cn.minerealms.assimpshowcase.rocket.client;

import org.lwjgl.opengl.GL20C;

/** One bounded packet per frame; every exhaust shares the same full-screen volume pass. */
public final class ExhaustUniforms {
    private static final String[] NAMES = {"OriginRadius", "AxisLength", "Shape", "Optics", "ColorShock", "Smoke",
            "Flow", "Thermal", "Transport", "PreviousOriginRadius", "PreviousAxisLength", "Gas"};
    private final int count, time, deltaTime, historyValid, cameraDelta, puffCount;
    private final int[] arrays = new int[NAMES.length];
    private final int[] puffs = new int[3];
    private final ExhaustOcclusion.Binding occlusion;

    public static final class Packet {
        long frame;
        int count, historyValid, puffCount;
        float time, deltaTime, cameraDeltaX, cameraDeltaY, cameraDeltaZ;
        final float[] originRadius = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] axisLength = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] shape = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] optics = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] colorShock = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] smoke = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] flow = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] thermal = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] transport = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] gas = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] previousOriginRadius = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] previousAxisLength = new float[RocketExhausts.MAX_SOURCES * 4];
        final float[] puffOriginRadius = new float[RocketExhausts.MAX_PUFFS * 4];
        final float[] puffOptics = new float[RocketExhausts.MAX_PUFFS * 4];
        final float[] puffMotion = new float[RocketExhausts.MAX_PUFFS * 4];
        public int count() { return count; }
        public int puffCount() { return puffCount; }
    }

    public ExhaustUniforms(int program) {
        count = location(program, "Count"); time = location(program, "Time");
        deltaTime = location(program, "DeltaTime"); historyValid = location(program, "HistoryValid");
        cameraDelta = location(program, "PreviousCameraDelta"); puffCount = location(program, "PuffCount");
        for (int i = 0; i < arrays.length; i++) arrays[i] = location(program, NAMES[i] + "[0]");
        puffs[0] = location(program, "PuffOriginRadius[0]");
        puffs[1] = location(program, "PuffOptics[0]"); puffs[2] = location(program, "PuffMotion[0]");
        occlusion = new ExhaustOcclusion.Binding(program);
    }

    private static int location(int program, String suffix) {
        return GL20C.glGetUniformLocation(program, "assimpExhaust" + suffix);
    }
    public boolean active() { return count >= 0; }
    public void disable() {
        if (active()) GL20C.glUniform1i(count, 0);
        if (puffCount >= 0) GL20C.glUniform1i(puffCount, 0);
        if (historyValid >= 0) GL20C.glUniform1i(historyValid, 0);
        occlusion.upload();
    }

    /** Called only after the intended GL program is bound. Locations belong to its lifetime. */
    public void upload(Packet packet) {
        if (!active()) return;
        GL20C.glUniform1i(count, packet.count);
        if (time >= 0) GL20C.glUniform1f(time, packet.time);
        if (deltaTime >= 0) GL20C.glUniform1f(deltaTime, packet.deltaTime);
        if (historyValid >= 0) GL20C.glUniform1i(historyValid, packet.historyValid);
        if (cameraDelta >= 0) GL20C.glUniform3f(cameraDelta, packet.cameraDeltaX, packet.cameraDeltaY, packet.cameraDeltaZ);
        if (puffCount >= 0) GL20C.glUniform1i(puffCount, packet.puffCount);
        if (packet.count > 0) {
            float[][] values = {packet.originRadius, packet.axisLength, packet.shape, packet.optics, packet.colorShock,
                    packet.smoke, packet.flow, packet.thermal, packet.transport, packet.previousOriginRadius, packet.previousAxisLength, packet.gas};
            for (int i = 0; i < arrays.length; i++) if (arrays[i] >= 0) GL20C.glUniform4fv(arrays[i], values[i]);
        }
        if (packet.puffCount > 0) {
            if (puffs[0] >= 0) GL20C.glUniform4fv(puffs[0], packet.puffOriginRadius);
            if (puffs[1] >= 0) GL20C.glUniform4fv(puffs[1], packet.puffOptics);
            if (puffs[2] >= 0) GL20C.glUniform4fv(puffs[2], packet.puffMotion);
        }
        occlusion.upload();
    }
}
