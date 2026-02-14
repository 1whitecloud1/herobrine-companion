package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.SyncRewardsPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HeroDataHandler {

    private static final String KEY_SAVED_TRUST = "HerobrineSavedTrust";

    public static void syncGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            
            // [修改] 遍历周围玩家，同步各自的信任度和奖励
            for (Player player : hero.level().players()) {
                if (player.distanceToSqr(hero) < 64 * 64) {
                    UUID uuid = player.getUUID();
                    int trust = data.getTrust(uuid);
                    
                    // 如果是 Owner，更新 HeroEntity 上的显示值 (仅用于渲染或简单逻辑)
                    if (uuid.equals(hero.getOwnerUUID())) {
                        if (trust > hero.getTrustLevel()) {
                            hero.setTrustLevel(trust);
                        }
                    }
                    
                    // 同步奖励状态给该玩家
                    Set<Integer> claimedRewards = getClaimedRewards(data, uuid);
                    SyncRewardsPacket packet = new SyncRewardsPacket(hero.getId(), claimedRewards);
                    PacketHandler.sendToPlayer(packet, (ServerPlayer) player);
                }
            }
        }
    }
    
    public static void syncRewardsToPlayer(HeroEntity hero, ServerPlayer player) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            Set<Integer> claimedRewards = getClaimedRewards(data, player.getUUID());
            PacketHandler.sendToPlayer(new SyncRewardsPacket(hero.getId(), claimedRewards), player);
        }
    }
    
    private static Set<Integer> getClaimedRewards(HeroWorldData data, UUID uuid) {
        Set<Integer> claimedRewards = new HashSet<>();
        for (HeroRewards.Reward reward : HeroRewards.REWARDS) {
            if (data.isRewardClaimed(uuid, reward.id)) {
                claimedRewards.add(reward.id);
            }
        }
        return claimedRewards;
    }

    public static void updateGlobalTrust(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel) {
            HeroWorldData data = HeroWorldData.get(serverLevel);
            
            // [修改] 如果有 Owner，更新 Owner 的信任度
            UUID ownerUUID = hero.getOwnerUUID();
            if (ownerUUID != null) {
                int current = hero.getTrustLevel();
                if (current != data.getTrust(ownerUUID)) {
                    data.setTrust(ownerUUID, current);
                }
            }
        }
    }

    public static void restoreTrustFromPlayer(HeroEntity hero) {
        if (hero.getTrustLevel() > 0) return;

        // [修改] 优先从 Owner 恢复，如果没有 Owner 则找最近的
        Player p = null;
        if (hero.getOwnerUUID() != null) {
            p = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        }
        
        if (p == null) {
            p = hero.level().getNearestPlayer(hero, 32.0D);
        }
        
        if (p != null) {
            // 尝试从 WorldData 恢复
            if (hero.level() instanceof ServerLevel serverLevel) {
                HeroWorldData data = HeroWorldData.get(serverLevel);
                int trust = data.getTrust(p.getUUID());
                if (trust > 0) {
                    hero.setTrustLevel(trust);
                    return;
                }
            }
            
            // 备用：从玩家 NBT 恢复 (旧数据迁移)
            CompoundTag data = p.getPersistentData();
            if (data.contains(KEY_SAVED_TRUST)) {
                int savedTrust = data.getInt(KEY_SAVED_TRUST);
                hero.setTrustLevel(savedTrust);
            }
        }
    }
}
