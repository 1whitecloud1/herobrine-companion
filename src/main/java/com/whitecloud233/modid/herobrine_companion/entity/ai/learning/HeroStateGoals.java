package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state.HeroMindStateRegistry;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class HeroStateGoals extends Goal {

    private final HeroEntity hero;

    public HeroStateGoals(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return hero.getTradingPlayer() == null && !hero.isCompanionMode();
    }

    @Override
    public boolean canContinueToUse() {
        return hero.getTradingPlayer() == null && !hero.isCompanionMode();
    }

    @Override
    public void tick() {
        if (hero.tickCount % 10 != 0) {
            // 状态 tick 间隙：每 tick 续瞄最近记录的注视目标，消除"2 tick 快甩 + 8 tick 慢回"的锯齿摆动
            HeroMindStateRegistry.tickServerLook(hero);
            return;
        }
        HeroMindStateRegistry.tickServer(hero);
        // 状态 tick 帧同样续瞄一次，保证 LookControl 的 2-tick 冷却无缝衔接
        HeroMindStateRegistry.tickServerLook(hero);
    }
}
