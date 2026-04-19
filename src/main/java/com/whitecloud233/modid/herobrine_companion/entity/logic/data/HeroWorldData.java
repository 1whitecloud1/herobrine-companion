package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

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
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * ⚡ 核心架构升级：Facade (外观模式) 分流存储
 * ----------------------------------------------------
 * 该类对外保持了原有的所有 API 调用格式 (无需修改其他文件的代码)
 * 对内则将原先臃肿的“上帝对象”拆分为：
 * 1. 独立玩家专属数据文件 (herobrine_companion_player_UUID.dat)
 * 2. 全局轻量级状态文件 (herobrine_companion_global.dat)
 * 大幅度降低磁盘 I/O 瓶颈并解决服务器 TPS 卡顿问题。
 */
public class HeroWorldData {

    // ==========================================
    // 1. 数据结构分离：个人档案 (PlayerProfile)
    // 继承 SavedData，现在每个玩家拥有极其轻量的独立存档！
    // ==========================================
    public static class PlayerProfile extends SavedData {
        public int trust = 0;
        public Set<Integer> claimedRewards = new HashSet<>();
        public CompoundTag brainMemory = new CompoundTag();
        public ListTag armorItems = new ListTag();
        public ListTag handItems = new ListTag();
        public CompoundTag curiosBackItem = new CompoundTag();
        public CompoundTag poseData = new CompoundTag();

        public UUID activeHeroUUID = null;
        public GlobalPos lastKnownHeroPos = null;
        public boolean hasSpawnedFromChat = false;
        public int skinVariant = 0;
        public String customSkinName = "";
        public byte[] customSkinData = new byte[0];
        public CompoundTag tempBrainData = null;
        public long respawnReadyTime = 0;

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            tag.putInt("Trust", trust);
            tag.putIntArray("ClaimedRewards", claimedRewards.stream().mapToInt(i -> i).toArray());
            tag.put("BrainMemory", brainMemory);
            tag.put("ArmorItems", armorItems);
            tag.put("HandItems", handItems);
            tag.put("CuriosBackItem", curiosBackItem);
            tag.put("PoseData", poseData);

            if (activeHeroUUID != null) tag.putUUID("ActiveHeroUUID", activeHeroUUID);
            if (lastKnownHeroPos != null) tag.put("LastKnownHeroPos", writeGlobalPos(lastKnownHeroPos));
            tag.putBoolean("HasSpawnedFromChat", hasSpawnedFromChat);
            tag.putInt("SkinVariant", skinVariant);
            tag.putString("CustomSkinName", customSkinName);
            tag.putByteArray("CustomSkinData", customSkinData);
            if (tempBrainData != null) tag.put("TempBrainData", tempBrainData);
            tag.putLong("RespawnReadyTime", respawnReadyTime);
            return tag;
        }

        public static PlayerProfile load(CompoundTag tag) {
            PlayerProfile profile = new PlayerProfile();
            profile.trust = tag.getInt("Trust");
            int[] rewards = tag.getIntArray("ClaimedRewards");
            for (int id : rewards) profile.claimedRewards.add(id);
            if (tag.contains("BrainMemory")) profile.brainMemory = tag.getCompound("BrainMemory");
            if (tag.contains("ArmorItems", 9)) profile.armorItems = tag.getList("ArmorItems", 10);
            if (tag.contains("HandItems", 9)) profile.handItems = tag.getList("HandItems", 10);
            if (tag.contains("CuriosBackItem", 10)) profile.curiosBackItem = tag.getCompound("CuriosBackItem");
            if (tag.contains("PoseData", 10)) profile.poseData = tag.getCompound("PoseData");

            if (tag.hasUUID("ActiveHeroUUID")) profile.activeHeroUUID = tag.getUUID("ActiveHeroUUID");
            if (tag.contains("LastKnownHeroPos")) profile.lastKnownHeroPos = readGlobalPos(tag.getCompound("LastKnownHeroPos"));
            if (tag.contains("HasSpawnedFromChat")) profile.hasSpawnedFromChat = tag.getBoolean("HasSpawnedFromChat");
            if (tag.contains("SkinVariant")) profile.skinVariant = tag.getInt("SkinVariant");
            if (tag.contains("CustomSkinName")) profile.customSkinName = tag.getString("CustomSkinName");
            if (tag.contains("CustomSkinData", Tag.TAG_BYTE_ARRAY)) profile.customSkinData = tag.getByteArray("CustomSkinData");
            if (tag.contains("TempBrainData")) profile.tempBrainData = tag.getCompound("TempBrainData");
            if (tag.contains("RespawnReadyTime")) profile.respawnReadyTime = tag.getLong("RespawnReadyTime");
            return profile;
        }

        private static CompoundTag writeGlobalPos(GlobalPos pos) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", pos.dimension().location().toString());
            tag.put("Pos", NbtUtils.writeBlockPos(pos.pos()));
            return tag;
        }

        private static GlobalPos readGlobalPos(CompoundTag tag) {
            try {
                ResourceLocation dimLoc = ResourceLocation.tryParse(tag.getString("Dimension"));
                if (dimLoc == null) return null;
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                return GlobalPos.of(dimKey, NbtUtils.readBlockPos(tag.getCompound("Pos")));
            } catch (Exception e) { return null; }
        }
    }

    // ==========================================
    // 2. 数据结构分离：全局公共数据 (GlobalData)
    // 专门存放服务器层面的锁定状态
    // ==========================================
    public static class GlobalData extends SavedData {
        public UUID activeChallengerUUID = null;
        public boolean migrated = false;

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            if (activeChallengerUUID != null) tag.putUUID("ActiveChallengerUUID", activeChallengerUUID);
            tag.putBoolean("Migrated", migrated);
            return tag;
        }

        public static GlobalData load(CompoundTag tag) {
            GlobalData data = new GlobalData();
            if (tag.hasUUID("ActiveChallengerUUID")) data.activeChallengerUUID = tag.getUUID("ActiveChallengerUUID");
            data.migrated = tag.getBoolean("Migrated");
            return data;
        }
    }

    // 用于读取旧版 God Object 数据的临时类，实现无损热迁移
    public static class LegacyData extends SavedData {
        public CompoundTag rawData;
        @Override public @NotNull CompoundTag save(@NotNull CompoundTag t) { return rawData; }
        public static LegacyData load(CompoundTag t) {
            LegacyData l = new LegacyData();
            l.rawData = t;
            return l;
        }
    }

    // ==========================================
    // 3. 核心控制器封装 (分发器)
    // ==========================================
    private final ServerLevel overworld;
    private final GlobalData globalData;

    private HeroWorldData(ServerLevel overworld) {
        this.overworld = overworld;
        this.globalData = overworld.getDataStorage().computeIfAbsent(GlobalData::load, GlobalData::new, "herobrine_companion_global");
        migrateIfNecessary(); // 启动时检查并执行数据平滑过渡
    }

    public static HeroWorldData get(ServerLevel level) {
        // 确保所有数据统一绑定在主世界 (Overworld)
        ServerLevel overworld = Objects.requireNonNull(level.getServer().getLevel(Level.OVERWORLD));
        return new HeroWorldData(overworld);
    }

    /**
     * 自动将玩家的旧存档拆分到独立的小文件中
     */
    private void migrateIfNecessary() {
        if (globalData.migrated) return;

        LegacyData legacy = overworld.getDataStorage().get(LegacyData::load, "herobrine_companion_data");
        if (legacy != null && legacy.rawData != null) {
            CompoundTag compound = legacy.rawData;

            // 拆分玩家个人数据
            if (compound.contains("PlayerProfiles", Tag.TAG_LIST)) {
                ListTag profilesTag = compound.getList("PlayerProfiles", Tag.TAG_COMPOUND);
                for (int i = 0; i < profilesTag.size(); i++) {
                    CompoundTag profileTag = profilesTag.getCompound(i);
                    UUID uuid = profileTag.getUUID("UUID");
                    PlayerProfile profile = getProfile(uuid);

                    PlayerProfile oldData = PlayerProfile.load(profileTag);
                    profile.trust = oldData.trust;
                    profile.claimedRewards = oldData.claimedRewards;
                    profile.brainMemory = oldData.brainMemory;
                    profile.armorItems = oldData.armorItems;
                    profile.handItems = oldData.handItems;
                    profile.curiosBackItem = oldData.curiosBackItem;
                    profile.poseData = oldData.poseData;
                    profile.activeHeroUUID = oldData.activeHeroUUID;
                    profile.lastKnownHeroPos = oldData.lastKnownHeroPos;
                    profile.hasSpawnedFromChat = oldData.hasSpawnedFromChat;
                    profile.skinVariant = oldData.skinVariant;
                    profile.customSkinName = oldData.customSkinName;
                    profile.customSkinData = oldData.customSkinData;
                    profile.tempBrainData = oldData.tempBrainData;
                    profile.respawnReadyTime = oldData.respawnReadyTime;

                    profile.setDirty(); // 保存为独立文件
                }
            }

            // 转移全局擂台锁
            if (compound.hasUUID("ActiveChallengerUUID")) {
                globalData.activeChallengerUUID = compound.getUUID("ActiveChallengerUUID");
            }
        }

        globalData.migrated = true;
        globalData.setDirty();
    }

    /**
     * 获取玩家独立数据，时间复杂度 O(1)
     */
    public PlayerProfile getProfile(UUID uuid) {
        if (uuid == null) return new PlayerProfile(); // 容错处理
        return overworld.getDataStorage().computeIfAbsent(
                PlayerProfile::load,
                PlayerProfile::new,
                "herobrine_companion_player_" + uuid
        );
    }

    // ==========================================
    // 4. API 接口 (100% 兼容你原来的外部调用格式)
    // 所有修改都会精准触发对应小文件的 setDirty()，极大地拯救 TPS
    // ==========================================

    public UUID getActiveChallengerUUID() { return globalData.activeChallengerUUID; }
    public void setActiveChallengerUUID(UUID uuid) { globalData.activeChallengerUUID = uuid; globalData.setDirty(); }

    public int getTrust(UUID uuid) { return getProfile(uuid).trust; }
    public void setTrust(UUID uuid, int trust) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.trust = trust;
        profile.setDirty(); // 🚨 只保存该玩家自己的文件！
    }

    public void addClaimedReward(UUID uuid, int rewardId) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.claimedRewards.add(rewardId);
        profile.setDirty();
    }

    public java.util.Set<Integer> getClaimedRewards(UUID uuid) { return getProfile(uuid).claimedRewards; }

    public boolean isRewardClaimed(UUID uuid, int id) { return getProfile(uuid).claimedRewards.contains(id); }

    public void setRewardClaimed(UUID uuid, int id, boolean claimed) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (claimed) profile.claimedRewards.add(id);
        else profile.claimedRewards.remove(id);
        profile.setDirty();
    }

    public CompoundTag getBrainMemory(UUID uuid) { return getProfile(uuid).brainMemory; }
    public void setBrainMemory(UUID uuid, CompoundTag memory) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.brainMemory = memory;
        profile.setDirty();
    }

    public ListTag getArmorItems(UUID uuid) { return getProfile(uuid).armorItems; }
    public ListTag getHandItems(UUID uuid) { return getProfile(uuid).handItems; }
    public void setEquipment(UUID uuid, ListTag armor, ListTag hands) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.armorItems = armor;
        profile.handItems = hands;
        profile.setDirty();
    }

    public CompoundTag getCuriosBackItem(UUID uuid) { return getProfile(uuid).curiosBackItem; }
    public void setCuriosBackItem(UUID uuid, CompoundTag tag) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.curiosBackItem = tag;
        profile.setDirty();
    }

    public CompoundTag getPoseData(UUID uuid) { return getProfile(uuid).poseData; }
    public void setPoseData(UUID uuid, CompoundTag tag) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.poseData = tag;
        profile.setDirty();
    }

    public void setRespawnCooldown(UUID uuid, ServerLevel level, int minutes) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.respawnReadyTime = level.getGameTime() + (long) minutes * 60 * 20;
        profile.setDirty();
    }

    public int getSkinVariant(UUID uuid) { return getProfile(uuid).skinVariant; }
    public void setSkinVariant(UUID uuid, int variant) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.skinVariant = variant;
        profile.setDirty();
    }

    public String getCustomSkinName(UUID uuid) { return getProfile(uuid).customSkinName; }
    public void setCustomSkinName(UUID uuid, String name) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.customSkinName = name;
        profile.setDirty();
    }

    public byte[] getCustomSkinData(UUID uuid) { return getProfile(uuid).customSkinData; }
    public void setCustomSkinData(UUID uuid, byte[] data) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.customSkinData = data != null ? data : new byte[0];
        profile.setDirty();
    }

    public CompoundTag getTempBrainData(UUID uuid) { return getProfile(uuid).tempBrainData; }
    public void setTempBrainData(UUID uuid, CompoundTag data) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.tempBrainData = data;
        profile.setDirty();
    }

    public UUID getActiveHeroUUID(UUID uuid) { return getProfile(uuid).activeHeroUUID; }
    public void setActiveHeroUUID(UUID uuid, UUID heroId) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.activeHeroUUID = heroId;
        profile.setDirty();
    }

    public GlobalPos getLastKnownHeroPos(UUID uuid) { return getProfile(uuid).lastKnownHeroPos; }
    public void setLastKnownHeroPos(UUID uuid, GlobalPos pos) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.lastKnownHeroPos = pos;
        profile.setDirty();
    }

    public boolean hasSpawnedFromChat(UUID uuid) { return getProfile(uuid).hasSpawnedFromChat; }
    public void setSpawnedFromChat(UUID uuid, boolean spawned) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.hasSpawnedFromChat = spawned;
        profile.setDirty();
    }
}