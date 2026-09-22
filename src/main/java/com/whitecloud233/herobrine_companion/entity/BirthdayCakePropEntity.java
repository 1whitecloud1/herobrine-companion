package com.whitecloud233.herobrine_companion.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 无名之蛋糕摆件 —— Bedrock `herobrine_companion:birthday_cake_prop` 的移植(NeoForge 1.21.1)。
 *
 * <p>由 {@code HeroBirthdayCakeService} 在玩家「潜行 + 使用蛋糕」时生成:
 * 站在被指定的方块格上、朝向玩家,不参与物理与战斗,不会阻挡玩家。
 * 模型与烛火动画见 {@code client.model.BirthdayCakePropModel}
 * (由 gen-cake-prop-model.ps1 从 Bedrock 的 birthday_cake.geo.json 生成)。
 *
 * <p>1.21 适配:`defineSynchedData(SynchedEntityData.Builder)`;NeoForge 不再需要
 * 覆写 `getAddEntityPacket()`。
 */
public class BirthdayCakePropEntity extends Entity {

    /** 火焰相对实体原点的抬升高度(模型单位 8.08/16 ≈ 0.505 格 + 一点余量)。 */
    private static final double FLAME_HEIGHT = 0.66D;

    public BirthdayCakePropEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // 无同步字段
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        // 无额外存档数据
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // 无额外存档数据
    }

    /** 不可被射线选中:收回走"同一格"判定,不需要实体交互。 */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }


    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 5 == 0) {
            // 烛火:与 Bedrock prop 的火焰表现对应(客户端另有模型自身的摆动动画)
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY() + FLAME_HEIGHT, this.getZ(),
                    1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }
}