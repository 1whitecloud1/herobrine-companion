package com.whitecloud233.herobrine_companion.client.network;

import com.whitecloud233.herobrine_companion.client.event.ClientHooks;
import com.whitecloud233.herobrine_companion.client.render.HeroClientSkinCache;
import com.whitecloud233.herobrine_companion.client.service.CrossChatHistoryStore;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * 收包后把服务端实体状态同步到客户端实体的处理入口。
 */
public final class ClientStateSync {
    private ClientStateSync() {
    }

    public static void applySavePose(int entityId, boolean isPosing, float[][] angles) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof HeroEntity hero) {
            hero.isPoseEditing = isPosing;
            hero.customPoseAngles = copyAngles(angles);
        }
    }

    public static void applySyncRewards(int entityId, Set<Integer> claimedRewards) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof HeroEntity hero) {
            for (int rewardId : claimedRewards) {
                hero.claimReward(rewardId);
            }
        }
    }

    public static void applySyncHeroCosmetics(int entityId, int skinVariant, String customSkinName,
                                              byte[] customSkinData, CompoundTag curiosBackItem,
                                              CompoundTag accessoriesData) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof HeroEntity hero)) {
            return;
        }

        hero.setSkinVariant(skinVariant);
        hero.setCustomSkinName(customSkinName);
        hero.setCuriosBackItemFromTag(curiosBackItem);
        hero.setAccessoriesDataFromTag(accessoriesData);

        if (skinVariant == HeroEntity.SKIN_CUSTOM && customSkinData.length > 0) {
            HeroClientSkinCache.put(hero.getUUID(), customSkinData);
        } else {
            HeroClientSkinCache.clear(hero.getUUID());
        }
    }

    public static void setVisitedHeroDimension(boolean visited) {
        ClientHooks.setVisitedHeroDimension(visited);
    }

    public static void appendCrossChatHistory(String peerName, boolean hbMode, String speaker, String content, String kind) {
        CrossChatHistoryStore.getInstance().appendEntry(peerName, hbMode, speaker, content, kind);
    }

    public static void syncCrossChatState(boolean allowIncoming, boolean activeSession, String peerName,
                                          boolean autoChatEnabled, int autoHbTurnLimit) {
        ClientHooks.syncCrossChatState(allowIncoming, activeSession, peerName, autoChatEnabled, autoHbTurnLimit);
    }

    private static float[][] copyAngles(float[][] source) {
        float[][] dest = new float[10][3];
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source[i], 0, dest[i], 0, 3);
        }
        return dest;
    }
}
