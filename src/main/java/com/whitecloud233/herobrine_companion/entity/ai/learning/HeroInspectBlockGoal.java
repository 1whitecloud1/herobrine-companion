package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroInspectBlockGoal extends Goal {
    private final HeroEntity hero;
    private BlockPos targetPos;
    private int timer;
    private int cooldown;

    public HeroInspectBlockGoal(HeroEntity hero) {
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

        // [深度学习] 根据心智状态调整频率
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        int chance = 50; // 默认 1/50

        // 1. 拒绝工作的状态
        if (state == SimpleNeuralNetwork.MindState.REMINISCING || 
            state == SimpleNeuralNetwork.MindState.JUDGE || 
            state == SimpleNeuralNetwork.MindState.MONSTER_KING) {
            return false;
        }

        // 2. 狂热工作的状态
        if (state == SimpleNeuralNetwork.MindState.GLITCH_LORD) {
            chance = 5; // 代码之神：极度频繁 (1/5)
        } else if (state == SimpleNeuralNetwork.MindState.MAINTAINER) {
            chance = 10; // 维护者：频繁 (1/10)
        } else if (state == SimpleNeuralNetwork.MindState.PROTECTOR) {
            chance = 30; // 守护者：稍微频繁一点 (1/30)
        }

        if (this.hero.getRandom().nextInt(chance) != 0) return false;

        this.targetPos = findInterestingBlock();
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetPos != null && this.timer < 100 && this.hero.distanceToSqr(Vec3.atCenterOf(this.targetPos)) < 256.0D;
    }

    @Override
    public void start() {
        this.timer = 0;
        // 飞向目标，但保持一点距离
        Vec3 targetVec = Vec3.atCenterOf(this.targetPos);
        Vec3 offset = this.hero.position().subtract(targetVec).normalize().scale(2.0);

        this.hero.getNavigation().moveTo(targetVec.x + offset.x, targetVec.y + 1, targetVec.z + offset.z, 1.0D);
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;

        // [修复] 限制头部转动速度，避免快速摇摆
        // 使用 lookAt 的平滑版本，或者降低更新频率
        // 原来的 this.hero.getLookControl().setLookAt(...) 会每 tick 强制更新，可能导致与身体转向冲突
        
        // 只有当距离较远时才频繁看，近距离时偶尔看
        if (this.timer % 5 == 0) {
             this.hero.getLookControl().setLookAt(Vec3.atCenterOf(this.targetPos));
        }

        this.timer++;

        // 到达附近后开始“分析”
        if (this.hero.distanceToSqr(Vec3.atCenterOf(this.targetPos)) < 16.0D) {
            this.hero.getNavigation().stop();

            // 粒子效果：仿佛在扫描代码
            if (this.timer % 5 == 0) {
                double x = this.targetPos.getX() + 0.5 + (this.hero.getRandom().nextDouble() - 0.5);
                double y = this.targetPos.getY() + 0.5 + (this.hero.getRandom().nextDouble() - 0.5);
                double z = this.targetPos.getZ() + 0.5 + (this.hero.getRandom().nextDouble() - 0.5);
                
                // [深度学习] 根据状态改变粒子效果
                SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
                if (state == SimpleNeuralNetwork.MindState.GLITCH_LORD) {
                    // 代码之神：使用反转传送门粒子，更有“故障”感
                    this.hero.level().addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0, 0, 0);
                } else {
                    // 默认：附魔符文
                    this.hero.level().addParticle(ParticleTypes.ENCHANT, x, y, z, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public void stop() {
        // 结束时偶尔评价一句
        if (this.targetPos != null && this.hero.getRandom().nextInt(3) == 0) {
            Player player = null;
            if (this.hero.getOwnerUUID() != null) {
                player = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
            }
            if (player == null) {
                player = this.hero.level().getNearestPlayer(this.hero, 16.0D);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                HeroDialogueHandler.onInspectBlock(this.hero, serverPlayer, this.hero.level().getBlockState(this.targetPos));
                
                // [深度学习] 学习反馈
                // 如果玩家在附近，并且 Herobrine 发现了好东西（如钻石），这会增加玩家的“探索值”
                // 因为 Herobrine 认为玩家带他来到了一个好地方
                BlockState state = this.hero.level().getBlockState(this.targetPos);
                if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.ANCIENT_DEBRIS)) {
                    // [修复] 传入 playerUUID
                    this.hero.getHeroBrain().inputExploration(serverPlayer.getUUID(), 0.05f);
                }
            }
        }

        this.targetPos = null;
        
        // [深度学习] 根据状态调整冷却时间
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (state == SimpleNeuralNetwork.MindState.GLITCH_LORD) {
            this.cooldown = 100; // 代码之神：几乎没有冷却 (5秒)
        } else if (state == SimpleNeuralNetwork.MindState.MAINTAINER) {
            this.cooldown = 300; // 维护者：较短冷却 (15秒)
        } else {
            this.cooldown = 600 + this.hero.getRandom().nextInt(600); // 默认：30-60秒
        }
    }


    private BlockPos findInterestingBlock() {
        BlockPos heroPos = this.hero.blockPosition();
        int range = 16;

        for (int i = 0; i < 20; i++) { // 随机尝试20次，避免遍历整个区域造成卡顿
            int x = heroPos.getX() + this.hero.getRandom().nextInt(range * 2) - range;
            int y = heroPos.getY() + this.hero.getRandom().nextInt(range) - range / 2;
            int z = heroPos.getZ() + this.hero.getRandom().nextInt(range * 2) - range;

            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = this.hero.level().getBlockState(pos);

            if (isInteresting(state)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isInteresting(BlockState state) {
        // [深度学习] 代码之神对更多方块感兴趣
        SimpleNeuralNetwork.MindState mindState = this.hero.getHeroBrain().getState();
        boolean isGlitchLord = mindState == SimpleNeuralNetwork.MindState.GLITCH_LORD;

        if (state.is(Blocks.SPAWNER)) return true;
        if (state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)) return true;
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return true;
        if (state.is(Blocks.BEACON)) return true;
        if (state.is(Blocks.CONDUIT)) return true;
        if (state.is(Blocks.ENCHANTING_TABLE)) return true;
        
        // 也可以检查一些红石元件
        if (state.is(Blocks.OBSERVER) || state.is(Blocks.COMPARATOR)) return true;

        // [新增] 代码之神对基岩和屏障也感兴趣
        if (isGlitchLord) {
            if (state.is(Blocks.BEDROCK)) return true;
            if (state.is(Blocks.BARRIER)) return true;
            if (state.is(Blocks.STRUCTURE_BLOCK)) return true;
            if (state.is(Blocks.JIGSAW)) return true;
        }

        return false;
    }
}
