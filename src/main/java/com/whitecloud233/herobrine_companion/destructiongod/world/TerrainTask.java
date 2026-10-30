package com.whitecloud233.herobrine_companion.destructiongod.world;

import java.util.function.IntPredicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

abstract class TerrainTask {
    protected final ServerLevel level;
    @Nullable
    protected final UUID casterUuid;
    protected final Vec3 origin;
    protected final Vec3 forward;
    protected final Vec3 perpendicular;
    protected final double maxLength;
    protected int delayTicks;
    protected double currentLength;
    private final TerrainMutationQueue mutations = new TerrainMutationQueue();
    private boolean stagesComplete;
    private TickBudget activeBudget;

    protected TerrainTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int delayTicks) {
        this.level = level;
        this.casterUuid = caster == null ? null : caster.getUUID();
        this.origin = origin;
        this.forward = DestructionTerrainManager.flattenLook(direction);
        this.perpendicular = new Vec3(-this.forward.z, 0.0D, this.forward.x);
        this.maxLength = maxLength;
        this.delayTicks = Math.max(0, delayTicks);
        this.currentLength = 0.0D;
    }

    public final boolean tick(TickBudget budget) {
        if (this.delayTicks > 0) {
            this.delayTicks--;
            return false;
        }
        if (!this.mutations.drain(budget)) return false;
        if (this.stagesComplete) return true;
        // Preserve the logical phase on exhausted shared budget, including one-shot effects.
        if (!budget.hasBudget()) return false;
        this.activeBudget = budget;
        try {
            this.stagesComplete = this.doTick(budget);
        } finally {
            this.activeBudget = null;
        }
        boolean drained = this.mutations.drain(budget);
        return this.stagesComplete && drained;
    }

    protected final void queueClearBlocks(List<BlockPos> positions, DestructionMode mode, boolean absolute) {
        this.queueMutationBatch(DestructionTerrainManager.clearBlocksBatch(this.level, positions, mode, absolute));
    }

    protected final void queueFaultChanges(List<BlockPos> clear, List<BlockPos> annihilate,
                                          Map<BlockPos, BlockState> placements, DestructionMode mode) {
        this.queueMutationBatch(DestructionTerrainManager.faultChangesBatch(this.level, clear, annihilate, placements, mode));
    }

    protected final void queueMutationBatch(TerrainMutationBatch batch) {
        this.mutations.add(batch);
        // Preserve the original clear-before-effects ordering while budget remains.
        if (this.activeBudget != null) this.mutations.drain(this.activeBudget);
    }

    @Nullable
    protected LivingEntity getCaster() {
        if (this.casterUuid == null) {
            return null;
        }
        Entity entity = this.level.getEntity(this.casterUuid);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    protected Vec3 centerPoint() {
        return this.origin.add(this.forward.scale(this.currentLength));
    }

    /** False from step means that task cannot continue (for example an unloaded center). */
    protected final boolean sweep(int steps, double stepSize, IntPredicate step) {
        return sweep(steps, stepSize, step, () -> {});
    }

    protected final boolean sweep(int steps, double stepSize, IntPredicate step, Runnable completed) {
        for (int index = 0; index < steps; index++) {
            if (this.currentLength >= this.maxLength) {
                completed.run();
                return true;
            }
            if (!step.test(index)) return true;
            this.currentLength += stepSize;
            if (!this.mutations.isEmpty() || (this.activeBudget != null && !this.activeBudget.hasBudget())) return false;
        }
        return false;
    }

    protected abstract boolean doTick(TickBudget budget);
}

