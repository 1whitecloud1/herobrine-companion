package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

/** 同步动作的两端倒计时；雷电实体只能由服务端创建。 */
public final class HeroActionTiming {
    private HeroActionTiming() {
    }

    public static void tick(HeroEntity hero) {
        if (hero.scytheAnimTick > 0) hero.scytheAnimTick--;
        if (hero.debugAnimTick > 0) hero.debugAnimTick--;
        if (hero.shockTicks > 0) hero.shockTicks--;
        if (hero.thunderTicks > 0) {
            hero.thunderTicks--;
            if (hero.thunderTicks == 1 && hero.level() instanceof ServerLevel level) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(hero.getX(), hero.getY(), hero.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                hero.shockTicks = HeroEntity.MAX_SHOCK_TICKS;
            }
        }
    }
}
