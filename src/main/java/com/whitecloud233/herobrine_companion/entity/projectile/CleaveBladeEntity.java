package com.whitecloud233.herobrine_companion.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

// 1.21.1 更新：IEntityAdditionalSpawnData 接口被重命名为 IEntityWithComplexSpawn
public class CleaveBladeEntity extends Entity implements IEntityWithComplexSpawn {

    private int lifeSpan = 100;
    private float slashRollDegrees;

    public CleaveBladeEntity(EntityType<?> type, Level level) {
        super(type, level);
        // noPhysics 在 1.21.1 中依然可用，但建议通过实体类型 Builder 设置更佳，这里保留你的原始逻辑
        this.noPhysics = true;
    }

    public CleaveBladeEntity(EntityType<?> type, Level level, double x, double y, double z, double dirX, double dirZ, int maxLife) {
        this(type, level, x, y, z, dirX, dirZ, maxLife, 0.0F);
    }

    public CleaveBladeEntity(EntityType<?> type, Level level, double x, double y, double z, double dirX, double dirZ, int maxLife, float slashRollDegrees) {
        this(type, level);
        this.setPos(x, y, z);
        this.setDeltaMovement(dirX * 1.0, 0, dirZ * 1.0);
        this.lifeSpan = maxLife;
        this.slashRollDegrees = slashRollDegrees;

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
        this.slashRollDegrees = tag.getFloat("SlashRoll");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeSpan", this.lifeSpan);
        tag.putFloat("SlashRoll", this.slashRollDegrees);
    }

    @Override
    public void tick() {
        super.tick();
        this.yRotO = this.getYRot();
        Vec3 previousPos = this.position();
        Vec3 motion = this.getDeltaMovement();
        this.setPos(previousPos.x + motion.x, previousPos.y, previousPos.z + motion.z);
        if (this.level().isClientSide) {
            this.spawnSpatialTrail(previousPos, this.position(), motion);
        }
        if (this.tickCount >= this.lifeSpan) {
            this.discard();
        }
    }

    private void spawnSpatialTrail(Vec3 start, Vec3 end, Vec3 motion) {
        Vec3 flatMotion = new Vec3(motion.x, 0.0D, motion.z);
        if (flatMotion.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 direction = flatMotion.normalize();
        double rollRadians = Math.toRadians(this.slashRollDegrees);
        Vec3 perpendicular = new Vec3(-direction.z, 0.0D, direction.x);
        Vec3 slashAxis = perpendicular.scale(Math.cos(rollRadians)).add(0.0D, Math.sin(rollRadians), 0.0D).normalize();
        double travel = Math.max(1.0D, start.distanceTo(end));
        int samples = Math.max(8, Mth.ceil(travel * 5.0D));
        double edgeDistance = 3.4D;
        double crackHalfLength = 2.8D;

        for (int i = 0; i <= samples; i++) {
            double progress = i / (double) samples;
            Vec3 base = start.lerp(end, progress);
            Vec3 crackPoint = base.add(direction.scale(Mth.lerp(progress, -crackHalfLength, crackHalfLength)));
            double crackY = crackPoint.y + 0.55D + this.random.nextDouble() * 0.75D;

            this.level().addParticle(ParticleTypes.END_ROD, crackPoint.x, crackY, crackPoint.z, 0.0D, 0.015D, 0.0D);
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, crackPoint.x, crackY + 0.05D, crackPoint.z, 0.0D, 0.02D, 0.0D);

            if ((i & 1) == 0) {
                this.level().addParticle(ParticleTypes.FLASH, crackPoint.x, crackY + 0.08D, crackPoint.z, 0.0D, 0.0D, 0.0D);
            }

            if (i % 2 == 0) {
                Vec3 leftEdge = base.add(slashAxis.scale(edgeDistance));
                Vec3 rightEdge = base.add(slashAxis.scale(-edgeDistance));
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, leftEdge.x, leftEdge.y + 0.25D, leftEdge.z, 0.0D, 0.02D, 0.0D);
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, rightEdge.x, rightEdge.y + 0.25D, rightEdge.z, 0.0D, 0.02D, 0.0D);

                if (i % 4 == 0) {
                    this.level().addParticle(ParticleTypes.FLASH, leftEdge.x, leftEdge.y + 0.5D, leftEdge.z, 0.0D, 0.0D, 0.0D);
                    this.level().addParticle(ParticleTypes.FLASH, rightEdge.x, rightEdge.y + 0.5D, rightEdge.z, 0.0D, 0.0D, 0.0D);
                }
            }
        }

        Vec3 head = end.add(direction.scale(1.4D));
        this.level().addParticle(ParticleTypes.FLASH, head.x, head.y + 0.85D, head.z, 0.0D, 0.0D, 0.0D);
        this.level().addParticle(ParticleTypes.CLOUD, head.x, head.y + 0.7D, head.z, 0.0D, 0.01D, 0.0D);
    }

    public float getSlashRollDegrees() {
        return this.slashRollDegrees;
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
        buffer.writeFloat(this.slashRollDegrees);
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
        this.slashRollDegrees = additionalData.readFloat();
    }
}
