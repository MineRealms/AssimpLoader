package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.animation.ModelInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Copies an anchor rather than retaining an entity through a weak-owner animation entry. */
public final class MinecraftAttachmentParticles {
    private MinecraftAttachmentParticles() {}
    public static void bind(ModelInstance instance,Level level,Vec3 worldOrigin,Matrix4f modelRotationScale) {
        Matrix4f transform=new Matrix4f(modelRotationScale);
        long anchorTime=level==null?0:level.getGameTime();
        instance.setParticleSink((name,position,velocity) -> {
            if(!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Particles require client tick thread");
            if(level==null || !level.isClientSide || !level.hasChunkAt(BlockPos.containing(worldOrigin))) return;
            if(level.getGameTime()-anchorTime>1) return;
            ResourceLocation id=ResourceLocation.tryParse(name);
            if(id==null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(id)) return;
            var type=BuiltInRegistries.PARTICLE_TYPE.get(id);
            if(!(type instanceof SimpleParticleType simple)) return;
            transform.transformPosition(position); transform.transformDirection(velocity);
            level.addParticle(simple,worldOrigin.x+position.x,worldOrigin.y+position.y,worldOrigin.z+position.z,velocity.x,velocity.y,velocity.z);
        });
    }
}
