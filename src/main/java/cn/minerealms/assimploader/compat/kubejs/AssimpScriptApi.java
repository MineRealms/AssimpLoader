package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

/** Client scripts issue bounded commands; the regular Java runtime owns simulation and rendering. */
public final class AssimpScriptApi {
    public int apiVersion() {return 1;}

    public List<String> models() {
        checkThread();
        return new java.util.ArrayList<>(AssimpModelLoader.getAllCachedModels().entrySet().stream()
                .filter(entry->entry.getValue().isDynamic()).map(Map.Entry::getKey).sorted().toList());
    }

    /** Returns null until the renderer/resource loader has imported this dynamic model. */
    public AssimpModelHandle instance(Object owner,String model) {
        checkOwner(owner);
        var scene=AssimpModelLoader.getCachedModel(model,ModelImportMode.DYNAMIC);
        if(scene==null) return null;
        var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(owner,model,scene);
        return state==null?null:new AssimpModelHandle(state);
    }

    public void release(Object owner) {checkOwner(owner);ClientAnimationManager.getInstance().release(owner);}

    public Map<String,Object> statistics() {
        checkThread();
        var stats=ClientAnimationManager.getInstance().getPerformanceStatistics();
        return Map.of("mode",RuntimePerformancePolicy.current().mode().name(),"instances",stats.instances(),
                "evaluated",stats.evaluated(),"skipped",stats.skipped(),"paused",stats.paused());
    }

    private static void checkOwner(Object owner) {
        checkThread();
        var level=Minecraft.getInstance().level;
        if(owner instanceof Entity entity) {
            if(level==null || entity.level()!=level || entity.isRemoved()) throw new IllegalArgumentException("Expected a live client entity");
        } else if(owner instanceof BlockEntity block) {
            if(level==null || block.getLevel()!=level || block.isRemoved()) throw new IllegalArgumentException("Expected a live client block entity");
        } else if(!(owner instanceof ItemStack stack) || stack.isEmpty()) {
            throw new IllegalArgumentException("Assimp owner must be a client entity, block entity or nonempty ItemStack");
        }
    }

    private static void checkThread() {
        var client=Minecraft.getInstance();
        if(client==null || !client.isSameThread()) throw new IllegalStateException("Assimp scripts must run on the client thread");
    }
}
