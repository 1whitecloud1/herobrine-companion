package com.whitecloud233.herobrine_companion.entity;

import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

public class VoidRiftEntity extends Entity {

    private static final EntityDataAccessor<Float> ROTATION = SynchedEntityData.defineId(VoidRiftEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerUUID;
    private int lifeTime = 0;
    private static final int MAX_LIFE_TIME = 31; // 1.5秒

    public VoidRiftEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
    }

    public VoidRiftEntity(Level level, double x, double y, double z, UUID ownerUUID) {
        this(ModEvents.VOID_RIFT.get(), level);
        this.setPos(x, y, z);
        this.ownerUUID = ownerUUID;
        // 设置随机旋转 (0-360)
        this.entityData.set(ROTATION, this.random.nextFloat() * 360.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROTATION, 0.0F);
    }

    public float getRotation() {
        return this.entityData.get(ROTATION);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.lifeTime = compound.getInt("LifeTime");
        if (compound.hasUUID("Owner")) {
            this.ownerUUID = compound.getUUID("Owner");
        }
        if (compound.contains("RiftRotation")) {
            this.entityData.set(ROTATION, compound.getFloat("RiftRotation"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("LifeTime", this.lifeTime);
        if (this.ownerUUID != null) {
            compound.putUUID("Owner", this.ownerUUID);
        }
        compound.putFloat("RiftRotation", this.getRotation());
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
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide) {
            this.lifeTime++;
            if (this.lifeTime >= MAX_LIFE_TIME) {
                this.discard();
                return;
            }

            // 伤害逻辑 (每 10 tick / 0.5秒)
            if (this.lifeTime % 10 == 0 && this.level() instanceof ServerLevel serverLevel) {
                HeroWorldData data = HeroWorldData.get(serverLevel);
                // [修复] 使用 ownerUUID 获取信任度，如果 ownerUUID 为空则默认为 0
                int trust = (this.ownerUUID != null) ? data.getTrust(this.ownerUUID) : 0;
                float baseDamage = 4.0F + (trust / 20.0F);

                // [修改] 增大伤害范围
                AABB box = this.getBoundingBox().inflate(2.5);
                List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box, 
                        e -> !e.getUUID().equals(this.ownerUUID) && !(e instanceof HeroEntity));

                for (LivingEntity target : targets) {
                    Entity ownerEntity = null;
                    float damage = baseDamage;

                    if (this.ownerUUID != null) {
                        ownerEntity = serverLevel.getEntity(this.ownerUUID);
                        // 如果主人是玩家且手持终末之诗，计算附魔加成
                        if (ownerEntity instanceof Player player) {
                            ItemStack mainHandItem = player.getMainHandItem();
                            if (mainHandItem.getItem() instanceof PoemOfTheEndItem) {
                                damage = EnchantmentHelper.modifyDamage(serverLevel, mainHandItem, target, this.damageSources().playerAttack(player), baseDamage);
                            }
                        }
                    }
                    
                    target.invulnerableTime = 0;
                    
                    if (ownerEntity instanceof Player player) {
                        target.hurt(this.damageSources().playerAttack(player), damage);
                    } else {
                        target.hurt(this.damageSources().magic(), damage);
                    }
                }
            }
        } else {
            // 客户端粒子效果
            if (this.random.nextFloat() < 0.3F) {
                this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, 
                        this.getX() + (this.random.nextDouble() - 0.5), 
                        this.getY() + (this.random.nextDouble() * 2.0), 
                        this.getZ() + (this.random.nextDouble() - 0.5), 
                        0, 0, 0);
            }
        }
    }
}
