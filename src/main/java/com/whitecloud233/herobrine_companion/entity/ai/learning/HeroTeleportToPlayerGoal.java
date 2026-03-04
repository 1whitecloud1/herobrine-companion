package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroTeleportToPlayerGoal extends Goal {
    private final HeroEntity hero;
    private Player targetPlayer;
    private int cooldown;

    // 凝视相关变量
    private boolean isStaring;
    private int stareTimer;

    // 战斗判定超时时间
    private static final int COMBAT_TIMEOUT = 100;

    public HeroTeleportToPlayerGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * 【重写】精准判断玩家是否处于战斗状态
     */
    private boolean isInCombat(Player player) {
        // 1. 放宽伤害时间戳判定
        // 只要近期 (5秒内) 造成或受到过伤害，就算战斗状态缓冲期。
        // 【核心修复】：移除了对“攻击者必须还活着”的死板检测，防止秒杀怪物后立刻判定脱战。
        int currentTick = player.tickCount;
        if (player.getLastHurtByMobTimestamp() > 0 && (currentTick - player.getLastHurtByMobTimestamp()) < COMBAT_TIMEOUT) return true;
        if (player.getLastHurtMobTimestamp() > 0 && (currentTick - player.getLastHurtMobTimestamp()) < COMBAT_TIMEOUT) return true;

        // 2. 增加“仇恨感知”（主动预判）
        // 不要等挨打了才算战斗！扫描周围 16 格，只要有怪物把仇恨目标(Target)锁定为你，神明就会立刻察觉并避让。
        net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().inflate(16.0D, 8.0D, 16.0D);
        java.util.List<net.minecraft.world.entity.Mob> threats = player.level().getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                searchBox,
                mob -> mob.getTarget() != null && mob.getTarget().getUUID().equals(player.getUUID())
        );

        return !threats.isEmpty();
    }

    @Override
    public boolean canUse() {
        // 如果正在骑乘 (比如在船上)，禁止传送，防止下车
        if (this.hero.isPassenger()) return false;

        // 如果正在交易，禁止传送
        if (this.hero.getTradingPlayer() != null) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        // 优先跟随主人
        if (this.hero.getOwnerUUID() != null) {
            this.targetPlayer = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
        }

        // 如果没有主人，或者主人不在附近，才找最近的玩家
        if (this.targetPlayer == null) {
            this.targetPlayer = this.hero.level().getNearestPlayer(this.hero, 64.0D);
        }

        if (this.targetPlayer == null) return false;

        double distSqr = this.hero.distanceToSqr(this.targetPlayer);
        boolean playerInCombat = isInCombat(this.targetPlayer);

        // [深度学习] Spooky Teleport (惊吓传送) 逻辑
        // 只有在 观察者 (OBSERVER) 或 恶作剧者 (PRANKSTER) 状态下才触发
        // 【关键修复】如果玩家正在战斗中，绝对不触发惊吓传送，以免干扰玩家战斗
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        boolean canSpookyTeleport = state == SimpleNeuralNetwork.MindState.OBSERVER || state == SimpleNeuralNetwork.MindState.PRANKSTER;

        if (canSpookyTeleport && !playerInCombat) {
            int chance = (state == SimpleNeuralNetwork.MindState.PRANKSTER) ? 100 : 500;

            if (this.hero.tickCount > 60 &&
                    this.hero.level().getBrightness(LightLayer.BLOCK, this.targetPlayer.blockPosition()) < 7 &&
                    distSqr > 8.0D * 8.0D && distSqr < 40.0D * 40.0D &&
                    this.hero.getRandom().nextInt(chance) == 0) {
                return true;
            }
        }

        // 正常的跟随传送逻辑 (必须是陪伴模式)
        if (this.hero.isCompanionMode()) {
            if (playerInCombat) {
                // 【关键修复】战斗中放宽传送距离：
                // 只要你没跑出 40 格外(1600.0D)，他就会靠腿走路和避让，而不是一直瞬移到你身边
                return distSqr > 1600.0D;
            } else {
                // 非战斗状态下，拉开 25 格(625.0D)就正常传送
                return distSqr > 625.0D;
            }
        }

        return false;
    }

    @Override
    public void start() {
        this.isStaring = false;
        this.stareTimer = 0;

        if (this.targetPlayer != null) {
            RandomSource random = this.hero.getRandom();

            double distSqr = this.hero.distanceToSqr(this.targetPlayer);
            boolean isSpookyTeleport = !this.hero.isCompanionMode() || distSqr < 25.0D * 25.0D;

            // Spooky 模式下，距离稍微近一点 (3-6格)
            double distance = isSpookyTeleport ? 3.0 + random.nextDouble() * 3.0 : 2.0 + random.nextDouble() * 2.0;

            float angleOffset;
            float baseAngle = this.targetPlayer.getYRot();

            if (isSpookyTeleport) {
                // 降低背后出现的概率，增加侧面出现的概率
                float roll = random.nextFloat();
                if (roll < 0.2F) {
                    // 前方 +/- 30 度
                    angleOffset = (random.nextFloat() - 0.5F) * 60.0F;
                } else if (roll < 0.6F) {
                    // 侧面 +/- 90 度 (左右各半)
                    angleOffset = (random.nextBoolean() ? 90.0F : -90.0F) + (random.nextFloat() - 0.5F) * 30.0F;
                } else {
                    // 身后 +/- 60 度
                    angleOffset = (random.nextFloat() - 0.5F) * 120.0F + 180.0F;
                }
            } else {
                // 跟随模式：只在身后
                angleOffset = 180.0F;
            }

            Vec3 offsetVec = Vec3.directionFromRotation(0, baseAngle + angleOffset).scale(distance);

            double x = this.targetPlayer.getX() + offsetVec.x;
            double z = this.targetPlayer.getZ() + offsetVec.z;
            double y = this.targetPlayer.getY();

            // 简单的防卡墙检查
            BlockPos targetPos = new BlockPos((int)x, (int)y, (int)z);
            if (!hero.level().isEmptyBlock(targetPos) || !hero.level().isEmptyBlock(targetPos.above())) {
                if (hero.level().isEmptyBlock(targetPos.above()) && hero.level().isEmptyBlock(targetPos.above(2))) {
                    y += 1.0;
                } else {
                    if (!isSpookyTeleport) {
                        x = this.targetPlayer.getX();
                        z = this.targetPlayer.getZ();
                        y = this.targetPlayer.getY();
                    } else {
                        return;
                    }
                }
            }

            this.hero.teleportTo(x, y, z);
            this.hero.lookAt(this.targetPlayer, 180.0F, 180.0F);
            this.hero.getNavigation().stop();
            this.hero.setDeltaMovement(0, 0, 0);

            if (isSpookyTeleport) {
                this.isStaring = true;
                this.stareTimer = 60 + random.nextInt(40); // 3-5秒
                this.hero.setFloating(false);
                this.hero.setNoGravity(false);

                // 播放音效，增加惊吓感
                this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
            } else {
                this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (this.hero.getTradingPlayer() != null) return false;
        return this.isStaring && this.stareTimer > 0 && this.targetPlayer != null && this.targetPlayer.isAlive();
    }

    @Override
    public void tick() {
        if (this.isStaring) {
            this.stareTimer--;
            this.hero.getNavigation().stop();
            this.hero.setDeltaMovement(0, 0, 0);
            if (this.targetPlayer != null) {
                this.hero.getLookControl().setLookAt(this.targetPlayer, 30.0F, 30.0F);
            }
        }
    }

    @Override
    public void stop() {
        this.isStaring = false;
        // 增加冷却时间，从 40 ticks (2s) 增加到 200 ticks (10s)
        this.cooldown = 200;
    }
}