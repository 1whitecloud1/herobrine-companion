package com.whitecloud233.herobrine_companion.destructiongod.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

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

    public boolean tick(TickBudget budget) {
        if (this.delayTicks > 0) {
            this.delayTicks--;
            return false;
        }
        return this.doTick(budget);
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

    protected abstract boolean doTick(TickBudget budget);
}

