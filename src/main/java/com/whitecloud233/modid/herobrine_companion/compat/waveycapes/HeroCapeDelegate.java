package com.whitecloud233.modid.herobrine_companion.compat.waveycapes;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer;

final class HeroCapeDelegate implements MinecraftPlayer {

    private final HeroEntity hero;
    private final HeroCapeState state;

    HeroCapeDelegate(HeroEntity hero, HeroCapeState state) {
        this.hero = hero;
        this.state = state;
    }

    @Override
    public boolean isVisuallySwimming() {
        return hero.isVisuallySwimming();
    }

    @Override
    public float getXRot() {
        return hero.getXRot();
    }

    @Override
    public boolean isCrouching() {
        return hero.isCrouching();
    }

    @Override
    public double getY() {
        return hero.getY();
    }

    @Override
    public float getYRot() {
        return hero.getYRot();
    }

    @Override
    public double getZ() {
        return hero.getZ();
    }

    @Override
    public double getX() {
        return hero.getX();
    }

    @Override
    public boolean isUnderWater() {
        return hero.isUnderWater();
    }

    @Override
    public double getXCloak() {
        return state.getXCloak(1.0F);
    }

    @Override
    public double getZCloak() {
        return state.getZCloak(1.0F);
    }

    @Override
    public float getYBodyRotO() {
        return hero.yBodyRotO;
    }

    @Override
    public float getYBodyRot() {
        return hero.yBodyRot;
    }

    @Override
    public double getYo() {
        return hero.yo;
    }

    @Override
    public double getXo() {
        return hero.xo;
    }

    @Override
    public double getZo() {
        return hero.zo;
    }
}
