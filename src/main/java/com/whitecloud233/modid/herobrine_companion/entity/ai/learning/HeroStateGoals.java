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
        return hero.getTradingPlayer() == null;
    }

    @Override
    public void tick() {
        if (hero.tickCount % 10 != 0) return;
        HeroMindStateRegistry.tickServer(hero);
    }
}
