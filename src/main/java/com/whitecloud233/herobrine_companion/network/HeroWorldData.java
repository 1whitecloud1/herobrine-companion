package com.whitecloud233.herobrine_companion.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HeroWorldData extends SavedData {
    
    private final Map<UUID, PlayerProfile> playerProfiles = new HashMap<>();

    // [新增] 遗留数据暂存区
    private int legacyGlobalTrust = -1;
    private int legacyClaimedRewards = 0;

    // [修改] 默认皮肤状态改为 Herobrine (2)
    private int globalSkinVariant = 2;
    private CompoundTag tempBrainData = null;

    public static class PlayerProfile {
        private int trustLevel = 0;
        private int claimedRewardsFlags = 0;
        private CompoundTag brainMemory = new CompoundTag();

        public PlayerProfile() {}

        public void load(CompoundTag tag) {
            this.trustLevel = tag.getInt("Trust");
            this.claimedRewardsFlags = tag.getInt("Rewards");
            if (tag.contains("Brain")) {
                this.brainMemory = tag.getCompound("Brain");
            }
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Trust", this.trustLevel);
            tag.putInt("Rewards", this.claimedRewardsFlags);
            tag.put("Brain", this.brainMemory);
            return tag;
        }
    }

    @Override
    public CompoundTag save(CompoundTag compound, net.minecraft.core.HolderLookup.Provider provider) {
        ListTag profileList = new ListTag();
        for (Map.Entry<UUID, PlayerProfile> entry : playerProfiles.entrySet()) {
            CompoundTag tag = entry.getValue().save();
            tag.putUUID("UUID", entry.getKey());
            profileList.add(tag);
        }
        compound.put("PlayerProfiles", profileList);
        
        compound.putInt("GlobalSkinVariant", this.globalSkinVariant);
        
        if (this.tempBrainData != null) {
            compound.put("TempBrainData", this.tempBrainData);
        }
        
        // 如果遗留数据还没被任何人继承，继续保存，防止丢失
        if (this.legacyGlobalTrust != -1) {
            compound.putInt("GlobalTrust", this.legacyGlobalTrust);
            compound.putInt("ClaimedRewardsFlags", this.legacyClaimedRewards);
        }
        
        return compound;
    }

    public static HeroWorldData load(CompoundTag compound, net.minecraft.core.HolderLookup.Provider provider) {
        HeroWorldData data = new HeroWorldData();
        
        // 1. 读取新版数据
        if (compound.contains("PlayerProfiles", Tag.TAG_LIST)) {
            ListTag list = compound.getList("PlayerProfiles", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                if (tag.hasUUID("UUID")) {
                    UUID uuid = tag.getUUID("UUID");
                    PlayerProfile profile = new PlayerProfile();
                    profile.load(tag);
                    data.playerProfiles.put(uuid, profile);
                }
            }
        }

        // 2. 读取旧版数据 (如果存在)
        if (compound.contains("GlobalTrust")) {
            data.legacyGlobalTrust = compound.getInt("GlobalTrust");
        }
        
        if (compound.contains("ClaimedRewardsFlags")) {
            data.legacyClaimedRewards = compound.getInt("ClaimedRewardsFlags");
        } else if (compound.contains("ClaimedRewards")) {
            // 兼容更早版本的数组格式
            int[] rewards = compound.getIntArray("ClaimedRewards");
            for (int reward : rewards) {
                if (reward >= 0 && reward < 32) {
                    data.legacyClaimedRewards |= (1 << reward);
                }
            }
        }

        if (compound.contains("GlobalSkinVariant")) {
            data.globalSkinVariant = compound.getInt("GlobalSkinVariant");
        }
        
        if (compound.contains("TempBrainData")) {
            data.tempBrainData = compound.getCompound("TempBrainData");
        }
        
        return data;
    }

    // --- Player Profile Access ---
    
    public PlayerProfile getProfile(UUID playerUUID) {
        // 如果该玩家还没有数据，创建一个新的
        return playerProfiles.computeIfAbsent(playerUUID, k -> {
            PlayerProfile newProfile = new PlayerProfile();
            
            // [核心逻辑] 数据迁移：先到先得
            // 如果存在遗留数据，且这是第一个被创建的 Profile，则继承旧数据
            if (this.legacyGlobalTrust != -1) {
                newProfile.trustLevel = this.legacyGlobalTrust;
                newProfile.claimedRewardsFlags = this.legacyClaimedRewards;
                
                // 迁移完成，清除遗留数据，防止被第二个人重复继承
                this.legacyGlobalTrust = -1;
                this.legacyClaimedRewards = 0;
                this.setDirty(); // 标记保存
                
                // 可以在这里打印一条日志
                // System.out.println("Migrated legacy Herobrine data to player: " + playerUUID);
            }
            
            return newProfile;
        });
    }

    // --- Trust API ---
    
    public int getTrust(UUID playerUUID) {
        return getProfile(playerUUID).trustLevel;
    }

    public void setTrust(UUID playerUUID, int trust) {
        getProfile(playerUUID).trustLevel = trust;
        this.setDirty();
    }
    
    // --- Rewards API ---
    
    public boolean hasClaimedReward(UUID playerUUID, int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return false;
        return (getProfile(playerUUID).claimedRewardsFlags & (1 << rewardId)) != 0;
    }

    public void claimReward(UUID playerUUID, int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return;
        PlayerProfile profile = getProfile(playerUUID);
        int mask = (1 << rewardId);
        if ((profile.claimedRewardsFlags & mask) == 0) {
            profile.claimedRewardsFlags |= mask;
            this.setDirty();
        }
    }
    
    public int[] getClaimedRewards(UUID playerUUID) {
        int flags = getProfile(playerUUID).claimedRewardsFlags;
        Set<Integer> rewards = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            if ((flags & (1 << i)) != 0) {
                rewards.add(i);
            }
        }
        return rewards.stream().mapToInt(i -> i).toArray();
    }
    
    // --- Brain Memory API ---
    
    public CompoundTag getBrainMemory(UUID playerUUID) {
        return getProfile(playerUUID).brainMemory;
    }
    
    public void setBrainMemory(UUID playerUUID, CompoundTag memory) {
        getProfile(playerUUID).brainMemory = memory;
        this.setDirty();
    }

    // --- Global Skin API ---
    
    public int getGlobalSkinVariant() {
        return this.globalSkinVariant;
    }

    public void setGlobalSkinVariant(int variant) {
        if (this.globalSkinVariant != variant) {
            this.globalSkinVariant = variant;
            this.setDirty();
        }
    }
    
    public CompoundTag getTempBrainData() {
        return this.tempBrainData;
    }
    
    public void setTempBrainData(CompoundTag data) {
        this.tempBrainData = data;
        this.setDirty();
    }

    public static HeroWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        HeroWorldData::new,
                        HeroWorldData::load,
                        null // <--- 修复：改为 null，防止原版清洗自定义数据
                ),
                "herobrine_companion_data"
        );
    }
}
