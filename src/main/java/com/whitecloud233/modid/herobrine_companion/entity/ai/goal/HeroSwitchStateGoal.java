package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;

public class HeroSwitchStateGoal extends Goal {
    private final HeroEntity hero;
    private int cooldown;

    public HeroSwitchStateGoal(HeroEntity hero) {
        this.hero = hero;
    }

    @Override
    public boolean canUse() {
        if (this.hero.isCompanionMode()) return false;

        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        return hero.getTarget() == null; 
    }
    
    @Override
    public boolean canContinueToUse() {
        return !this.hero.isCompanionMode() && super.canContinueToUse();
    }
    
    @Override
    public void start() {
        this.cooldown = 200 + hero.getRandom().nextInt(400);
        boolean current = hero.isFloating();
        hero.setFloating(!current);
        hero.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.5f);
    }
}