package com.whitecloud233.herobrine_companion.compat.waveycapes;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;

final class HeroCapeDelegate {

    private final HeroEntity hero;
    private final HeroCapeState state;

    HeroCapeDelegate(HeroEntity hero, HeroCapeState state) {
        this.hero = hero;
        this.state = state;
    }

    boolean isVisuallySwimming() {
        return hero.isVisuallySwimming();
    }

    float getXRot() {
        return hero.getXRot();
    }

    boolean isCrouching() {
        return hero.isCrouching();
    }

    double getY() {
        return hero.getY();
    }

    float getYRot() {
        return hero.getYRot();
    }

    double getZ() {
        return hero.getZ();
    }

    double getX() {
        return hero.getX();
    }

    boolean isUnderWater() {
        return hero.isUnderWater();
    }

    double getXCloak() {
        return state.getXCloak(1.0F);
    }

    double getZCloak() {
        return state.getZCloak(1.0F);
    }

    float getYBodyRotO() {
        return hero.yBodyRotO;
    }

    float getYBodyRot() {
        return hero.yBodyRot;
    }

    double getYo() {
        return hero.yo;
    }

    double getXo() {
        return hero.xo;
    }

    double getZo() {
        return hero.zo;
    }
}
