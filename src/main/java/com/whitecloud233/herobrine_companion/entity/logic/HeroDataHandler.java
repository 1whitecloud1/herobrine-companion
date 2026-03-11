package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.HeroWorldData;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class HeroDataHandler {

    /**
     * 从全局存档同步信任度到实体 (Read)
     */
    public static void syncGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID == null) return;

            HeroWorldData data = HeroWorldData.get(serverLevel);

            // 1. 同步信任度
            int trust = data.getTrust(ownerUUID);
            hero.setTrustLevel(trust);

            // 2. 同步奖励状态：将 WorldData 的 int[] 转换为实体的位掩码 (int)
            int[] rewards = data.getClaimedRewards(ownerUUID);
            int flags = 0;
            for (int r : rewards) {
                if (r >= 0 && r < 32) {
                    flags |= (1 << r);
                }
            }
            hero.setClaimedRewards(flags);
        }
    }

    /**
     * 将实体信任度更新到全局存档 (Write)
     */
    public static void updateGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID == null) return;

            HeroWorldData data = HeroWorldData.get(serverLevel);

            // 1. 更新信任度
            int currentTrust = hero.getTrustLevel();
            if (currentTrust != data.getTrust(ownerUUID)) {
                data.setTrust(ownerUUID, currentTrust);
            }

            // 2. 更新奖励状态：解析实体的位掩码 (int)，更新到 WorldData
            int flags = hero.getClaimedRewards();
            for (int i = 0; i < 32; i++) {
                if ((flags & (1 << i)) != 0) {
                    // 如果实体中该位为 1，则将其记录到 WorldData
                    data.claimReward(ownerUUID, i);
                }
            }
        }
    }

    /**
     * 尝试从附近的玩家身上恢复信任度 (Legacy / Backup)
     * [Deprecated] 现在使用 HeroWorldData，此方法仅用于兼容旧存档迁移
     */
    public static void restoreTrustFromPlayer(HeroEntity hero) {
        // 直接复用新的全局同步逻辑即可
        syncGlobalTrust(hero);
    }
}