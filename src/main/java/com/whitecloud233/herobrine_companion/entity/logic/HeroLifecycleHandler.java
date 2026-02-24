package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public class HeroLifecycleHandler {

    public static void checkUniqueness(HeroEntity hero) {
        if (hero.level().isClientSide) return;

        ServerLevel currentLevel = (ServerLevel) hero.level();
        MinecraftServer server = currentLevel.getServer();
        if (server == null) return;

        UUID myOwner = hero.getOwnerUUID();
        boolean iAmSafe = hero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);

        // 遍历所有维度
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                // 跳过自己
                if (entity.getId() == hero.getId()) continue;

                if (entity instanceof HeroEntity other && other.isAlive()) {
                    // 检查是否属于同一个主人 (或者都是无主的)
                    UUID otherOwner = other.getOwnerUUID();
                    boolean sameOwner = (myOwner == null && otherOwner == null) || (myOwner != null && myOwner.equals(otherOwner));

                    if (sameOwner) {
                        // --- 冲突解决策略 ---

                        // 1. 优先保留带有 "TAG_RESPAWNED_SAFE" 的 (特权标记)
                        boolean otherIsSafe = other.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE);

                        if (iAmSafe && !otherIsSafe) {
                            other.discard();
                            continue; // 杀敌后继续检查其他敌人
                        } else if (!iAmSafe && otherIsSafe) {
                            hero.discard();
                            return; // 我死了，结束检查
                        }

                        // 2. 优先保留与 Owner 同维度的 (跟随逻辑)
                        if (myOwner != null) {
                            ServerPlayer owner = server.getPlayerList().getPlayer(myOwner);
                            if (owner != null) {
                                boolean iAmWithPlayer = hero.level() == owner.level();
                                boolean otherIsWithPlayer = other.level() == owner.level();

                                if (iAmWithPlayer && !otherIsWithPlayer) {
                                    other.discard();
                                    continue;
                                } else if (!iAmWithPlayer && otherIsWithPlayer) {
                                    hero.discard();
                                    return;
                                }
                            }
                        }

                        // 3. 如果条件都一样，保留 tickCount 小的 (假设是新生成的)
                        // 注意：tickCount 小意味着存活时间短。通常新生成的是为了替换旧的。
                        if (hero.tickCount < other.tickCount) {
                            other.discard();
                        } else {
                            hero.discard();
                            return;
                        }
                    }
                }
            }
        }

        // 检查完毕，如果我还活着，移除特权标签
        if (iAmSafe) {
            hero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
        }
    }
}
