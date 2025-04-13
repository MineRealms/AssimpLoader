package io.github.flemmli97.improvedmobs.mekanism_turrets.common.scheduler;
import io.github.flemmli97.improvedmobs.ImprovedMobs;


import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, modid = ImprovedMobs.MODID)
public final class Scheduler {

    static final ConcurrentMap<Integer, List<Runnable>> SERVER_SCHEDULE = new ConcurrentHashMap<>();

    private Scheduler() {

    }

    public static void schedule(Runnable task, int delay) {
        SERVER_SCHEDULE.compute(ServerLifecycleHooks.getCurrentServer().getTickCount() + delay,
                (integer, runnables) -> {
                    if(runnables == null) {
                        runnables = new ObjectArrayList<>();
                    }
                    runnables.add(task);
                    return runnables;
                });
    }
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        int ticks = ServerLifecycleHooks.getCurrentServer().getTickCount();
        List<Runnable> tasks = SERVER_SCHEDULE.get(ticks);
        if(tasks != null) {
            tasks.forEach(Runnable::run);
            SERVER_SCHEDULE.remove(ticks);
        }
    }
}
