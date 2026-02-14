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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class HeroKingAuraGoal extends Goal {
    private final HeroEntity hero;
    private final List<Mob> affectedMobs = new ArrayList<>();
    private static final double RANGE = 24.0D; // 普通怪物威压范围
    private static final double DRAGON_RANGE = 64.0D; // 末影龙威压范围

    private ResourceKey<Level> lastDimension;

    public HeroKingAuraGoal(HeroEntity hero) {
        this.hero = hero;
        // 这里的 Flag 设置为 none 是对的，因为我们不想打断 Hero 自己的行动，
        // 也不想通过 Goal 优先级来竞争，而是直接在 tick 里控制其他生物。
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!Config.heroKingAuraEnabled) return false;
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
        // 当 Goal 停止时（例如配置被禁用，或者 Hero 死亡），必须清理所有受影响的生物
        clearAllMobs();
    }

    @Override
    public void tick() {
        // 双重检查配置，虽然 canUse 会控制 Goal 的启停，但 tick 内也保留检查以防万一
        if (!Config.heroKingAuraEnabled) {
            clearAllMobs();
            return;
        }

        ResourceKey<Level> currentDimension = this.hero.level().dimension();
        if (this.lastDimension != null && !this.lastDimension.equals(currentDimension)) {
            clearAllMobs();
            this.lastDimension = currentDimension;
        }

        // === 核心修复：清理逻辑 ===
        // 我们不能用简单的 removeIf，必须在移除前把 NBT 改回去
        this.affectedMobs.removeIf(mob -> {
            boolean shouldRemove = false;

            // 1. 检查有效性
            if (mob.level() != this.hero.level() || !mob.isAlive()) {
                shouldRemove = true;
            } else {
                // 2. 检查距离
                double maxRange = (mob instanceof EnderDragon) ? DRAGON_RANGE : RANGE;
                // 加上 5 格缓冲，防止在边缘反复横跳
                if (mob.distanceToSqr(this.hero) > (maxRange + 5) * (maxRange + 5)) {
                    shouldRemove = true;
                }
            }

            if (shouldRemove) {
                // 【重要】临走前解除封印
                mob.getPersistentData().putBoolean("HeroSubmission", false);
                // 恢复声音
                mob.setSilent(false);
                // 恢复 AI 状态
                if (mob instanceof NeutralMob neutral) {
                    neutral.stopBeingAngry(); // 确保它不会立刻反咬一口
                }
                return true; // 从列表移除
            }
            return false; // 保留
        });

        // 降低扫描频率，每 10 tick 扫描一次新目标
        boolean shouldScan = this.affectedMobs.isEmpty() || this.hero.tickCount % 10 == 0;
        if (shouldScan) {
            scanForMonsters();
        }

        // === 执行循环 ===
        for (Mob mob : this.affectedMobs) {
            if (!mob.isAlive()) continue;

            if (mob instanceof EnderDragon dragon) {
                handleDragonSubmission(dragon);
            } else {
                // 现在这里只需要负责计算朝向，不需要管姿态了 (Mixin 会管)
                applySubmissionCalculations(mob);
            }
        }
    }

    // 辅助方法：禁用全部
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
        // 扫描普通怪物
        List<Mob> list = this.hero.level().getEntitiesOfClass(Mob.class,
                this.hero.getBoundingBox().inflate(RANGE),
                e -> {
                    if (!e.isAlive()) return false;
                    if (e == this.hero) return false;

                    // 排除列表
                    if (e instanceof WitherBoss || e instanceof EnderDragon || e instanceof HeroEntity) return false;

                    boolean isEnemy = e instanceof Enemy;
                    boolean isMonster = e instanceof net.minecraft.world.entity.monster.Monster;

                    return isEnemy || isMonster;
                });

        for (Mob m : list) {
            if (!this.affectedMobs.contains(m)) {
                this.affectedMobs.add(m);
            }
        }

        // 独立扫描末影龙 (范围更大)
        List<EnderDragon> dragons = this.hero.level().getEntitiesOfClass(EnderDragon.class,
                this.hero.getBoundingBox().inflate(DRAGON_RANGE));

        for (EnderDragon dragon : dragons) {
            if (dragon.isAlive() && !this.affectedMobs.contains(dragon)) {
                this.affectedMobs.add(dragon);
            }
        }
    }

    // 重构的普通怪物逻辑
    private void applySubmissionCalculations(Mob mob) {
        // 1. 打上标记
        mob.getPersistentData().putBoolean("HeroSubmission", true);

        // 2. 强制静音 (解决龙扇翅膀和怪物低吼的问题)
        if (!mob.isSilent()) mob.setSilent(true);

        // 3. 清除原版“看人”的 AI 干扰
        // 这行非常关键！告诉 AI 系统：“你现在没有想看的东西”
        mob.getLookControl().setLookAt(this.hero.getX(), this.hero.getY(), this.hero.getZ());

        // 4. 计算强制朝向 (面向 Herobrine)
        double dx = this.hero.getX() - mob.getX();
        double dz = this.hero.getZ() - mob.getZ();
        // Minecraft 的角度计算公式：atan2 算出来是弧度，转角度，-90 是修正模型朝向
        float targetYaw = (float)(Math.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        // 5. 将计算好的“目标角度”存入 NBT
        // 这样 Mixin 就能直接读取这个数值，不需要知道 Herobrine 在哪
        mob.getPersistentData().putFloat("HeroSubmissionYaw", targetYaw);

        // 6. 在 Goal 层先做一次暴力设置 (双重保险)
        mob.setYRot(targetYaw);
        mob.setYBodyRot(targetYaw);
        mob.setYHeadRot(targetYaw); // 强行把头扭过来

        // 7. 粒子效果 (保持不变)
        if (mob.tickCount % 10 == 0) {
            mob.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    mob.getX(), mob.getEyeY() + 0.5, mob.getZ(), 0, -0.05, 0);
        }
    }

    // === 末影龙逻辑保持你之前的版本 ===
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
            if (!dragon.isSilent()) dragon.setSilent(true); // 强制静音

            dragon.setDeltaMovement(Vec3.ZERO);
            if (Math.abs(dragon.getY() - groundY) > 0.05) {
                dragon.setPos(dragon.getX(), groundY, dragon.getZ());
            }
            lookAtEntitySmoothly(dragon, this.hero, 5.0F);
            dragon.setXRot(25.0F);
        } else {
            dragon.getPersistentData().putBoolean("HeroSubmission", false);
            if (dragon.isSilent()) dragon.setSilent(false); // 恢复声音

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