package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncRewardsPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;


public class HeroDataHandler {

    public static void syncGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);

            // 【核心修复1】：绝对不要遍历周围玩家！Hero 只能同步它自己真正主人的数据！
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null) {
                int globalTrust = data.getTrust(ownerUUID);
                // 同步信任度
                if (hero.getTrustLevel() != globalTrust) {
                    hero.setTrustLevel(globalTrust);
                }

                // 同步奖励状态
                Set<Integer> rewards = data.getClaimedRewards(ownerUUID);
                for (int r : rewards) {
                    if (!hero.hasClaimedReward(r)) {
                        hero.claimReward(r);
                    }
                }

                // 👇【核心修复3】：将服务器的奖励数据强制同步给客户端，防止界面按钮错误点亮！
                Player player = serverLevel.getPlayerByUUID(ownerUUID);
                if (player instanceof ServerPlayer serverPlayer) {
                    PacketHandler.sendToPlayer(
                            new SyncRewardsPacket(hero.getId(), rewards),
                            serverPlayer
                    );
                }
            }
        }
    }

    public static void restoreTrustFromPlayer(HeroEntity hero) {
        if (hero.getTrustLevel() > 0) return;

        // 【核心修复2】：即使找不到 Owner，也坚决不能用 getNearestPlayer 去认别人当主人！
        // 否则会导致主权被劫持，进而触发唯一性检测被误杀。
        Player p = null;
        if (hero.getOwnerUUID() != null) {
            p = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        }

        if (p != null) {
            if (hero.level() instanceof ServerLevel serverLevel) {
                HeroWorldData data = HeroWorldData.get(serverLevel);
                int trust = data.getTrust(p.getUUID());
                if (trust > 0) {
                    hero.setTrustLevel(trust);
                }
            }
        }
    }

    public static void updateGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null) {
                int current = hero.getTrustLevel();
                if (current != data.getTrust(ownerUUID)) {
                    data.setTrust(ownerUUID, current);
                }
            }
        }
    }
}