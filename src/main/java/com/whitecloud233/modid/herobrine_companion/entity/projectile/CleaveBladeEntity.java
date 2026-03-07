package com.whitecloud233.modid.herobrine_companion.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

// 【关键修复】：实现 IEntityAdditionalSpawnData 接口，强制同步出生数据！
public class CleaveBladeEntity extends Entity implements IEntityAdditionalSpawnData {

    private int lifeSpan = 100;

    public CleaveBladeEntity(EntityType<?> type, Level level) {
        super(type, level);
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

    @Override
    protected void defineSynchedData() {}

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

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // ==========================================
    // 发送端：服务端生成实体时，把速度和角度写进数据包
    // ==========================================
    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeDouble(this.getDeltaMovement().x);
        buffer.writeDouble(this.getDeltaMovement().y);
        buffer.writeDouble(this.getDeltaMovement().z);
        buffer.writeFloat(this.getYRot());
        buffer.writeInt(this.lifeSpan);
    }

    // ==========================================
    // 接收端：客户端收到实体时，立刻读取速度和角度！
    // ==========================================
    @Override
    public void readSpawnData(FriendlyByteBuf additionalData) {
        this.setDeltaMovement(additionalData.readDouble(), additionalData.readDouble(), additionalData.readDouble());
        this.setYRot(additionalData.readFloat());
        this.yRotO = this.getYRot();
        this.lifeSpan = additionalData.readInt();
    }
}