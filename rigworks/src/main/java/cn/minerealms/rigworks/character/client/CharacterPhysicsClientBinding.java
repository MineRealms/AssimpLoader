package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.character.physics.BulletCharacterPhysics;
import cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

import java.util.*;

import static cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition.*;

/** Bounded client observation adapter. Never loads chunks or writes entities, blocks or server collision. */
final class CharacterPhysicsClientBinding {
    private record Box(String id,AABB world) {}
    private final BulletCharacterPhysics physics;
    private List<Box> cached=List.of();
    private BlockPos sampledAt;
    private long nextSample;
    CharacterPhysicsClientBinding(BulletCharacterPhysics physics){this.physics=physics;}
    void update(AbstractClientPlayer player,float modelYaw) {
        var config=cn.minerealms.rigworks.RigWorksConfig.CLIENT;
        physics.setFrame(new Matrix4f().rotateY((float)Math.toRadians(modelYaw-player.yBodyRot)),player.getX(),player.getY(),player.getZ());
        boolean world=AssimpLoaderConfig.getOrDefault(config.physicsWorldCollision::get,true);
        int interval=AssimpLoaderConfig.getIntOrDefault(config.physicsSampleTicks,4);
        if(world && (sampledAt==null || !sampledAt.equals(player.blockPosition()) || player.tickCount>=nextSample)) {
            sampledAt=player.blockPosition();nextSample=player.tickCount+interval;
            int radius=AssimpLoaderConfig.getIntOrDefault(config.physicsRadius,2);
            var found=new ArrayList<Box>();
            var origin=player.blockPosition();var context=CollisionContext.of(player);
            var positions=new ArrayList<BlockPos>();
            BlockPos.betweenClosed(origin.offset(-radius,-2,-radius),origin.offset(radius,3,radius)).forEach(pos->positions.add(pos.immutable()));
            positions.sort(Comparator.comparingDouble(pos->pos.distToCenterSqr(player.position())));
            for(var pos:positions) {
                if(found.size()>=512)break;
                if(!player.level().hasChunkAt(pos))continue;
                var shape=player.level().getBlockState(pos).getCollisionShape(player.level(),pos,context);
                int part=0;
                for(var box:shape.toAabbs()) {
                    if(part>=32 || found.size()>=512)break;
                    if(box.getXsize()>=.002 && box.getYsize()>=.002 && box.getZsize()>=.002)
                        found.add(new Box(pos.asLong()+":"+part,box.move(pos)));
                    part++;
                }
            }
            found.sort(Comparator.comparingDouble(box->box.world.getCenter().distanceToSqr(player.position())));
            cached=List.copyOf(found.subList(0,Math.min(found.size(),physics.definition().maxWorldBoxes())));
        }
        if(!world)sampledAt=null;
        var boxes=new ArrayList<BulletCharacterPhysics.WorldBox>();
        if(world)for(var box:cached) {
            var center=box.world.getCenter().subtract(player.position());
            boxes.add(new BulletCharacterPhysics.WorldBox(box.id,new Vec3((float)center.x,(float)center.y,(float)center.z),
                    new Vec3((float)box.world.getXsize()*.5f,(float)box.world.getYsize()*.5f,(float)box.world.getZsize()*.5f),Rotation.IDENTITY,4,65535));
        }
        physics.setWorldBoxes(boxes);
        Boolean override=player==net.minecraft.client.Minecraft.getInstance().player?LocalPlayerCharacter.ragdollOverride():null;
        physics.setRagdollEnabled(override!=null?override:!player.isAlive() && AssimpLoaderConfig.getOrDefault(config.ragdollOnDeath::get,true));
    }
}
