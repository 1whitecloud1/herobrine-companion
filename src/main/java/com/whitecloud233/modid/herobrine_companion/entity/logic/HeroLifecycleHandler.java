package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class HeroLifecycleHandler {

    public static void checkUniqueness(HeroEntity hero) {
        if (hero.level().isClientSide) return;

        boolean amISafe = hero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);
        ServerLevel serverLevel = (ServerLevel) hero.level();

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeroEntity other && entity.getId() != hero.getId() && other.isAlive()) {

                if (amISafe) {
                    other.remove(Entity.RemovalReason.DISCARDED);
                }
                else if (!other.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE)) {
                    if (hero.tickCount < other.tickCount) {
                        hero.remove(Entity.RemovalReason.DISCARDED);
                        return;
                    }
                }
                else {
                    hero.remove(Entity.RemovalReason.DISCARDED);
                    return;
                }
            }
        }

        if (amISafe) {
            hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
        }
    }
}
