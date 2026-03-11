package com.whitecloud233.herobrine_companion.network;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HeroWorldData extends SavedData {

    private final Map<UUID, PlayerProfile> playerProfiles = new HashMap<>();

    // 遗留数据暂存区
    private int legacyGlobalTrust = -1;
    private int legacyClaimedRewards = 0;

    // 全局数据
    private int globalSkinVariant = 2; // 默认 Herobrine (2)
    private String customSkinName = ""; // [新增] 自定义皮肤名称/URL
    private CompoundTag tempBrainData = null;
    private long respawnReadyTime = 0;  // [新增] 重生冷却时间

    private UUID activeHeroUUID = null;
    private GlobalPos lastKnownHeroPos = null;

    public static class PlayerProfile {
        public int trustLevel = 0;
        public int claimedRewardsFlags = 0;
        public CompoundTag brainMemory = new CompoundTag();

        // [新增] 装备持久化数据
        public ListTag armorItems = new ListTag();
        public ListTag handItems = new ListTag();
        public CompoundTag curiosBackItem = new CompoundTag();

        public PlayerProfile() {}

        public void load(CompoundTag tag) {
            this.trustLevel = tag.getInt("Trust");
            this.claimedRewardsFlags = tag.getInt("Rewards");
            if (tag.contains("Brain")) this.brainMemory = tag.getCompound("Brain");

            // [新增] 读取装备与背部饰品
            if (tag.contains("ArmorItems", Tag.TAG_LIST)) this.armorItems = tag.getList("ArmorItems", Tag.TAG_COMPOUND);
            if (tag.contains("HandItems", Tag.TAG_LIST)) this.handItems = tag.getList("HandItems", Tag.TAG_COMPOUND);
            if (tag.contains("CuriosBackItem", Tag.TAG_COMPOUND)) this.curiosBackItem = tag.getCompound("CuriosBackItem");
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Trust", this.trustLevel);
            tag.putInt("Rewards", this.claimedRewardsFlags);
            tag.put("Brain", this.brainMemory);

            // [新增] 保存装备与背部饰品
            tag.put("ArmorItems", this.armorItems);
            tag.put("HandItems", this.handItems);
            tag.put("CuriosBackItem", this.curiosBackItem);
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
        compound.putString("CustomSkinName", this.customSkinName); // [新增]
        compound.putLong("RespawnReadyTime", this.respawnReadyTime); // [新增]

        if (this.tempBrainData != null) {
            compound.put("TempBrainData", this.tempBrainData);
        }

        if (this.legacyGlobalTrust != -1) {
            compound.putInt("GlobalTrust", this.legacyGlobalTrust);
            compound.putInt("ClaimedRewardsFlags", this.legacyClaimedRewards);
        }

        if (this.activeHeroUUID != null) {
            compound.putUUID("ActiveHeroUUID", this.activeHeroUUID);
        }

        if (this.lastKnownHeroPos != null) {
            compound.put("LastKnownHeroPos", writeGlobalPos(this.lastKnownHeroPos));
        }

        return compound;
    }

    public static HeroWorldData load(CompoundTag compound, net.minecraft.core.HolderLookup.Provider provider) {
        HeroWorldData data = new HeroWorldData();

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

        if (compound.contains("GlobalTrust")) data.legacyGlobalTrust = compound.getInt("GlobalTrust");
        if (compound.contains("ClaimedRewardsFlags")) {
            data.legacyClaimedRewards = compound.getInt("ClaimedRewardsFlags");
        } else if (compound.contains("ClaimedRewards")) {
            int[] rewards = compound.getIntArray("ClaimedRewards");
            for (int reward : rewards) {
                if (reward >= 0 && reward < 32) data.legacyClaimedRewards |= (1 << reward);
            }
        }

        if (compound.contains("GlobalSkinVariant")) data.globalSkinVariant = compound.getInt("GlobalSkinVariant");
        if (compound.contains("CustomSkinName")) data.customSkinName = compound.getString("CustomSkinName"); // [新增]
        if (compound.contains("RespawnReadyTime")) data.respawnReadyTime = compound.getLong("RespawnReadyTime"); // [新增]
        if (compound.contains("TempBrainData")) data.tempBrainData = compound.getCompound("TempBrainData");
        if (compound.hasUUID("ActiveHeroUUID")) data.activeHeroUUID = compound.getUUID("ActiveHeroUUID");
        if (compound.contains("LastKnownHeroPos")) data.lastKnownHeroPos = readGlobalPos(compound.getCompound("LastKnownHeroPos"));

        return data;
    }

    private static CompoundTag writeGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(pos.pos()));
        return tag;
    }

    private static GlobalPos readGlobalPos(CompoundTag tag) {
        try {
            ResourceLocation dimLoc = ResourceLocation.parse(tag.getString("Dimension"));
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            return GlobalPos.of(dimKey, NbtUtils.readBlockPos(tag, "Pos").orElse(net.minecraft.core.BlockPos.ZERO));
        } catch (Exception e) {
            return null;
        }
    }

    // --- Player Profile Access ---

    public PlayerProfile getProfile(UUID playerUUID) {
        return playerProfiles.computeIfAbsent(playerUUID, k -> {
            PlayerProfile newProfile = new PlayerProfile();
            if (this.legacyGlobalTrust != -1) {
                newProfile.trustLevel = this.legacyGlobalTrust;
                newProfile.claimedRewardsFlags = this.legacyClaimedRewards;
                this.legacyGlobalTrust = -1;
                this.legacyClaimedRewards = 0;
                this.setDirty();
            }
            return newProfile;
        });
    }

    // --- Trust API ---
    public int getTrust(UUID playerUUID) { return getProfile(playerUUID).trustLevel; }
    public void setTrust(UUID playerUUID, int trust) {
        getProfile(playerUUID).trustLevel = trust;
        this.setDirty();
    }

    // --- Rewards API (适配 HeroEntity 调用) ---
    public boolean hasClaimedReward(UUID playerUUID, int rewardId) {
        if (rewardId < 0 || rewardId >= 32) return false;
        return (getProfile(playerUUID).claimedRewardsFlags & (1 << rewardId)) != 0;
    }
    public boolean isRewardClaimed(UUID playerUUID, int rewardId) {
        return hasClaimedReward(playerUUID, rewardId); // API 别名适配
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
    public void setRewardClaimed(UUID playerUUID, int rewardId, boolean claimed) {
        if (claimed) claimReward(playerUUID, rewardId);
        else {
            if (rewardId >= 0 && rewardId < 32) {
                getProfile(playerUUID).claimedRewardsFlags &= ~(1 << rewardId);
                this.setDirty();
            }
        }
    }

    public int[] getClaimedRewards(UUID playerUUID) {
        int flags = getProfile(playerUUID).claimedRewardsFlags;
        Set<Integer> rewards = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            if ((flags & (1 << i)) != 0) rewards.add(i);
        }
        return rewards.stream().mapToInt(i -> i).toArray();
    }

    // --- Equipment Backup API [新增] ---
    public ListTag getArmorItems(UUID uuid) { return getProfile(uuid).armorItems; }
    public ListTag getHandItems(UUID uuid) { return getProfile(uuid).handItems; }

    public void setEquipment(UUID uuid, ListTag armor, ListTag hands) {
        getProfile(uuid).armorItems = armor;
        getProfile(uuid).handItems = hands;
        this.setDirty();
    }

    public CompoundTag getCuriosBackItem(UUID uuid) { return getProfile(uuid).curiosBackItem; }
    public void setCuriosBackItem(UUID uuid, CompoundTag tag) {
        getProfile(uuid).curiosBackItem = tag;
        this.setDirty();
    }

    // --- Brain Memory API ---
    public CompoundTag getBrainMemory(UUID playerUUID) { return getProfile(playerUUID).brainMemory; }
    public void setBrainMemory(UUID playerUUID, CompoundTag memory) {
        getProfile(playerUUID).brainMemory = memory;
        this.setDirty();
    }

    // --- Global Data API ---
    public int getGlobalSkinVariant() { return this.globalSkinVariant; }
    public void setGlobalSkinVariant(int variant) {
        if (this.globalSkinVariant != variant) {
            this.globalSkinVariant = variant;
            this.setDirty();
        }
    }

    public String getGlobalCustomSkinName() { return this.customSkinName; }
    public void setGlobalCustomSkinName(String name) {
        if (!this.customSkinName.equals(name)) {
            this.customSkinName = name;
            this.setDirty();
        }
    }

    public void setRespawnCooldown(ServerLevel level, int minutes) {
        this.respawnReadyTime = level.getGameTime() + (long) minutes * 60 * 20;
        this.setDirty();
    }
    public long getRespawnReadyTime() { return this.respawnReadyTime; }

    public CompoundTag getTempBrainData() { return this.tempBrainData; }
    public void setTempBrainData(CompoundTag data) {
        this.tempBrainData = data;
        this.setDirty();
    }

    // --- Active Hero API ---
    public UUID getActiveHeroUUID() { return this.activeHeroUUID; }
    public void setActiveHeroUUID(UUID uuid) {
        this.activeHeroUUID = uuid;
        this.setDirty();
    }

    public GlobalPos getLastKnownHeroPos() { return this.lastKnownHeroPos; }
    public void setLastKnownHeroPos(GlobalPos pos) {
        this.lastKnownHeroPos = pos;
        this.setDirty();
    }

    // --- 1.21.1 标准 Factory 初始化 ---
    public static HeroWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        HeroWorldData::new,
                        HeroWorldData::load,
                        null
                ),
                "herobrine_companion_data"
        );
    }
}