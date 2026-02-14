package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class HeroLifecycleHandler {

    public static void checkUniqueness(HeroEntity hero) {
        if (hero.level().isClientSide) return;

        // 1. 检查自己是否是"特权替身"
        boolean amISafe = hero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);
        ServerLevel serverLevel = (ServerLevel) hero.level();

        // 遍历所有实体寻找同类
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof HeroEntity other && entity.getId() != hero.getId() && other.isAlive()) {

                // 情况 A: 我是特权替身 (我是新的)
                if (amISafe) {
                    // 我拥有最高优先级，清理掉所有旧的/其他的 Hero
                    other.discard();
                }
                // 情况 B: 我是普通 Hero，对方也是普通 Hero
                else if (!other.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE)) {
                    // 冲突了！保留存在时间更长的那个，或者保留ID更小的那个
                    // 这里我们采取简单的策略：后生成的自杀 (tickCount 小的通常是后生成的)
                    if (hero.tickCount < other.tickCount) {
                        hero.discard();
                        return; // 我死了，不用继续检查了
                    }
                }
                // 情况 C: 对方是特权替身
                else {
                    // 对方有特权，我必须死
                    hero.discard();
                    return;
                }
            }
        }

        // 2. 如果我是特权替身，并且我已经完成了清理工作 (活过了这一帧)
        // 移除特权标签，变回普通实体，防止未来出 Bug
        if (amISafe) {
            hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
        }
    }
}
