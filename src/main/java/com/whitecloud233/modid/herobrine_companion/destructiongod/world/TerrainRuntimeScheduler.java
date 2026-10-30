package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class TerrainRuntimeScheduler {
    private static final List<TerrainTask> ACTIVE_TASKS = new ArrayList<>();
    private static final List<TerrainTask> PENDING_TASKS = new ArrayList<>();
    private static boolean processingActiveTasks;
    private static final Map<ServerLevel, DimensionTurn> DIMENSIONS = new WeakHashMap<>();

    private static final class DimensionTurn {
        long gameTime = Long.MIN_VALUE;
        int next;
    }

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
        return !ACTIVE_TASKS.isEmpty();
    }

    static void tick(ServerLevel serverLevel) {
        DimensionTurn turn = DIMENSIONS.computeIfAbsent(serverLevel, ignored -> new DimensionTurn());
        long now = serverLevel.getGameTime();
        // A repeated event for the same dimension and tick must not mint another budget.
        if (turn.gameTime == now) return;
        turn.gameTime = now;
        TickBudget budget = new TickBudget(DestructionTuning.MAX_BROKEN_BLOCKS_PER_TICK);
        List<TerrainTask> tasks = ACTIVE_TASKS.stream().filter(task -> task.level == serverLevel).toList();
        int count = tasks.size();
        int start = count == 0 ? 0 : Math.floorMod(turn.next, count);
        // Rotate the first serviced task so a large batch cannot starve the others.
        turn.next = count == 0 ? 0 : (start + 1) % count;
        processingActiveTasks = true;
        try {
            for (int index = 0; index < count; index++) {
                TerrainTask task = tasks.get((start + index) % count);
                if (task.tick(budget)) ACTIVE_TASKS.remove(task);
            }
        } finally {
            processingActiveTasks = false;
        }

        if (!PENDING_TASKS.isEmpty()) {
            ACTIVE_TASKS.addAll(PENDING_TASKS);
            PENDING_TASKS.clear();
        }

    }

}

