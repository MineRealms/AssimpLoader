package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.config.ClientVisualPolicy;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.function.Function;

/** A weak owner keeps managed playback collectible; all Level work happens on the client tick. */
public final class MinecraftInstanceWorldBinding {
    private MinecraftInstanceWorldBinding() {}

    public static <T extends Entity> void bind(ModelInstance instance,T entity,Function<T,Matrix4f> rotationScale,
            java.util.function.Predicate<T> ground,java.util.function.Predicate<T> effects) {
        if(instance.hasWorldBinding()) return;
        var owner=new WeakReference<>(entity);
        var sampler=new MinecraftGroundSampler(entity.level());
        instance.setWorldBinding(value->{
            T current=owner.get();
            if(current==null || current.isRemoved() || !current.level().isClientSide) { value.clearWorldContext(); return; }
            Matrix4f rotation=rotationScale.apply(current);
            var policy=ClientVisualPolicy.current();
            value.setGroundPlacementEnabled(policy.mode()==ClientVisualPolicy.Mode.CLIENT_LOCAL?policy.localGroundPlacement():ground.test(current));
            value.setWorldEffectsEnabled(policy.mode()==ClientVisualPolicy.Mode.CLIENT_LOCAL?policy.localEffects():effects.test(current));
            value.setWorldContext(new Matrix4f().translation((float)current.getX(),(float)current.getY(),(float)current.getZ()).mul(rotation),sampler);
            MinecraftAttachmentParticles.bind(value,current.level(),current.position(),rotation);
        });
        Matrix4f rotation=rotationScale.apply(entity);
        var policy=ClientVisualPolicy.current();
        instance.setGroundPlacementEnabled(policy.mode()==ClientVisualPolicy.Mode.CLIENT_LOCAL?policy.localGroundPlacement():ground.test(entity));
        instance.setWorldEffectsEnabled(policy.mode()==ClientVisualPolicy.Mode.CLIENT_LOCAL?policy.localEffects():effects.test(entity));
        instance.setWorldContext(new Matrix4f().translation((float)entity.getX(),(float)entity.getY(),(float)entity.getZ()).mul(rotation),sampler);
    }
}
