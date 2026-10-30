package com.whitecloud233.herobrine_companion.world.terrain;

import com.whitecloud233.herobrine_companion.network.ClearAreaPacket;
import com.whitecloud233.herobrine_companion.network.DesolateAreaPacket;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/** Exercises inherited task callbacks on the real NeoForge bus without editing a world. */
public final class TerrainEditTaskChecks {
    public static void main(String[] args) {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        NeoForge.EVENT_BUS.start();

        CountingQueue desolateQueue = new CountingQueue();
        CountingQueue voidQueue = new CountingQueue();
        CountingQueue subclassQueue = new CountingQueue();
        TerrainEditTask[] tasks = {
                new DesolateAreaPacket.DesolateTask(null, desolateQueue, null),
                new ClearAreaPacket.VoidDomainTask(null, voidQueue, null),
                new TerrainEditTask(null, subclassQueue, null, 1, null) { }
        };
        List<CountingQueue> queues = List.of(desolateQueue, voidQueue, subclassQueue);

        for (TerrainEditTask task : tasks) {
            task.start();
            task.start();
        }
        check("registration does not execute work", queues.stream().allMatch(queue -> queue.probes == 0));
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Pre(() -> true, null));
        check("tasks only listen to post ticks", queues.stream().allMatch(queue -> queue.probes == 0));
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, null));
        check("each subclass runs once despite repeated start", queues.stream().allMatch(queue -> queue.probes == 1));
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, null));
        check("completed callbacks are unregistered", queues.stream().allMatch(queue -> queue.probes == 1));
        for (TerrainEditTask task : tasks) {
            task.start();
        }
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, null));
        check("completed tasks stay stopped", queues.stream().allMatch(queue -> queue.probes == 1));
        System.out.println("TerrainEditTaskChecks: 5 checks passed");
    }

    private static final class CountingQueue extends ArrayDeque<ChunkPos> {
        int probes;

        @Override
        public boolean isEmpty() {
            probes++;
            return super.isEmpty();
        }
    }

    private static void check(String label, boolean success) {
        if (!success) throw new AssertionError(label);
    }
}
