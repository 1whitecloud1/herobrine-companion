package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
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
                            // [新增] 在杀死对方前，尝试从对方同步数据（特别是皮肤）
                            syncDataBeforeDiscard(other, hero);
                            other.discard();
                            continue; // 杀敌后继续检查其他敌人
                        } else if (!iAmSafe && otherIsSafe) {
                            // [新增] 在自杀前，尝试将数据同步给对方
                            syncDataBeforeDiscard(hero, other);
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
                                    syncDataBeforeDiscard(other, hero);
                                    other.discard();
                                    continue;
                                } else if (!iAmWithPlayer && otherIsWithPlayer) {
                                    syncDataBeforeDiscard(hero, other);
                                    hero.discard();
                                    return;
                                }
                            }
                        }

                        // 3. 如果条件都一样，保留 tickCount 小的 (假设是新生成的)
                        // 注意：tickCount 小意味着存活时间短。通常新生成的是为了替换旧的。
                        if (hero.tickCount < other.tickCount) {
                            syncDataBeforeDiscard(other, hero);
                            other.discard();
                        } else {
                            syncDataBeforeDiscard(hero, other);
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

    // [新增] 在丢弃实体前同步关键数据
    private static void syncDataBeforeDiscard(HeroEntity source, HeroEntity target) {
        // 如果源实体有自定义皮肤，且目标实体没有，则同步过去
        if (source.getSkinVariant() == HeroEntity.SKIN_CUSTOM && target.getSkinVariant() != HeroEntity.SKIN_CUSTOM) {
            target.setSkinVariant(HeroEntity.SKIN_CUSTOM);
            target.setCustomSkinName(source.getCustomSkinName());
        }

        // 如果源实体有更高的信任度，同步过去 (虽然通常应该以 WorldData 为准，但作为保险)
        if (source.getTrustLevel() > target.getTrustLevel()) {
            target.setTrustLevel(source.getTrustLevel());
        }

        // ============== [修复] 转移原生装备 ==============
        target.loadEquipmentFromTag(source.getArmorItemsTag(), source.getHandItemsTag());
        target.setCuriosBackItemFromTag(source.getCuriosBackItemTag()); // [新增]
        // ==================================================

        // 强制更新一次全局数据，确保数据不丢失
        if (target.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            if (target.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                data.setSkinVariant(HeroEntity.SKIN_CUSTOM);
                data.setCustomSkinName(target.getCustomSkinName());
            }

            // ============== [修复] 更新全局原生装备 ==============
            if (target.getOwnerUUID() != null) {
                data.setEquipment(target.getOwnerUUID(), target.getArmorItemsTag(), target.getHandItemsTag());
                data.setCuriosBackItem(target.getOwnerUUID(), target.getCuriosBackItemTag()); // [新增]
            }
            // ===============================================
        }
    }
}