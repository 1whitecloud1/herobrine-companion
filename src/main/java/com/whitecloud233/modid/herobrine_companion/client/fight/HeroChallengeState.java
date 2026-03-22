package com.whitecloud233.modid.herobrine_companion.client.fight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.ai.control.MoveControl;

public class HeroChallengeState {

    // 在 HeroEntity 的 tick 中调用，实时守护状态
    public static void tick(HeroEntity hero) {
        // 1. 状态自愈：如果硬盘记录在挑战中，但内存丢失了，立刻补回
        if (hero.getPersistentData().getBoolean("IsChallengeActive")) {
            if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
                int savedTicks = hero.getPersistentData().getInt("ChallengePhaseTicks");
                hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, savedTicks);
            }
        }

        // 如果不在挑战状态，直接退出
        if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) return;

        // 2. 物理状态强制锁定（断点续传的核心）
        hero.setNoGravity(true);
        hero.setFloating(true);
        hero.setDeltaMovement(0, hero.getDeltaMovement().y, 0); // 锁死水平移动，允许垂直浮动

        // 3. 强制劫持移动控制器
        if (!(hero.getMoveControl() instanceof ChallengeMoveControl)) {
            hero.moveControl = new ChallengeMoveControl(hero);
        }
    }

    // 在 HeroEntity 读取 NBT 时调用，确保落地第一时间恢复姿态
    public static void onRestoreFromDisk(HeroEntity hero) {
        if (hero.getPersistentData().getBoolean("IsChallengeActive")) {
            hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
            int savedTicks = hero.getPersistentData().getInt("ChallengePhaseTicks");
            hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, savedTicks);

            hero.setNoGravity(true);
            hero.setFloating(true);

            // ==========================================
            // 【核心修复】：AI 断点续传拦截
            // 实体在构造函数阶段由于还没读取 NBT，误加载了日常 AI。
            // 此时必须强制清空，并重新装填战斗 AI！
            // ==========================================
            hero.goalSelector.removeAllGoals(goal -> true);
            hero.targetSelector.removeAllGoals(goal -> true);
            hero.setTarget(null);
            hero.getNavigation().stop();

            // 强制接管移动控制权，防止掉落
            hero.moveControl = new ChallengeMoveControl(hero);

            // 重新注入雷电 Boss 攻击 AI
            hero.goalSelector.addGoal(1, new com.whitecloud233.modid.herobrine_companion.client.fight.goal.HeroPhase1Goal(hero));
        }
    }

    // 将其改为 public static，以便正确通过 instanceof 检查和分配
    public static class ChallengeMoveControl extends MoveControl {
        public ChallengeMoveControl(HeroEntity hero) {
            super(hero);
        }
        @Override
        public void tick() {
            // 什么都不做，彻底锁死原版寻路系统的移动意图
        }
    }
}