package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class TerrainRuntimeScheduler {
    private static final List<TerrainTask> ACTIVE_TASKS = new ArrayList<>();
    private static final List<TerrainTask> PENDING_TASKS = new ArrayList<>();
    private static final List<RestorationRecord> RESTORATION_QUEUE = new ArrayList<>();
    private static boolean processingActiveTasks;

    private TerrainRuntimeScheduler() {
    }

    static void scheduleTask(TerrainTask task) {
        if (processingActiveTasks) {
            PENDING_TASKS.add(task);
        } else {
            ACTIVE_TASKS.add(task);
        }
    }

    static boolean hasWork() {
        return !ACTIVE_TASKS.isEmpty() || !RESTORATION_QUEUE.isEmpty();
    }

    static void tick(ServerLevel serverLevel) {
        TickBudget budget = new TickBudget(Math.max(64, Config.destructionGodMaxBrokenBlocksPerTick));

        processingActiveTasks = true;
        try {
            Iterator<TerrainTask> iterator = ACTIVE_TASKS.iterator();
            while (iterator.hasNext()) {
                TerrainTask task = iterator.next();
                if (task.level != serverLevel) {
                    continue;
                }
                if (task.tick(budget)) {
                    iterator.remove();
                }
            }
        } finally {
            processingActiveTasks = false;
        }

        if (!PENDING_TASKS.isEmpty()) {
            ACTIVE_TASKS.addAll(PENDING_TASKS);
            PENDING_TASKS.clear();
        }

        processRestoration(serverLevel, budget);
    }

    private static void processRestoration(ServerLevel level, TickBudget budget) {
        if (RESTORATION_QUEUE.isEmpty()) {
            return;
        }
        RESTORATION_QUEUE.clear();
    }
}

