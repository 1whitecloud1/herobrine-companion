package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SavePosePacket;
import com.whitecloud233.herobrine_companion.network.SyncHeroCosmeticsPacket;
import com.whitecloud233.herobrine_companion.network.SyncRewardsPacket;
import net.minecraft.server.level.ServerPlayer;


public final class HeroTrackingSync {
    private HeroTrackingSync() {
    }

    public static void sendSnapshot(HeroEntity hero, ServerPlayer player) {
        if (hero.isPoseEditing) {
            PacketHandler.sendToPlayer(new SavePosePacket(hero.getId(), true, hero.customPoseAngles), player);
        }
        // 新追踪者无法从自己的本地路径重建他人的皮肤与饰品。
        PacketHandler.sendToPlayer(new SyncHeroCosmeticsPacket(hero), player);
        PacketHandler.sendToPlayer(new SyncRewardsPacket(hero.getId(), hero.getClaimedRewards()), player);
    }
}
