package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.goal.HeroGodlyCompanionGoal;
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
        if (HeroGodlyCompanionGoal.isOwnerWithinStayStillRadius(this.hero)) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        if (this.hero.level().isDay() && !this.hero.level().isRaining()) return false;

        // [深度学习] 根据心智状态调整概率
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (state != SimpleNeuralNetwork.MindState.PRANKSTER) return false;

        if (this.hero.getRandom().nextInt(10) != 0) return false;

        this.targetTorch = findNearbyTorch();
        return this.targetTorch != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (HeroGodlyCompanionGoal.isOwnerWithinStayStillRadius(this.hero)) return false;
        return this.targetTorch != null && this.hero.distanceToSqr(Vec3.atCenterOf(this.targetTorch)) < 256.0D;
    }

    @Override
    public void start() {
        this.hero.getNavigation().moveTo(this.targetTorch.getX(), this.targetTorch.getY(), this.targetTorch.getZ(), 1.0D);
    }

    @Override
    public void tick() {
        if (this.targetTorch == null) return;
        Vec3 targetCenter = Vec3.atCenterOf(this.targetTorch);
        double distToTargetSqr = this.hero.distanceToSqr(targetCenter);

        if (distToTargetSqr < 16.0D || this.hero.getNavigation().isDone()) {
            this.hero.getLookControl().setLookAt(targetCenter);
        }

        if (distToTargetSqr < 4.0D) {
            BlockState state = this.hero.level().getBlockState(this.targetTorch);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                this.hero.level().destroyBlock(this.targetTorch, true);
                this.hero.level().playSound(
                        null,
                        this.targetTorch,
                        SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS,
                        0.5F,
                        2.6F + (this.hero.level().random.nextFloat() - this.hero.level().random.nextFloat()) * 0.8F
                );
                this.hero.level().addParticle(
                        ParticleTypes.LARGE_SMOKE,
                        this.targetTorch.getX() + 0.5D,
                        this.targetTorch.getY() + 0.5D,
                        this.targetTorch.getZ() + 0.5D,
                        0.0D,
                        0.0D,
                        0.0D
                );

                if (this.hero.getOwnerUUID() != null) {
                    Player player = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
                    if (player instanceof ServerPlayer serverPlayer) {
                        HeroDialogueHandler.onExtinguishTorch(this.hero, serverPlayer);
                    }
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
