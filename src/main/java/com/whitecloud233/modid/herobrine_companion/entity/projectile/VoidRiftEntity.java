package com.whitecloud233.modid.herobrine_companion.entity.projectile;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
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

    private static final EntityDataAccessor<Float> ROTATION =
            SynchedEntityData.defineId(VoidRiftEntity.class, EntityDataSerializers.FLOAT);

    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(VoidRiftEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> VISUAL_ONLY =
            SynchedEntityData.defineId(VoidRiftEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int MAX_LIFE_TIME = 24;

    private UUID ownerUUID;
    private int lifeTime = 0;

    public VoidRiftEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
    }

    public VoidRiftEntity(Level level, double x, double y, double z, UUID ownerUUID) {
        this(ModEntities.VOID_RIFT.get(), level);
        this.setPos(x, y, z);
        this.ownerUUID = ownerUUID;
        this.entityData.set(ROTATION, this.random.nextFloat() * 360.0F);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ROTATION, 0.0F);
        this.entityData.define(TARGET_ID, -1);
        this.entityData.define(VISUAL_ONLY, false);
    }

    public float getRotation() {
        return this.entityData.get(ROTATION);
    }

    public void setTarget(Entity target) {
        if (target == null) {
            this.entityData.set(TARGET_ID, -1);
        } else {
            this.entityData.set(TARGET_ID, target.getId());
        }
    }

    public Entity getTarget() {
        int id = this.entityData.get(TARGET_ID);
        if (id < 0 || this.level() == null) {
            return null;
        }
        return this.level().getEntity(id);
    }

    public void setVisualOnly(boolean visualOnly) {
        this.entityData.set(VISUAL_ONLY, visualOnly);
    }

    public boolean isVisualOnly() {
        return this.entityData.get(VISUAL_ONLY);
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

        if (compound.contains("TargetId")) {
            this.entityData.set(TARGET_ID, compound.getInt("TargetId"));
        }

        if (compound.contains("VisualOnly")) {
            this.entityData.set(VISUAL_ONLY, compound.getBoolean("VisualOnly"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putInt("LifeTime", this.lifeTime);

        if (this.ownerUUID != null) {
            compound.putUUID("Owner", this.ownerUUID);
        }

        compound.putFloat("RiftRotation", this.getRotation());
        compound.putInt("TargetId", this.entityData.get(TARGET_ID));
        compound.putBoolean("VisualOnly", this.isVisualOnly());
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

            if (this.isVisualOnly()) {
                return;
            }

            if (this.lifeTime % 10 == 0 && this.level() instanceof ServerLevel serverLevel) {
                HeroWorldData data = HeroWorldData.get(serverLevel);

                int trust = 0;
                if (this.ownerUUID != null) {
                    trust = data.getTrust(this.ownerUUID);
                }

                float baseDamage = 4.0F + (trust / 20.0F);

                AABB box = this.getBoundingBox().inflate(2.5);
                List<LivingEntity> targets = this.level().getEntitiesOfClass(
                        LivingEntity.class,
                        box,
                        e -> !e.getUUID().equals(this.ownerUUID) && !(e instanceof HeroEntity)
                );

                for (LivingEntity hurtTarget : targets) {
                    Entity ownerEntity = null;

                    if (this.ownerUUID != null) {
                        ownerEntity = serverLevel.getEntity(this.ownerUUID);
                    }

                    hurtTarget.invulnerableTime = 0;

                    float damage = baseDamage;

                    if (ownerEntity instanceof Player player) {
                        ItemStack mainHandItem = player.getMainHandItem();

                        if (mainHandItem.getItem() instanceof PoemOfTheEndItem) {
                            damage += EnchantmentHelper.getDamageBonus(mainHandItem, hurtTarget.getMobType());
                        }

                        hurtTarget.hurt(this.damageSources().playerAttack(player), damage);
                    } else {
                        hurtTarget.hurt(this.damageSources().magic(), damage);
                    }
                }
            }
        } else {
            if (this.random.nextFloat() < 0.65F) {
                this.level().addParticle(
                        ParticleTypes.ELECTRIC_SPARK,
                        this.getX() + (this.random.nextDouble() - 0.5D) * 1.8D,
                        this.getY() + (this.random.nextDouble() - 0.5D) * 2.4D,
                        this.getZ() + (this.random.nextDouble() - 0.5D) * 1.8D,
                        0.0D,
                        0.015D,
                        0.0D
                );
            }
        }
    }
}
