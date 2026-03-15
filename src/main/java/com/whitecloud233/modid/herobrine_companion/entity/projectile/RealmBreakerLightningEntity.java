package com.whitecloud233.modid.herobrine_companion.entity.projectile;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
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
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    protected void defineSynchedData() {
        this.entityData.define(DAMAGE, 10.0F);
        this.entityData.define(EXPLOSION_RADIUS, 2.0F);
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
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        // 修改：使用 magic() 伤害源，避免被雷电免疫
        target.hurt(this.damageSources().magic(), this.getDamage());
        this.impact(target.blockPosition());
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
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

            // 1. 计算爆炸参数
            float radius = this.getExplosionRadius();
            DamageSource damageSource = this.damageSources().explosion(this, this.getOwner());
            Vec3 center = Vec3.atCenterOf(pos);

            // 2. 手动伤害处理 (排除 Owner 和 HeroEntity)
            AABB aabb = new AABB(pos).inflate(radius);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, aabb);

            for (LivingEntity entity : entities) {
                // 排除 HeroEntity 和 射击者(玩家)
                if (entity instanceof HeroEntity || (this.getOwner() != null && entity.is(this.getOwner()))) {
                    continue;
                }
                
                double distance = entity.position().distanceTo(center);
                if (distance < radius) {
                    // 伤害衰减
                    float damage = this.getDamage() * (1.0F - (float)(distance / radius));
                    entity.hurt(damageSource, damage);
                    
                    // 击退效果 (模拟爆炸推力)
                    double kbStrength = 1.0 - distance / radius; 
                    Vec3 dir = entity.position().subtract(center).normalize();
                    if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 1, 0); // 避免零向量
                    entity.push(dir.x * kbStrength, dir.y * kbStrength, dir.z * kbStrength);
                }
            }

            // 3. 视觉效果 (粒子 & 声音) - 替代 serverLevel.explode 以避免误伤
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
            serverLevel.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, (1.0F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.2F) * 0.7F);
            serverLevel.playSound(null, pos, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.0F, 1.0F);

            // 4. 方块破坏 (如果配置启用)
            if (Config.poemOfTheEndExplosion) {
                int r = (int) Math.ceil(radius);
                for (int x = -r; x <= r; x++) {
                    for (int y = -r; y <= r; y++) {
                        for (int z = -r; z <= r; z++) {
                            double distSqr = x*x + y*y + z*z;
                            if (distSqr <= radius * radius) {
                                BlockPos p = pos.offset(x, y, z);
                                BlockState state = serverLevel.getBlockState(p);
                                
                                // 不破坏空气、基岩等不可破坏方块
                                if (!state.isAir() && state.getDestroySpeed(serverLevel, p) >= 0) {
                                    serverLevel.destroyBlock(p, true);
                                }
                            }
                        }
                    }
                }
            }

            this.discard();
        }
    }
}