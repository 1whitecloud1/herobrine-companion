package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden; // 引入监守者
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class HeroKingAuraGoal extends Goal {
    private final HeroEntity hero;
    private final List<Mob> affectedMobs = new ArrayList<>();
    private static final double RANGE = 24.0D; // 普通怪物威压范围
    private static final double BOSS_RANGE = 64.0D; // Boss级别(龙、凋灵、监守者)威压范围

    private ResourceKey<Level> lastDimension;

    public HeroKingAuraGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!Config.heroKingAuraEnabled) return false;
        // [新增] 如果正在交易，暂时关闭光环，避免干扰
        if (this.hero.getTradingPlayer() != null) return false;
        return this.hero.isAlive() && !this.hero.isSpectator();
    }

    @Override
    public void start() {
        this.lastDimension = this.hero.level().dimension();
        scanForMonsters();
    }

    @Override
    public void stop() {
        super.stop();
        clearAllMobs();
    }

    @Override
    public void tick() {
        if (!Config.heroKingAuraEnabled) {
            clearAllMobs();
            return;
        }
        
        // [新增] 如果正在交易，立即停止并清除影响
        if (this.hero.getTradingPlayer() != null) {
            clearAllMobs();
            return;
        }

        ResourceKey<Level> currentDimension = this.hero.level().dimension();
        if (this.lastDimension != null && !this.lastDimension.equals(currentDimension)) {
            clearAllMobs();
            this.lastDimension = currentDimension;
        }

        this.affectedMobs.removeIf(mob -> {
            boolean shouldRemove = false;

            if (mob.level() != this.hero.level() || !mob.isAlive()) {
                shouldRemove = true;
            } else {
                // 检查距离：如果是 Boss 或监守者，使用更大的范围
                boolean isBoss = (mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden);
                double maxRange = isBoss ? BOSS_RANGE : RANGE;

                if (mob.distanceToSqr(this.hero) > (maxRange + 5) * (maxRange + 5)) {
                    shouldRemove = true;
                }
            }

            if (shouldRemove) {
                mob.getPersistentData().putBoolean("HeroSubmission", false);
                mob.setSilent(false);
                if (mob instanceof NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
                return true;
            }
            return false;
        });

        boolean shouldScan = this.affectedMobs.isEmpty() || this.hero.tickCount % 10 == 0;
        if (shouldScan) {
            scanForMonsters();
        }

        for (Mob mob : this.affectedMobs) {
            if (!mob.isAlive()) continue;

            if (mob instanceof EnderDragon dragon) {
                handleDragonSubmission(dragon);
            } else if (mob instanceof WitherBoss wither) {
                // 凋灵的特殊处理
                applySubmissionCalculations(mob);
                wither.setTarget(null); // 强制清除目标，停止发射骷髅头
            } else if (mob instanceof Warden warden) {
                // 监守者的特殊处理
                applySubmissionCalculations(mob);
                warden.setTarget(null); // 强制清除目标
                warden.clearAnger(this.hero); // 清除怒气，防止它使用声波攻击
            } else {
                applySubmissionCalculations(mob);
            }
        }
    }

    private void clearAllMobs() {
        for (Mob mob : this.affectedMobs) {
            if (mob.isAlive()) {
                mob.getPersistentData().putBoolean("HeroSubmission", false);
                mob.setSilent(false);
            }
        }
        this.affectedMobs.clear();
    }

    private void scanForMonsters() {
        // 1. 扫描普通怪物
        List<Mob> list = this.hero.level().getEntitiesOfClass(Mob.class,
                this.hero.getBoundingBox().inflate(RANGE),
                e -> {
                    if (!e.isAlive()) return false;
                    if (e == this.hero) return false;

                    // 排除末影龙和 HeroEntity (单独处理)
                    if (e instanceof EnderDragon || e instanceof HeroEntity) return false;

                    // 【修改点】移除了对 WitherBoss 的排除
                    // 监守者 (Warden) 属于 Enemy，原本也会被扫描到，只是现在我们给了它独立的大范围

                    boolean isEnemy = e instanceof Enemy;
                    boolean isMonster = e instanceof net.minecraft.world.entity.monster.Monster;

                    return isEnemy || isMonster;
                });

        for (Mob m : list) {
            if (!this.affectedMobs.contains(m)) {
                this.affectedMobs.add(m);
            }
        }

        // 2. 独立扫描范围更大的 Boss (龙、凋灵、监守者)

        // 末影龙
        List<EnderDragon> dragons = this.hero.level().getEntitiesOfClass(EnderDragon.class,
                this.hero.getBoundingBox().inflate(BOSS_RANGE));
        for (EnderDragon dragon : dragons) {
            if (dragon.isAlive() && !this.affectedMobs.contains(dragon)) {
                this.affectedMobs.add(dragon);
            }
        }

        // 凋灵
        List<WitherBoss> withers = this.hero.level().getEntitiesOfClass(WitherBoss.class,
                this.hero.getBoundingBox().inflate(BOSS_RANGE));
        for (WitherBoss wither : withers) {
            if (wither.isAlive() && !this.affectedMobs.contains(wither)) {
                this.affectedMobs.add(wither);
            }
        }

        // 监守者
        List<Warden> wardens = this.hero.level().getEntitiesOfClass(Warden.class,
                this.hero.getBoundingBox().inflate(BOSS_RANGE));
        for (Warden warden : wardens) {
            if (warden.isAlive() && !this.affectedMobs.contains(warden)) {
                this.affectedMobs.add(warden);
            }
        }
    }

    private void applySubmissionCalculations(Mob mob) {
        mob.getPersistentData().putBoolean("HeroSubmission", true);
        if (!mob.isSilent()) mob.setSilent(true);
        mob.getLookControl().setLookAt(this.hero.getX(), this.hero.getY(), this.hero.getZ());

        double dx = this.hero.getX() - mob.getX();
        double dz = this.hero.getZ() - mob.getZ();
        float targetYaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        mob.getPersistentData().putFloat("HeroSubmissionYaw", targetYaw);

        mob.setYRot(targetYaw);
        mob.setYBodyRot(targetYaw);
        mob.setYHeadRot(targetYaw);

        if (mob.tickCount % 10 == 0) {
            mob.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 0, -0.05, 0);
        }
    }

    private void handleDragonSubmission(EnderDragon dragon) {
        Vec3 look = this.hero.getLookAngle();
        double targetX = this.hero.getX() + look.x * 12.0;
        double targetZ = this.hero.getZ() + look.z * 12.0;
        double groundY = getGroundY(dragon.level(), targetX, targetZ, (int)this.hero.getY() + 10);

        double distToTargetXZ = dragon.distanceToSqr(targetX, dragon.getY(), targetZ);
        double verticalDiff = dragon.getY() - groundY;
        boolean isGrounded = dragon.onGround() || (verticalDiff < 0.5 && verticalDiff > -0.5);

        double sitThresholdSqr = 16.0;
        double takeoffThresholdSqr = 400.0;

        boolean isSitting = dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.SITTING_SCANNING;
        boolean shouldSit;

        if (isSitting) {
            shouldSit = distToTargetXZ < takeoffThresholdSqr;
        } else {
            shouldSit = isGrounded && distToTargetXZ < sitThresholdSqr;
        }

        if (shouldSit) {
            if (!isSitting) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
            }
            dragon.getPersistentData().putBoolean("HeroSubmission", true);
            if (!dragon.isSilent()) dragon.setSilent(true);

            dragon.setDeltaMovement(Vec3.ZERO);
            if (Math.abs(dragon.getY() - groundY) > 0.05) {
                dragon.setPos(dragon.getX(), groundY, dragon.getZ());
            }
            lookAtEntitySmoothly(dragon, this.hero, 5.0F);
            dragon.setXRot(25.0F);
        } else {
            dragon.getPersistentData().putBoolean("HeroSubmission", false);
            if (dragon.isSilent()) dragon.setSilent(false);

            if (isSitting) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                dragon.setDeltaMovement(0, 0.5, 0);
            } else if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOLDING_PATTERN) {
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            }

            double targetFlyY;
            if (distToTargetXZ > 625.0) {
                targetFlyY = groundY + 15.0;
            } else {
                targetFlyY = groundY - 2.0;
            }

            double dx = targetX - dragon.getX();
            double dy = targetFlyY - dragon.getY();
            double dz = targetZ - dragon.getZ();
            double speed = distToTargetXZ > 900.0 ? 0.8D : 0.45D;

            Vec3 intendedVel = new Vec3(dx, dy, dz).normalize().scale(speed);
            if (verticalDiff < 5.0 && intendedVel.y < -0.2) {
                intendedVel = new Vec3(intendedVel.x, -0.2, intendedVel.z);
            }

            dragon.setDeltaMovement(intendedVel);
            float targetYaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 180.0F;
            float newYaw = rotlerp(dragon.getYRot(), targetYaw, 4.0F);
            dragon.setYRot(newYaw);
            dragon.setYBodyRot(newYaw);
            dragon.hasImpulse = true;
        }
    }

    private void lookAtEntitySmoothly(Mob self, Mob target, float speed) {
        double dx = target.getX() - self.getX();
        double dz = target.getZ() - self.getZ();
        float targetYaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 180.0F;
        float newYaw = rotlerp(self.getYRot(), targetYaw, speed);
        self.setYRot(newYaw);
        self.setYBodyRot(newYaw);
    }

    private double getGroundY(Level level, double x, double z, int startY) {
        int iX = Mth.floor(x);
        int iZ = Mth.floor(z);
        for (int y = startY; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(iX, y - 1, iZ);
            if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).isSolid()) {
                return y;
            }
        }
        return startY;
    }

    private float rotlerp(float current, float target, float maxChange) {
        float f = Mth.wrapDegrees(target - current);
        if (f > maxChange) f = maxChange;
        if (f < -maxChange) f = -maxChange;
        return current + f;
    }
}