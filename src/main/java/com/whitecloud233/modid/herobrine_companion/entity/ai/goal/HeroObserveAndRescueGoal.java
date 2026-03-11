package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroObserveAndRescueGoal extends Goal {
    private final HeroEntity hero;
    private Player targetPlayer;

    private static final int COMBAT_TIMEOUT = 100;
    private static final float RESCUE_HEALTH_THRESHOLD = 6.0F;
    // 添加 10 分钟的冷却常量 (10分钟 * 60秒 * 20ticks)
    private static final int RESCUE_COOLDOWN = 12000;

    private long lastRescueTime = 0;

    public HeroObserveAndRescueGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean isOwner(Player player) {
        return this.hero.isCompanionMode() && this.hero.getOwnerUUID() != null && player.getUUID().equals(this.hero.getOwnerUUID());
    }

    @Override
    public boolean canUse() {
        if (this.hero.level().getGameTime() - this.hero.getLastSummonedTime() < 40) {
            return false;
        }

        Player player = this.hero.level().getNearestPlayer(this.hero, 32.0D);
        if (player == null || !player.isAlive()) return false;

        boolean isCompanionOwner = isOwner(player);
        long currentTime = this.hero.level().getGameTime();

        // 使用 10 分钟冷却常量
        if (isCompanionOwner && player.getHealth() <= RESCUE_HEALTH_THRESHOLD && (currentTime - this.lastRescueTime) > RESCUE_COOLDOWN) {
            this.targetPlayer = player;
            return true;
        }

        if (isInCombat(player)) {
            this.targetPlayer = player;
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) return false;

        boolean isCompanionOwner = isOwner(this.targetPlayer);
        long currentTime = this.hero.level().getGameTime();

        // 使用 10 分钟冷却常量
        if (isCompanionOwner && (currentTime - this.lastRescueTime) <= RESCUE_COOLDOWN) {
            return isInCombat(this.targetPlayer);
        }

        if (!isCompanionOwner && this.hero.distanceToSqr(this.targetPlayer) > 576.0D) {
            return false;
        }

        // 使用 10 分钟冷却常量
        return isInCombat(this.targetPlayer) || (isCompanionOwner && this.targetPlayer.getHealth() <= RESCUE_HEALTH_THRESHOLD && (currentTime - this.lastRescueTime) > RESCUE_COOLDOWN);
    }

    /**
     * [关键修复] 统一战斗判定逻辑，与 HeroGodlyCompanionGoal 保持一致。
     * 只有在玩家实际受伤或攻击时才判定为战斗，而不是有怪物盯上就算。
     */
    private boolean isInCombat(Player player) {
        int currentTick = player.tickCount;
        int lastHurtByMobTime = player.getLastHurtByMobTimestamp();
        int lastHurtMobTime = player.getLastHurtMobTimestamp();

        boolean recentlyHurt = lastHurtByMobTime > 0 && (currentTick - lastHurtByMobTime) < COMBAT_TIMEOUT;
        boolean recentlyAttacked = lastHurtMobTime > 0 && (currentTick - lastHurtMobTime) < COMBAT_TIMEOUT;

        if (!recentlyHurt && !recentlyAttacked) return false;

        LivingEntity attacker = player.getLastHurtByMob();
        LivingEntity target = player.getLastHurtMob();

        boolean hasValidAttacker = attacker != null && attacker.isAlive() && attacker.distanceToSqr(player) < 900.0D;
        boolean hasValidTarget = target != null && target.isAlive() && target.distanceToSqr(player) < 900.0D;

        return hasValidAttacker || hasValidTarget;
    }

    @Override
    public void start() {
        this.hero.getNavigation().stop();

        // 保持神明浮空姿态
        this.hero.setFloating(true);
        this.hero.setNoGravity(true);
    }

    @Override
    public void tick() {
        if (this.targetPlayer == null || !this.targetPlayer.isAlive()) return;
        long currentTime = this.hero.level().getGameTime();
        boolean isCompanionOwner = isOwner(this.targetPlayer);

        // 1. 始终高冷注视
        this.hero.getLookControl().setLookAt(this.targetPlayer, 10.0F, this.hero.getMaxHeadXRot());

        // 2. 残血救援
        // 使用 10 分钟冷却常量
        if (isCompanionOwner && this.targetPlayer.getHealth() <= RESCUE_HEALTH_THRESHOLD && (currentTime - this.lastRescueTime) > RESCUE_COOLDOWN) {
            rescuePlayer(currentTime);
            return;
        }

        // 3. 【绝对实时的平滑排斥系统】
        double distanceSq = this.hero.distanceToSqr(this.targetPlayer);

        if (distanceSq < 144.0D) {
            // 距离小于 12 格：每 tick 都在疯狂计算后退！完全没有冷却！
            Vec3 dir = this.hero.position().subtract(this.targetPlayer.position());

            // 防止重合导致方向错乱
            if (dir.lengthSqr() < 0.001) {
                dir = new Vec3(this.hero.getRandom().nextDouble() - 0.5, 0, this.hero.getRandom().nextDouble() - 0.5);
            }
            dir = dir.normalize();

            // 永远试图退到反方向的 4 格外，高度保持在玩家头顶 2 格
            double targetX = this.hero.getX() + dir.x * 4.0D;
            double targetZ = this.hero.getZ() + dir.z * 4.0D;
            double targetY = this.targetPlayer.getY() + 2.0D;

            // 【动态速度核心】：你离他越近，他退得越快！(最高可达原速的 2 倍以上)
            double speed = 1.2D + ((144.0D - distanceSq) / 144.0D) * 1.5D;

            // 像神仙跟随一样，底层强制控制，无视任何地形障碍飘走
            this.hero.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed);

        } else if (distanceSq > 400.0D && isCompanionOwner) {
            // 距离大于 20 格：主动靠近维持观战视野
            double targetY = this.targetPlayer.getY() + 2.0D;
            this.hero.getMoveControl().setWantedPosition(this.targetPlayer.getX(), targetY, this.targetPlayer.getZ(), 1.5D);

        } else {
            // 安全距离 (12~20格)：平滑刹车
            // 【极其关键】：不再调用会导致罢工的 stop() 方法，直接让他摩擦力减速
            Vec3 currentMotion = this.hero.getDeltaMovement();
            this.hero.setDeltaMovement(currentMotion.x * 0.8, currentMotion.y * 0.8, currentMotion.z * 0.8);
        }
    }

    // 核心救援逻辑 (不变)
    private void rescuePlayer(long currentTime) {
        this.lastRescueTime = currentTime;

        LivingEntity attacker = this.targetPlayer.getLastHurtByMob();
        Vec3 dangerPos = attacker != null ? attacker.position() : this.targetPlayer.position();

        Vec3 safeDir = this.targetPlayer.position().subtract(dangerPos).normalize();
        if (safeDir.lengthSqr() == 0) {
            safeDir = new Vec3(this.hero.getRandom().nextDouble() - 0.5, 0, this.hero.getRandom().nextDouble() - 0.5).normalize();
        }

        double tpDistance = 24.0;
        double targetX = this.targetPlayer.getX() + safeDir.x * tpDistance;
        double targetZ = this.targetPlayer.getZ() + safeDir.z * tpDistance;

        BlockPos safePos = this.targetPlayer.level().getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int)targetX, (int)this.targetPlayer.getY(), (int)targetZ)
        );

        this.targetPlayer.teleportTo(safePos.getX() + 0.5, safePos.getY() + 0.1, safePos.getZ() + 0.5);
        this.targetPlayer.level().playSound(null, this.targetPlayer.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        this.hero.teleportTo(safePos.getX() + 1.5, safePos.getY() + 0.1, safePos.getZ() + 1.5);
        this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);

        this.targetPlayer.setLastHurtByMob(null);
        this.targetPlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        this.targetPlayer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));

        this.hero.getNavigation().stop();
    }
}
