package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.animation.ik.FootPlacementModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;

/** Reads Level collision only on the client thread; native/background solvers never receive Level. */
public final class MinecraftGroundSampler implements FootPlacementModifier.GroundSampler {
    private final Level level;
    public MinecraftGroundSampler(Level level) {
        this.level=java.util.Objects.requireNonNull(level);
        if(!level.isClientSide) throw new IllegalArgumentException("Visual ground sampling requires a client level");
    }
    @Override public Optional<Vector3f> ground(Vector3f origin,float downDistance) {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Ground sampling requires client thread");
        if(!origin.isFinite() || !Float.isFinite(downDistance) || downDistance<=0 || downDistance>FootPlacementModifier.MAX_GROUND_TRACE)
            throw new IllegalArgumentException("Ground trace must be finite and in (0,64] meters");
        Vec3 start = new Vec3(origin.x,origin.y,origin.z);
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(start))) return Optional.empty();
        var hit = level.clip(new ClipContext(start,start.add(0,-downDistance,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
        if (hit.getType()!=HitResult.Type.BLOCK) return Optional.empty();
        Vec3 point=hit.getLocation();
        return Optional.of(new Vector3f((float)point.x,(float)point.y,(float)point.z));
    }
}
