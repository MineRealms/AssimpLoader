package cn.minerealms.assimploader.rocket.client;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.rocket.common.ExhaustPhysics;
import cn.minerealms.assimploader.rocket.common.ExhaustFlowState;
import cn.minerealms.assimploader.rocket.common.RocketEngineState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Render-thread source registry. Update from client ticks, including when an engine is off-screen. */
public final class RocketExhausts {
    public static final int MAX_SOURCES = 16;
    public static final int MAX_PUFFS = 24;
    private static final Map<Object, Source> SOURCES = new HashMap<>();
    private static final ArrayList<Source> ORDERED = new ArrayList<>();
    private static final ExhaustUniforms.Packet PACKET = new ExhaustUniforms.Packet();
    private static long frame;
    private static long packetFrame = Long.MIN_VALUE;
    private static Object world;
    private static long bridgeDraws;
    private static Frustum frustum;
    private static Vec3 previousCamera, currentCamera;
    private static final Map<Object, Snapshot> PREVIOUS = new HashMap<>(), CURRENT = new HashMap<>();
    private static final ArrayList<Object> PREVIOUS_SLOTS = new ArrayList<>(), CURRENT_SLOTS = new ArrayList<>();
    private static final ArrayList<Puff> PUFFS = new ArrayList<>();
    private static long lastNanos, smokeTick = Long.MIN_VALUE;
    private static float frameDelta;
    private static int puffSequence;

    private record Source(Object identity, ResourceLocation profile, Vec3 nozzle, Vec3 axis, double radius,
                          double previousThrottle, double throttle, RocketEngineState state, long tick) {}
    private record Snapshot(Vec3 nozzle, Vec3 axis, double radius, double length, double flow, ResourceLocation profile) {}

    private static final class Puff {
        Vec3 position, previous, velocity;
        final double mass, soot, water, ambient, gravity, metres;
        final Vec3 wind;
        final int seed;
        double radius, age, temperature;
        Puff(Vec3 position, Vec3 velocity, double radius, double mass, double temperature,
             double soot, double water, double ambient, double gravity, double metres, Vec3 wind) {
            this.position = this.previous = position; this.velocity = velocity; this.radius = radius;
            this.mass = mass; this.temperature = temperature; this.soot = soot; this.water = water;
            this.ambient = ambient; this.gravity = gravity; this.metres = metres; this.wind = wind;
            seed = ++puffSequence;
        }
    }

    private RocketExhausts() {}

    public static void updateSource(Object identity, ResourceLocation profile, Vec3 nozzle, Vec3 axis,
                                    double radius, double previousThrottle, double throttle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.isSameThread()) throw new IllegalStateException("Update rocket exhaust on the client thread");
        if (identity == null || profile == null || !finite(nozzle) || !finite(axis) || axis.lengthSqr() < 1e-12
                || !Double.isFinite(radius) || radius <= 0 || radius > 64
                || !Double.isFinite(previousThrottle) || !Double.isFinite(throttle)
                || previousThrottle < 0 || previousThrottle > 1 || throttle < 0 || throttle > 1)
            throw new IllegalArgumentException("invalid exhaust source");
        checkWorld(mc);
        if (previousThrottle == 0 && throttle == 0) SOURCES.remove(identity);
        else SOURCES.put(identity, new Source(identity, profile, nozzle, axis.normalize(), radius, previousThrottle, throttle, null,
                mc.level.getGameTime()));
        invalidateFrame();
    }

    public static void updateFlowSource(Object identity, ResourceLocation profile, Vec3 nozzle, Vec3 axis,
                                        double radius, RocketEngineState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.isSameThread()) throw new IllegalStateException("Update exhaust on the client thread");
        if (identity == null || profile == null || state == null || !finite(nozzle) || !finite(axis)
                || axis.lengthSqr() < 1e-12 || !Double.isFinite(radius) || radius <= 0 || radius > 64)
            throw new IllegalArgumentException("invalid flow source");
        checkWorld(mc);
        SOURCES.put(identity, new Source(identity, profile, nozzle, axis.normalize(), radius,
                state.previousThrottle(), state.throttle(), state, mc.level.getGameTime()));
        invalidateFrame();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        checkWorld(mc);
        if (mc.level != null && !mc.isPaused()) advanceSmoke(mc);
    }

    public static void removeSource(Object identity) { SOURCES.remove(identity); invalidateFrame(); }
    public static void beginFrame() {
        frame++; frustum = null;
        PREVIOUS.clear(); PREVIOUS.putAll(CURRENT); CURRENT.clear();
        PREVIOUS_SLOTS.clear(); PREVIOUS_SLOTS.addAll(CURRENT_SLOTS); CURRENT_SLOTS.clear();
        previousCamera = currentCamera;
        long now = System.nanoTime();
        frameDelta = lastNanos == 0 ? 0 : (float) ((now - lastNanos) * 1e-9);
        lastNanos = now;
    }
    public static void setFrustum(Frustum current) { frustum = current; invalidateFrame(); }
    public static void invalidateFrame() { packetFrame = Long.MIN_VALUE; }
    public static void recordBridgeDraw() { bridgeDraws++; }
    public static long bridgeDraws() { return bridgeDraws; }
    public static boolean hasSource(Object identity) { return SOURCES.containsKey(identity); }

    public static ExhaustUniforms.Packet packet() {
        Minecraft mc = Minecraft.getInstance();
        checkWorld(mc);
        if (packetFrame == frame) return PACKET;
        packetFrame = frame;
        PACKET.frame = frame; PACKET.count = 0; PACKET.puffCount = 0; PACKET.historyValid = 0;
        CURRENT.clear(); CURRENT_SLOTS.clear();
        if (mc.level == null || !AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.rocketExhaustEnabled::get, true))
            return PACKET;
        long tick = mc.level.getGameTime();
        SOURCES.values().removeIf(source -> tick - source.tick() > 2 || tick < source.tick());
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        currentCamera = camera;
        Vec3 delta = previousCamera == null ? Vec3.ZERO : camera.subtract(previousCamera);
        PACKET.cameraDeltaX = (float) delta.x; PACKET.cameraDeltaY = (float) delta.y; PACKET.cameraDeltaZ = (float) delta.z;
        PACKET.deltaTime = mc.isPaused() ? 0 : Math.min(frameDelta, 0.2f);
        PACKET.historyValid = previousCamera != null && delta.lengthSqr() < 16 && frameDelta > 0 && frameDelta < .2 ? 1 : 0;
        float partial = mc.isPaused() ? 1 : mc.getFrameTime();
        PACKET.time = (float) ((tick % 12000L + partial) / 20.0);
        ORDERED.clear();
        ORDERED.addAll(SOURCES.values());
        // Stable nearest-first selection bounds upload and per-pixel work even in a large battle.
        ORDERED.sort(Comparator.comparingDouble((Source source) -> source.nozzle().distanceToSqr(camera))
                .thenComparingDouble(source -> source.nozzle().x).thenComparingDouble(source -> source.nozzle().y)
                .thenComparingDouble(source -> source.nozzle().z));
        Source occlusionSource = null;
        for (Source source : ORDERED) {
            if (PACKET.count == MAX_SOURCES) break;
            double throttle = source.previousThrottle() + (source.throttle() - source.previousThrottle()) * partial;
            var profile = ExhaustProfiles.get(source.profile());
            var visual = profile.appearance();
            if (source.nozzle().distanceToSqr(camera) > Math.pow(192 + visual.lengthBlocks(), 2)) continue;
            var physics = ExhaustPhysics.evaluate(profile, source.radius(), source.nozzle().y, throttle);
            ExhaustFlowState flow = source.state() == null ? null : source.state().interpolate(partial);
            double nominal = RocketEngineState.nominalMassFlowKgS(profile, source.radius());
            double ratio = flow == null ? throttle : flow.massFlowKgS() / Math.max(nominal, .001);
            double ambient = profile.flow().ambientTemperatureK();
            if (ratio < .0001 && (flow == null || flow.nozzleWallTemperatureK() < ambient + 3)) continue;
            double length = visual.lengthBlocks() * Math.pow(Math.max(ratio, .001), .35);
            double mismatch = flow == null ? 0 : Math.abs(Math.log(Math.max(flow.exitPressurePa(), 1) / Math.max(flow.ambientPressurePa(), 1)));
            double expansion = flow == null ? physics.expansion() : visual.expansion() * (1 + Math.min(mismatch, 2) * .35);
            double shock = (flow == null ? physics.shockContrast() : visual.shockContrast() * Math.min(mismatch, 1.5))
                    * smooth(.2, .65, ratio);
            if (flow != null) shock *= smooth(1.02, 1.35, flow.exitMach());
            double outerRadius = 2.5 * (source.radius() + expansion * length);
            Vec3 end = source.nozzle().add(source.axis().scale(length));
            AABB volumeBounds = new AABB(source.nozzle(), end).inflate(outerRadius);
            if (frustum != null && !frustum.isVisible(volumeBounds)) continue;
            Vec3 relative = source.nozzle().subtract(camera);
            int i = PACKET.count++;
            if (occlusionSource == null) occlusionSource = source;
            if (i >= PREVIOUS_SLOTS.size() || !source.identity().equals(PREVIOUS_SLOTS.get(i))) PACKET.historyValid = 0;
            CURRENT_SLOTS.add(source.identity());
            put(PACKET.originRadius, i, relative.x, relative.y, relative.z, source.radius());
            put(PACKET.axisLength, i, source.axis().x, source.axis().y, source.axis().z,
                    length);
            put(PACKET.shape, i, expansion, visual.axialDecay(), visual.density(), visual.turbulence());
            put(PACKET.optics, i, visual.absorption(), visual.scattering(), visual.emission(), shock);
            put(PACKET.colorShock, i, visual.color().r(), visual.color().g(), visual.color().b(),
                    flow == null ? physics.shockSpacingBlocks()
                            : Math.max(source.radius() * .6, 2.6 * source.radius() * Math.sqrt(Math.max(0, flow.exitMach() * flow.exitMach() - 1))));
            put(PACKET.smoke, i, visual.smokeDensity(), visual.heatDistortion(),
                    visual.lightRadiusBlocks(), visual.lightIntensity() * ratio);
            put(PACKET.flow, i, flow == null ? nominal * ratio : flow.massFlowKgS(),
                    flow == null ? physics.exitVelocityMps() : flow.exitVelocityMps(),
                    flow == null ? profile.gas().exitTemperatureK() : flow.exitTemperatureK(),
                    flow == null ? physics.exitDensityKgM3() : flow.exitDensityKgM3());
            put(PACKET.thermal, i, flow == null ? 1000 * ratio + ambient : flow.nozzleWallTemperatureK(), ambient,
                    flow == null ? PACKET.time : flow.ageSeconds(), ratio);
            put(PACKET.transport, i, profile.atmosphere().metresPerBlock(), profile.flow().mixingCoefficient(),
                    flow == null ? profile.flow().sootFraction() : flow.sootFraction(),
                    flow == null ? profile.flow().waterFraction() : flow.waterFraction());
            put(PACKET.gas, i, flow == null ? profile.gas().exitMach() : flow.exitMach(), profile.flow().gasRefractivityM3Kg(),
                    profile.atmosphere().pressureAt(source.nozzle().y) / (287.05 * ambient), profile.flow().specificHeatJPerKgK());
            Snapshot previous = PREVIOUS.get(source.identity());
            if (previous == null || !previous.profile().equals(source.profile())) PACKET.historyValid = 0;
            if (previous == null) previous = new Snapshot(source.nozzle(), source.axis(), source.radius(), length, ratio, source.profile());
            if (Math.abs(previous.flow() - ratio) > .12) PACKET.historyValid = 0;
            Vec3 previousRelative = source.nozzle().subtract(camera).add(delta);
            if (previousCamera != null) previousRelative = previous.nozzle().subtract(previousCamera);
            put(PACKET.previousOriginRadius, i, previousRelative.x, previousRelative.y, previousRelative.z, previous.radius());
            put(PACKET.previousAxisLength, i, previous.axis().x, previous.axis().y, previous.axis().z, previous.length());
            CURRENT.put(source.identity(), new Snapshot(source.nozzle(), source.axis(), source.radius(), length, ratio, source.profile()));
        }
        if (CURRENT.size() != PREVIOUS.size()) PACKET.historyValid = 0;
        if (occlusionSource != null) ExhaustOcclusion.update(occlusionSource.nozzle());
        for (Puff puff : PUFFS) {
            if (PACKET.puffCount == MAX_PUFFS) break;
            Vec3 position = puff.previous.lerp(puff.position, partial);
            if (frustum != null && !frustum.isVisible(new AABB(position, position).inflate(puff.radius * 2.2))) continue;
            Vec3 relative = position.subtract(camera);
            double volume = Math.pow(Math.PI / 1.25, 1.5) * Math.pow(puff.radius * puff.metres, 3);
            int i = PACKET.puffCount++;
            put(PACKET.puffOriginRadius, i, relative.x, relative.y, relative.z, puff.radius);
            put(PACKET.puffOptics, i, Math.min(puff.mass / Math.max(volume, .001), 4), puff.temperature, puff.soot, puff.water);
            put(PACKET.puffMotion, i, puff.age, puff.seed, puff.ambient, puff.metres);
        }
        return PACKET;
    }

    public static void clear() {
        if (!Minecraft.getInstance().isSameThread()) {
            Minecraft.getInstance().execute(RocketExhausts::clear); return;
        }
        SOURCES.clear(); ORDERED.clear(); PUFFS.clear(); CURRENT.clear(); PREVIOUS.clear();
        CURRENT_SLOTS.clear(); PREVIOUS_SLOTS.clear();
        PACKET.count = PACKET.puffCount = 0; world = null; frustum = null;
        previousCamera = currentCamera = null; lastNanos = 0; smokeTick = Long.MIN_VALUE;
        ExhaustOcclusion.clear(); invalidateFrame();
    }

    private static void advanceSmoke(Minecraft mc) {
        long tick = mc.level.getGameTime();
        if (tick == smokeTick) return;
        double dt = smokeTick == Long.MIN_VALUE ? .05 : Math.min(.25, Math.max(.05, (tick - smokeTick) / 20.0));
        smokeTick = tick;
        for (Puff p : PUFFS) {
            p.previous = p.position; p.age += dt;
            double buoyancy = p.gravity * Math.max(0, 1 - p.ambient / Math.max(p.temperature, p.ambient));
            p.velocity = p.wind.add(p.velocity.subtract(p.wind).scale(Math.exp(-.55 * dt))).add(0, buoyancy * dt / p.metres, 0);
            Vec3 next = p.position.add(p.velocity.scale(dt));
            var hit = mc.level.clip(new ClipContext(p.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                Vec3 lateral = new Vec3(Math.sin(p.seed * 2.399), 0, Math.cos(p.seed * 2.399)).scale(4 / p.metres);
                p.position = hit.getLocation().add(n.scale(.25));
                p.velocity = p.velocity.subtract(n.scale(Math.min(0, p.velocity.dot(n)))).scale(.55).add(lateral);
            } else p.position = next;
            p.radius += dt * (.25 + p.velocity.length() * .04);
            p.temperature = p.ambient + (p.temperature - p.ambient) * Math.exp(-.65 * dt);
        }
        PUFFS.removeIf(p -> p.age > 5 || p.radius > 6);
        if (tick % 3 != 0) return;
        for (Source source : SOURCES.values()) {
            if (source.state() == null || tick - source.tick() > 2) continue;
            var profile = ExhaustProfiles.get(source.profile());
            var flow = source.state().interpolate(1);
            double nominal = RocketEngineState.nominalMassFlowKgS(profile, source.radius());
            double ratio = flow.massFlowKgS() / Math.max(nominal, .001);
            if (ratio < .01 || flow.sootFraction() + flow.waterFraction() < .001) continue;
            double metres = profile.atmosphere().metresPerBlock();
            double length = profile.appearance().lengthBlocks() * Math.pow(ratio, .35);
            Vec3 end = source.nozzle().add(source.axis().scale(length * .8));
            var hit = mc.level.clip(new ClipContext(source.nozzle().add(source.axis().scale(.1)), end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.35));
            Vec3 wind = new Vec3(profile.flow().windXMps(), 0, profile.flow().windZMps()).scale(1 / metres);
            Vec3 velocity = source.axis().scale(Math.min(24, flow.exitVelocityMps() * .012) / metres).add(wind);
            if (PUFFS.size() >= MAX_PUFFS) PUFFS.remove(0);
            double mixedRadius = source.radius() + profile.appearance().expansion() * length * .8;
            PUFFS.add(new Puff(end, velocity, mixedRadius,
                    flow.massFlowKgS() * .15,
                    profile.flow().ambientTemperatureK() + (flow.exitTemperatureK() - profile.flow().ambientTemperatureK()) * .12,
                    flow.sootFraction(), flow.waterFraction(), profile.flow().ambientTemperatureK(),
                    profile.flow().gravityMps2(), metres, wind));
        }
    }

    private static double smooth(double a, double b, double value) {
        double x = Math.max(0, Math.min(1, (value - a) / (b - a))); return x * x * (3 - 2 * x);
    }

    private static void checkWorld(Minecraft mc) {
        if (world != mc.level) { clear(); world = mc.level; }
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static void put(float[] values, int index, double x, double y, double z, double w) {
        int offset = index * 4;
        values[offset] = (float) x; values[offset + 1] = (float) y;
        values[offset + 2] = (float) z; values[offset + 3] = (float) w;
    }
}
