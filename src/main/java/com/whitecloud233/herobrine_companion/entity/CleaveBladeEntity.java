package com.whitecloud233.herobrine_companion.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

// 1.21.1 更新：IEntityAdditionalSpawnData 接口被重命名为 IEntityWithComplexSpawn
public class CleaveBladeEntity extends Entity implements IEntityWithComplexSpawn {

    private int lifeSpan = 100;

    public CleaveBladeEntity(EntityType<?> type, Level level) {
        super(type, level);
        // noPhysics 在 1.21.1 中依然可用，但建议通过实体类型 Builder 设置更佳，这里保留你的原始逻辑
        this.noPhysics = true;
    }

    public CleaveBladeEntity(EntityType<?> type, Level level, double x, double y, double z, double dirX, double dirZ, int maxLife) {
        this(type, level);
        this.setPos(x, y, z);
        this.setDeltaMovement(dirX * 1.0, 0, dirZ * 1.0);
        this.lifeSpan = maxLife;

        float yRot = (float)(Math.atan2(dirZ, dirX) * (180D / Math.PI)) - 90.0F;
        this.setYRot(yRot);
        this.yRotO = yRot;
    }

    // 1.21.1 更新：defineSynchedData 现在必须接收一个 Builder 参数
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 如果有需要同步的 EntityDataAccessor，现在使用 builder.define(KEY, value) 来注册
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.lifeSpan = tag.getInt("LifeSpan");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeSpan", this.lifeSpan);
    }

    @Override
    public void tick() {
        super.tick();
        this.yRotO = this.getYRot();
        this.setPos(this.getX() + this.getDeltaMovement().x, this.getY(), this.getZ() + this.getDeltaMovement().z);
        if (this.tickCount >= this.lifeSpan) {
            this.discard();
        }
    }

    // 1.21.1 更新：彻底删除了 getAddEntityPacket 方法！
    // NeoForge 和原版现在会自动处理实体生成数据包，无需再手动调用 NetworkHooks

    // ==========================================
    // 发送端：服务端生成实体时，把速度和角度写进数据包
    // 1.21.1 更新：FriendlyByteBuf 变更为 RegistryFriendlyByteBuf
    // ==========================================
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(this.getDeltaMovement().x);
        buffer.writeDouble(this.getDeltaMovement().y);
        buffer.writeDouble(this.getDeltaMovement().z);
        buffer.writeFloat(this.getYRot());
        buffer.writeInt(this.lifeSpan);
    }

    // ==========================================
    // 接收端：客户端收到实体时，立刻读取速度和角度！
    // 1.21.1 更新：同样变更为 RegistryFriendlyByteBuf
    // ==========================================
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        this.setDeltaMovement(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble());
        this.setYRot(additionalData.readFloat());
        this.yRotO = this.getYRot();
        this.lifeSpan = additionalData.readInt();
    }
}