package com.whitecloud233.herobrine_companion.entity.projectile;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

            // 【核心修改】手动实现爆炸伤害，以排除 HeroEntity 和 玩家
            float radius = this.getExplosionRadius();
            AABB aabb = new AABB(pos).inflate(radius);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, aabb);
            DamageSource damageSource = this.damageSources().explosion(this, this.getOwner());

            for (LivingEntity entity : entities) {
                // 如果是 HeroEntity，则跳过，不造成伤害
                if (entity instanceof HeroEntity) {
                    continue;
                }
                
                // 【新增】：如果 entity 是发射者 (玩家)，也跳过
                if (this.getOwner() != null && entity.is(this.getOwner())) {
                    continue;
                }

                // 计算伤害衰减
                double distance = entity.position().distanceTo(Vec3.atCenterOf(pos));
                if (distance < radius) {
                    float damage = this.getDamage() * (1.0F - (float)(distance / radius));
                    entity.hurt(damageSource, damage);
                    
                    // 【新增】手动添加击退效果 (模拟爆炸击退)
                    double dX = entity.getX() - this.getX();
                    // 这里 entity 是 LivingEntity 类型，不可能是 PrimedTnt 类型，
                    // 所以这里的 instanceof 检查是多余且导致错误的。
                    // 实际上我们也不需要针对 TNT 做特殊处理，因为 entities 列表里只有 LivingEntity。
                    double dY = entity.getEyeY() - this.getY();
                    double dZ = entity.getZ() - this.getZ();
                    double dist = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
                    if (dist != 0.0) {
                        dX /= dist;
                        dY /= dist;
                        dZ /= dist;
                        double density = (1.0 - distance / radius);
                        // 添加击退，力度稍微调小一点，模拟爆炸击飞
                        entity.push(dX * density * 2.0, dY * density * 2.0, dZ * density * 2.0);
                        entity.hurtMarked = true;
                    }
                }
            }

            // 【核心修改】移除 serverLevel.explode，改为手动处理方块破坏和粒子
            // 这样可以彻底避免 explode 方法对玩家造成的二次伤害
            
            // 1. 粒子效果
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

            // 2. 方块破坏 (如果配置启用)
            if (Config.poemOfTheEndExplosion) {
                int r = (int) Math.ceil(radius);
                BlockPos center = pos;
                for (int x = -r; x <= r; x++) {
                    for (int y = -r; y <= r; y++) {
                        for (int z = -r; z <= r; z++) {
                            if (x*x + y*y + z*z <= radius*radius) {
                                BlockPos p = center.offset(x, y, z);
                                BlockState state = serverLevel.getBlockState(p);
                                // 限制只能破坏可破坏方块，并排除基岩等
                                if (!state.isAir() && state.getDestroySpeed(serverLevel, p) >= 0) {
                                     serverLevel.destroyBlock(p, true);
                                }
                            }
                        }
                    }
                }
            }

            // 播放声音
            serverLevel.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 5.0F, 1.0F);

            this.discard();
        }
    }
}