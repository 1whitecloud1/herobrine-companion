package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroExtinguishTorchGoal extends Goal {
    private final HeroEntity hero;
    private BlockPos targetTorch;
    private int cooldown;

    public HeroExtinguishTorchGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.hero.getTarget() != null) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        if (this.hero.level().isDay() && !this.hero.level().isRaining()) return false;

        // [深度学习] 根据心智状态调整概率
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        int chance = 60; // 默认 1/60

        if (state == SimpleNeuralNetwork.MindState.PRANKSTER) {
            chance = 10; // 恶作剧者：非常频繁 (1/10)
        } else if (state == SimpleNeuralNetwork.MindState.OBSERVER) {
            chance = 100; // 观察者：偶尔 (1/100)
        } else {
            return false; // 其他状态（如守护者、审判者）不屑于做这种小动作
        }

        if (this.hero.getRandom().nextInt(chance) != 0) return false;

        this.targetTorch = findNearbyTorch();
        return this.targetTorch != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetTorch != null && this.hero.distanceToSqr(Vec3.atCenterOf(this.targetTorch)) < 256.0D;
    }

    @Override
    public void start() {
        this.hero.getNavigation().moveTo(this.targetTorch.getX(), this.targetTorch.getY(), this.targetTorch.getZ(), 1.0D);
    }

    @Override
    public void tick() {
        if (this.targetTorch == null) return;

        this.hero.getLookControl().setLookAt(Vec3.atCenterOf(this.targetTorch));

        if (this.hero.distanceToSqr(Vec3.atCenterOf(this.targetTorch)) < 4.0D) {
            BlockState state = this.hero.level().getBlockState(this.targetTorch);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                this.hero.level().destroyBlock(this.targetTorch, true);
                this.hero.level().playSound(null, this.targetTorch, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (this.hero.level().random.nextFloat() - this.hero.level().random.nextFloat()) * 0.8F);
                this.hero.level().addParticle(ParticleTypes.LARGE_SMOKE, this.targetTorch.getX() + 0.5, this.targetTorch.getY() + 0.5, this.targetTorch.getZ() + 0.5, 0.0, 0.0, 0.0);

                Player player = null;
                if (this.hero.getOwnerUUID() != null) {
                    player = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
                }
                if (player == null) {
                    player = this.hero.level().getNearestPlayer(this.hero, 16.0D);
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    HeroDialogueHandler.onExtinguishTorch(this.hero, serverPlayer);
                    // [深度学习] 恶作剧成功，反馈给大脑
                    // 如果玩家没有攻击他，这会被视为正向反馈（好玩）
                    // 具体的反馈逻辑在 HeroBrain 或 HeroObserver 中处理，这里只做行为执行
                }
            }
            this.targetTorch = null;
        }
    }

    @Override
    public void stop() {
        this.targetTorch = null;
        this.cooldown = 600;
    }

    private BlockPos findNearbyTorch() {
        BlockPos heroPos = this.hero.blockPosition();
        int range = 10;

        for (int i = 0; i < 20; i++) {
            int x = heroPos.getX() + this.hero.getRandom().nextInt(range * 2) - range;
            int y = heroPos.getY() + this.hero.getRandom().nextInt(range) - range / 2;
            int z = heroPos.getZ() + this.hero.getRandom().nextInt(range * 2) - range;

            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = this.hero.level().getBlockState(pos);

            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                return pos;
            }
        }
        return null;
    }
}
