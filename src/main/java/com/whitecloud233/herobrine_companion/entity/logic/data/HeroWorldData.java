package com.whitecloud233.herobrine_companion.entity.logic.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

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
    public static final int DEFAULT_AUTO_HB_TURN_LIMIT = 8;
    public static final int MIN_AUTO_HB_TURN_LIMIT = 1;
    public static final int MAX_AUTO_HB_TURN_LIMIT = 20;

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
        public byte[] customSkinData = new byte[0];

        public String customSkinName = "";
        public CompoundTag tempBrainData = null;
        public long respawnReadyTime = 0;
        public boolean allowIncomingCrossChat = true;
        public String clientLanguageCode = "en_us";
        public int autoHbTurnLimit = DEFAULT_AUTO_HB_TURN_LIMIT;


        // 1.21.1：必须显式传入 HolderLookup.Provider
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
            tag.putByteArray("CustomSkinData", customSkinData);

            tag.putString("CustomSkinName", customSkinName);
            if (tempBrainData != null) tag.put("TempBrainData", tempBrainData);
            tag.putLong("RespawnReadyTime", respawnReadyTime);
            tag.putBoolean("AllowIncomingCrossChat", allowIncomingCrossChat);
            tag.putString("ClientLanguageCode", normalizeLanguageCode(clientLanguageCode));
            tag.putInt("AutoHbTurnLimit", normalizeAutoHbTurnLimit(autoHbTurnLimit));
            return tag;
        }

        // 1.21.1：Load 方法也需要增加 Provider 参数以匹配 Factory
        public static PlayerProfile load(CompoundTag tag, HolderLookup.Provider provider) {
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
            if (tag.contains("CustomSkinData", Tag.TAG_BYTE_ARRAY)) profile.customSkinData = tag.getByteArray("CustomSkinData");

            if (tag.contains("CustomSkinName")) profile.customSkinName = tag.getString("CustomSkinName");
            if (tag.contains("TempBrainData")) profile.tempBrainData = tag.getCompound("TempBrainData");
            if (tag.contains("RespawnReadyTime")) profile.respawnReadyTime = tag.getLong("RespawnReadyTime");
            if (tag.contains("AllowIncomingCrossChat")) profile.allowIncomingCrossChat = tag.getBoolean("AllowIncomingCrossChat");
            if (tag.contains("AutoHbTurnLimit")) profile.autoHbTurnLimit = normalizeAutoHbTurnLimit(tag.getInt("AutoHbTurnLimit"));
            if (tag.contains("ClientLanguageCode")) profile.clientLanguageCode = normalizeLanguageCode(tag.getString("ClientLanguageCode"));
            return profile;
        }
        private static String normalizeLanguageCode(String rawLanguageCode) {
            if (rawLanguageCode == null) {
                return "en_us";
            }
            String normalized = rawLanguageCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            return normalized.isEmpty() ? "en_us" : normalized;
        }
        private static int normalizeAutoHbTurnLimit(int turnLimit) {
            return Math.max(MIN_AUTO_HB_TURN_LIMIT, Math.min(MAX_AUTO_HB_TURN_LIMIT, turnLimit));
        }

        private static CompoundTag writeGlobalPos(GlobalPos pos) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", pos.dimension().location().toString());
            // 1.21.1 修复：手动保存 X/Y/Z 彻底避开 NbtUtils.writeBlockPos 的变动
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.pos().getX());
            posTag.putInt("Y", pos.pos().getY());
            posTag.putInt("Z", pos.pos().getZ());
            tag.put("Pos", posTag);
            return tag;
        }

        private static GlobalPos readGlobalPos(CompoundTag tag) {
            try {
                // 1.21.1 修复：改用 ResourceLocation.parse()
                ResourceLocation dimLoc = ResourceLocation.parse(tag.getString("Dimension"));
                ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
                // 1.21.1 修复：兼容提取 X/Y/Z 坐标，避开 NbtUtils.readBlockPos 参数不匹配问题
                CompoundTag posTag = tag.getCompound("Pos");
                BlockPos pos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                return GlobalPos.of(dimKey, pos);
            } catch (Exception e) { return null; }
        }
    }

    // ==========================================
    // 2. 数据结构分离：全局公共数据 (GlobalData)
    // ==========================================
    public static class GlobalData extends SavedData {
        public UUID activeChallengerUUID = null;
        public boolean migrated = false;

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            if (activeChallengerUUID != null) tag.putUUID("ActiveChallengerUUID", activeChallengerUUID);
            tag.putBoolean("Migrated", migrated);
            return tag;
        }

        public static GlobalData load(CompoundTag tag, HolderLookup.Provider provider) {
            GlobalData data = new GlobalData();
            if (tag.hasUUID("ActiveChallengerUUID")) data.activeChallengerUUID = tag.getUUID("ActiveChallengerUUID");
            data.migrated = tag.getBoolean("Migrated");
            return data;
        }
    }

    // 用于读取旧版 God Object 数据的临时类，实现无损热迁移
    public static class LegacyData extends SavedData {
        public CompoundTag rawData;
        @Override public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) { return rawData; }
        public static LegacyData load(CompoundTag t, HolderLookup.Provider provider) {
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
        // 1.21.1：使用 SavedData.Factory 包装
        SavedData.Factory<GlobalData> factory = new SavedData.Factory<>(GlobalData::new, GlobalData::load, null);
        this.globalData = overworld.getDataStorage().computeIfAbsent(factory, "herobrine_companion_global");
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

        // 1.21.1：使用 SavedData.Factory 读取老存档
        SavedData.Factory<LegacyData> legacyFactory = new SavedData.Factory<>(LegacyData::new, LegacyData::load, null);
        LegacyData legacy = overworld.getDataStorage().computeIfAbsent(legacyFactory, "herobrine_companion_data");

        if (legacy != null && legacy.rawData != null) {
            CompoundTag compound = legacy.rawData;

            // 拆分玩家个人数据
            if (compound.contains("PlayerProfiles", Tag.TAG_LIST)) {
                ListTag profilesTag = compound.getList("PlayerProfiles", Tag.TAG_COMPOUND);
                for (int i = 0; i < profilesTag.size(); i++) {
                    CompoundTag profileTag = profilesTag.getCompound(i);
                    UUID uuid = profileTag.getUUID("UUID");
                    PlayerProfile profile = getProfile(uuid);

                    // 旧档案迁移（传入空的Provider环境即可）
                    PlayerProfile oldData = PlayerProfile.load(profileTag, overworld.registryAccess());
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
                    profile.allowIncomingCrossChat = oldData.allowIncomingCrossChat;
                    profile.clientLanguageCode = oldData.clientLanguageCode;
                    profile.autoHbTurnLimit = oldData.autoHbTurnLimit;

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
        // 1.21.1：使用 SavedData.Factory 包装
        SavedData.Factory<PlayerProfile> factory = new SavedData.Factory<>(PlayerProfile::new, PlayerProfile::load, null);
        return overworld.getDataStorage().computeIfAbsent(factory, "herobrine_companion_player_" + uuid.toString());
    }

    // ==========================================
    // 4. API 接口 (100% 兼容你原来的外部调用格式)
    // ==========================================

    public UUID getActiveChallengerUUID() { return globalData.activeChallengerUUID; }
    public void setActiveChallengerUUID(UUID uuid) { globalData.activeChallengerUUID = uuid; globalData.setDirty(); }

    public int getTrust(UUID uuid) { return getProfile(uuid).trust; }
    public void setTrust(UUID uuid, int trust) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.trust = trust;
        profile.setDirty();
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

    public boolean isAllowIncomingCrossChat(UUID uuid) {
        return getProfile(uuid).allowIncomingCrossChat;
    }

    public void setAllowIncomingCrossChat(UUID uuid, boolean allowIncomingCrossChat) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        profile.allowIncomingCrossChat = allowIncomingCrossChat;
        profile.setDirty();
    }

    public String getClientLanguageCode(UUID uuid) {
        return PlayerProfile.normalizeLanguageCode(getProfile(uuid).clientLanguageCode);
    }

    public void setClientLanguageCode(UUID uuid, String clientLanguageCode) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        String normalized = PlayerProfile.normalizeLanguageCode(clientLanguageCode);
        if (Objects.equals(profile.clientLanguageCode, normalized)) {
            return;
        }
        profile.clientLanguageCode = normalized;
        profile.setDirty();
    }

    public int getAutoHbTurnLimit(UUID uuid) {
        return PlayerProfile.normalizeAutoHbTurnLimit(getProfile(uuid).autoHbTurnLimit);
    }

    public void setAutoHbTurnLimit(UUID uuid, int turnLimit) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        int normalized = PlayerProfile.normalizeAutoHbTurnLimit(turnLimit);
        if (profile.autoHbTurnLimit == normalized) {
            return;
        }
        profile.autoHbTurnLimit = normalized;
        profile.setDirty();
    }
}