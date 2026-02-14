package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.effect.MobEffectInstance;

public class HeroOtherProtection {

    public static boolean canBeAffected(HeroEntity hero, MobEffectInstance instance) {
        return false;
    }

    public static boolean canBeLeashed(HeroEntity hero) {
        return false;
    }

    // [新增] 检查并移除火焰
    public static void tick(HeroEntity hero) {
        if (hero.isOnFire()) {
            hero.clearFire();
        }
    }
}
