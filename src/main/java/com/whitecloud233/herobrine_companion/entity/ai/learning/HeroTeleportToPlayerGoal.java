package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroTeleportToPlayerGoal extends Goal {
    private final HeroEntity hero;
    private Player targetPlayer;
    private int cooldown;

    // [新增] 凝视相关变量
    private boolean isStaring;
    private int stareTimer;

    public HeroTeleportToPlayerGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // [新增] 如果正在骑乘 (比如在船上)，禁止传送，防止下车
        if (this.hero.isPassenger()) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        // 优先跟随主人
        if (this.hero.getOwnerUUID() != null) {
            this.targetPlayer = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
        }

        // 如果没有主人，或者主人不在附近，才找最近的玩家 (可选，或者直接返回 false)
        if (this.targetPlayer == null) {
            this.targetPlayer = this.hero.level().getNearestPlayer(this.hero, 64.0D);
        }

        if (this.targetPlayer == null) return false;

        double distSqr = this.hero.distanceToSqr(this.targetPlayer);

        // [深度学习] Spooky Teleport (惊吓传送) 逻辑
        // 只有在 观察者 (OBSERVER) 或 恶作剧者 (PRANKSTER) 状态下才触发
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        boolean canSpookyTeleport = state == SimpleNeuralNetwork.MindState.OBSERVER || state == SimpleNeuralNetwork.MindState.PRANKSTER;

        if (canSpookyTeleport) {
            // 恶作剧者状态下，概率翻倍
            int chance = (state == SimpleNeuralNetwork.MindState.PRANKSTER) ? 100 : 500;

            if (this.hero.tickCount > 60 &&
                    this.hero.level().getBrightness(LightLayer.BLOCK, this.targetPlayer.blockPosition()) < 7 &&
                    distSqr > 8.0D * 8.0D && distSqr < 40.0D * 40.0D &&
                    this.hero.getRandom().nextInt(chance) == 0) {
                return true;
            }
        }

        // [修改] 正常的跟随传送逻辑 (必须是陪伴模式)
        if (this.hero.isCompanionMode()) {
            // 触发距离：如果距离太远 (> 25格)，或者玩家传送走了 (距离突然变得非常远)，就触发传送
            return distSqr > 25.0D * 25.0D; // 超过 25 格就传送
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

            // [修改] 位置计算逻辑
            float angleOffset;
            float baseAngle = this.targetPlayer.getYRot();

            if (isSpookyTeleport) {
                // [修改] 降低背后出现的概率，增加侧面出现的概率
                // 20% 前方 (Jumpscare)
                // 40% 侧面 (+/- 90度)
                // 40% 背后 (+/- 180度)
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

                // [新增] 播放音效，增加惊吓感 (音调调低一点，更恐怖)
                this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.5F);
            } else {
                this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
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
        this.cooldown = 40; // 2秒冷却
    }
}