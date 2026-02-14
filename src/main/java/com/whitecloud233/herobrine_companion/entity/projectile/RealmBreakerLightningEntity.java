package com.whitecloud233.herobrine_companion.entity.projectile;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class RealmBreakerLightningEntity extends Projectile {
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(RealmBreakerLightningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EXPLOSION_RADIUS = SynchedEntityData.defineId(RealmBreakerLightningEntity.class, EntityDataSerializers.FLOAT);

    public RealmBreakerLightningEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    public RealmBreakerLightningEntity(Level level, LivingEntity shooter, float damage, float explosionRadius) {
        this(ModEvents.REALM_BREAKER_LIGHTNING.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.setDamage(damage);
        this.setExplosionRadius(explosionRadius);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DAMAGE, 10.0F);
        builder.define(EXPLOSION_RADIUS, 2.0F);
    }

    public void setDamage(float damage) {
        this.entityData.set(DAMAGE, damage);
    }

    public float getDamage() {
        return this.entityData.get(DAMAGE);
    }

    public void setExplosionRadius(float radius) {
        this.entityData.set(EXPLOSION_RADIUS, radius);
    }

    public float getExplosionRadius() {
        return this.entityData.get(EXPLOSION_RADIUS);
    }

    @Override
    public void tick() {
        super.tick();

        // 简单的运动逻辑
        Vec3 movement = this.getDeltaMovement();
        double nextX = this.getX() + movement.x;
        double nextY = this.getY() + movement.y;
        double nextZ = this.getZ() + movement.z;
        // 更新旋转
        this.updateRotation();
        // 碰撞检测
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
        }

        this.setPos(nextX, nextY, nextZ);
        
        // 粒子效果 (雷电轨迹)
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }

        // 超时销毁
        if (this.tickCount > 100) {
            this.discard();
        }
    }

    protected void updateRotation() {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-7D) {
            double horizontalDistance = velocity.horizontalDistance();
            this.setYRot((float)(Mth.atan2(velocity.x, velocity.z) * (double)(180F / (float)Math.PI)));
            this.setXRot((float)(Mth.atan2(velocity.y, horizontalDistance) * (double)(180F / (float)Math.PI)));
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        // 使用魔法伤害以绕过护甲，并防止被免疫雷电的生物（如女巫）免疫
        DamageSource damageSource = this.damageSources().source(DamageTypes.MAGIC, this.getOwner());
        target.hurt(damageSource, this.getDamage());
        this.impact(target.blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.impact(result.getBlockPos());
    }

    private void impact(BlockPos pos) {
        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            
            // 召唤视觉雷电
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(Vec3.atBottomCenterOf(pos));
                lightning.setVisualOnly(true);
                serverLevel.addFreshEntity(lightning);
            }

            // 爆炸
            // 检查配置是否启用爆炸
            // 强制重新读取配置，以防静态变量未更新
            if (Config.poemOfTheEndExplosion) {
                // 使用 this 作为 source，以便在 ExplosionEvent 中识别
                // 同时指定 DamageSource 为 explosion(this, owner)，确保击杀信息正确
                // 改为 BLOCK 以破坏方块
                serverLevel.explode(this, this.damageSources().explosion(this, this.getOwner()), null,
                        this.getX(), this.getY(), this.getZ(),
                        this.getExplosionRadius(), false, Level.ExplosionInteraction.BLOCK);
            }
            // 播放声音
            serverLevel.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 5.0F, 1.0F);
            
            this.discard();
        }
    }
}