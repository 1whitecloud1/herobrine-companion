package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class HeroDataHandler {

    /**
     * 从全局存档同步信任度到实体 (Read)
     * [修改] 现在只同步 Owner 的数据到实体，用于显示和交互
     */
    public static void syncGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID == null) return;

            HeroWorldData data = HeroWorldData.get(serverLevel);
            int trust = data.getTrust(ownerUUID);
            
            // 同步到实体
            hero.setTrustLevel(trust);
            
            // 同步奖励状态
            int[] rewards = data.getClaimedRewards(ownerUUID);
            hero.setClaimedRewards(rewards);
        }
    }

    /**
     * 将实体信任度更新到全局存档 (Write)
     * [修改] 将实体的当前信任度写回 Owner 的 Profile
     */
    public static void updateGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID == null) return;

            HeroWorldData data = HeroWorldData.get(serverLevel);
            int current = hero.getTrustLevel();
            
            // 更新 Owner 的信任度
            if (current != data.getTrust(ownerUUID)) {
                data.setTrust(ownerUUID, current);
            }
            
            // 更新奖励状态
            int[] rewards = hero.getClaimedRewards();
            for (int reward : rewards) {
                if (!data.hasClaimedReward(ownerUUID, reward)) {
                    data.claimReward(ownerUUID, reward);
                }
            }
        }
    }

    /**
     * 尝试从附近的玩家身上恢复信任度 (Legacy / Backup)
     * [Deprecated] 现在使用 HeroWorldData，此方法仅用于兼容旧存档迁移
     */
    public static void restoreTrustFromPlayer(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID == null) return;

            HeroWorldData data = HeroWorldData.get(serverLevel);
            int trust = data.getTrust(ownerUUID);
            
            // 同步到实体
            hero.setTrustLevel(trust);
            
            // 同步奖励状态
            int[] rewards = data.getClaimedRewards(ownerUUID);
            hero.setClaimedRewards(rewards);
        }
    }
}