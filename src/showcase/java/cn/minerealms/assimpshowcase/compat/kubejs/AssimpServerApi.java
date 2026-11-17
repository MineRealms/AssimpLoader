package cn.minerealms.assimpshowcase.compat.kubejs;

import cn.minerealms.assimpshowcase.deer.DeerShowcaseEntity;
import net.minecraft.world.entity.Entity;

/** Server-side commands use the showcase entity's existing synchronized and persisted fields. */
public final class AssimpServerApi {
    public int apiVersion() {return 1;}
    public void state(Entity entity,String state) {deer(entity).setShowcaseState(state);}
    public void groundPlacement(Entity entity,boolean enabled) {deer(entity).setGroundIkEnabled(enabled);}
    public void effects(Entity entity,boolean enabled) {deer(entity).setEffectsEnabled(enabled);}
    public String state(Entity entity) {return deer(entity).getShowcaseState();}

    private static DeerShowcaseEntity deer(Entity entity) {
        if(!(entity instanceof DeerShowcaseEntity deer)||entity.isRemoved())
            throw new IllegalArgumentException("AssimpServer currently supports live assimploader:deer_showcase entities");
        var server=entity.getServer();
        if(entity.level().isClientSide()||server==null||!server.isSameThread())
            throw new IllegalStateException("AssimpServer commands must run on the server thread");
        return deer;
    }
}
