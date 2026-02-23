package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class HeroWorldData extends SavedData {

    // [重构] 玩家档案类
    public static class PlayerProfile {
        public int trust = 0;
        public Set<Integer> claimedRewards = new HashSet<>();
        public CompoundTag brainMemory = new CompoundTag(); // 存储神经网络权重

        public void save(CompoundTag tag) {
            tag.putInt("Trust", trust);
            tag.putIntArray("ClaimedRewards", claimedRewards.stream().mapToInt(i -> i).toArray());
            tag.put("BrainMemory", brainMemory);
        }

        public void load(CompoundTag tag) {
            trust = tag.getInt("Trust");
            int[] rewards = tag.getIntArray("ClaimedRewards");
            for (int id : rewards) claimedRewards.add(id);
            if (tag.contains("BrainMemory")) {
                brainMemory = tag.getCompound("BrainMemory");
            }
        }
    }

    // [重构] 存储所有玩家的档案
    private final Map<UUID, PlayerProfile> playerProfiles = new HashMap<>();

    private long respawnReadyTime = 0;
    private boolean useHerobrineSkin = false;
    private CompoundTag tempBrainData = null;

    @Override
    public CompoundTag save(CompoundTag compound) {
        compound.putLong("RespawnReadyTime", this.respawnReadyTime);
        compound.putBoolean("UseHerobrineSkin", this.useHerobrineSkin);
        if (this.tempBrainData != null) {
            compound.put("TempBrainData", this.tempBrainData);
        }

        // 保存玩家档案
        ListTag profilesTag = new ListTag();
        for (Map.Entry<UUID, PlayerProfile> entry : playerProfiles.entrySet()) {
            CompoundTag profileTag = new CompoundTag();
            profileTag.putUUID("UUID", entry.getKey());
            entry.getValue().save(profileTag);
            profilesTag.add(profileTag);
        }
        compound.put("PlayerProfiles", profilesTag);

        return compound;
    }

    public static HeroWorldData load(CompoundTag compound) {
        HeroWorldData data = new HeroWorldData();
        if (compound.contains("RespawnReadyTime")) {
            data.respawnReadyTime = compound.getLong("RespawnReadyTime");
        }
        if (compound.contains("UseHerobrineSkin")) {
            data.useHerobrineSkin = compound.getBoolean("UseHerobrineSkin");
        }
        if (compound.contains("TempBrainData")) {
            data.tempBrainData = compound.getCompound("TempBrainData");
        }

        // 加载玩家档案
        if (compound.contains("PlayerProfiles", Tag.TAG_LIST)) {
            ListTag profilesTag = compound.getList("PlayerProfiles", Tag.TAG_COMPOUND);
            for (int i = 0; i < profilesTag.size(); i++) {
                CompoundTag profileTag = profilesTag.getCompound(i);
                UUID uuid = profileTag.getUUID("UUID");
                PlayerProfile profile = new PlayerProfile();
                profile.load(profileTag);
                data.playerProfiles.put(uuid, profile);
            }
        }

        return data;
    }

    // --- API ---

    public PlayerProfile getProfile(UUID uuid) {
        return playerProfiles.computeIfAbsent(uuid, k -> new PlayerProfile());
    }

    public int getTrust(UUID uuid) {
        return getProfile(uuid).trust;
    }

    public void setTrust(UUID uuid, int trust) {
        getProfile(uuid).trust = trust;
        this.setDirty();
    }

    public boolean isRewardClaimed(UUID uuid, int id) {
        return getProfile(uuid).claimedRewards.contains(id);
    }

    public void setRewardClaimed(UUID uuid, int id, boolean claimed) {
        if (claimed) getProfile(uuid).claimedRewards.add(id);
        else getProfile(uuid).claimedRewards.remove(id);
        this.setDirty();
    }

    public CompoundTag getBrainMemory(UUID uuid) {
        return getProfile(uuid).brainMemory;
    }

    public void setBrainMemory(UUID uuid, CompoundTag memory) {
        getProfile(uuid).brainMemory = memory;
        this.setDirty();
    }

    public void setRespawnCooldown(ServerLevel level, int minutes) {
        this.respawnReadyTime = level.getGameTime() + (long) minutes * 60 * 20;
        this.setDirty();
    }

    public boolean shouldUseHerobrineSkin() {
        return useHerobrineSkin;
    }

    public void setUseHerobrineSkin(boolean use) {
        this.useHerobrineSkin = use;
        this.setDirty();
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
                HeroWorldData::load,
                HeroWorldData::new,
                "herobrine_companion_data"
        );
    }
}