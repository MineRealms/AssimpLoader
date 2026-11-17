package cn.minerealms.rigworks.mixin.character;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingRendererLayersAccess {
    @Accessor("model") EntityModel<LivingEntity> rigworks$getLayerModel();
    @Accessor("model") void rigworks$setLayerModel(EntityModel<LivingEntity> model);
    @Accessor("layers") List<RenderLayer<LivingEntity,EntityModel<LivingEntity>>> rigworks$getLayers();
}
