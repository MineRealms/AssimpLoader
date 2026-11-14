package cn.minerealms.assimploader.rocket.client;

import cn.minerealms.assimploader.showcase.ShowcaseBlockEntity;
import cn.minerealms.assimploader.rocket.common.RocketEngineState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Asset attachment: nozzle was measured and recentered during export, in Y-up block coordinates. */
public final class RocketShowcaseClient {
    public static final double NOZZLE_RADIUS = 1.03425;
    private static final Vec3 DOWN = new Vec3(0, -1, 0);
    private RocketShowcaseClient() {}

    public static void tick(Level level, BlockPos pos, BlockState state, ShowcaseBlockEntity engine) {
        var profile = ExhaustProfiles.get(engine.getExhaustProfile());
        double nominalFlow = RocketEngineState.nominalMassFlowKgS(profile, NOZZLE_RADIUS);
        double override = engine.getTargetMassFlowKgS();
        double targetFlow = !engine.isRocketBurning() ? 0
                : override < 0 ? nominalFlow : override;
        var engineState = engine.getEngineState();
        engineState.tick(profile, NOZZLE_RADIUS, pos.getY(), targetFlow, 0.05);
        RocketExhausts.updateFlowSource(pos.immutable(), engine.getExhaustProfile(),
                new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), DOWN,
                NOZZLE_RADIUS, engineState);
    }
}
