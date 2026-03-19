package com.whitecloud233.herobrine_companion.event;

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HeroWorldData extends SavedData {

    // [1.21.1 新增] 必须定义 Factory 以供 computeIfAbsent 使用
    public static final SavedData.Factory<HeroWorldData> FACTORY = new SavedData.Factory<>(
            HeroWorldData::new,
            HeroWorldData::load,
            null // DataFixTypes，传 null 即可
    );

    // [重构] 玩家档案类
    public static class PlayerProfile {
        public int trust = 0;
        public Set<Integer> claimedRewards = new HashSet<>();
        public CompoundTag brainMemory = new CompoundTag(); // 存储神经网络权重

        // [修改] 存储原生的护甲、手持数据 以及 背部槽
        public ListTag armorItems = new ListTag();
        public ListTag handItems = new ListTag();
        public CompoundTag curiosBackItem = new CompoundTag(); // [新增]

        public void save(CompoundTag tag) {
            tag.putInt("Trust", trust);
            tag.putIntArray("ClaimedRewards", claimedRewards.stream().mapToInt(i -> i).toArray());
            tag.put("BrainMemory", brainMemory);
            // 保存
            tag.put("ArmorItems", armorItems);
            tag.put("HandItems", handItems);
            tag.put("CuriosBackItem", curiosBackItem);
        }

        public void load(CompoundTag tag) {
            trust = tag.getInt("Trust");
            int[] rewards = tag.getIntArray("ClaimedRewards");
            for (int id : rewards) claimedRewards.add(id);
            if (tag.contains("BrainMemory")) {
                brainMemory = tag.getCompound("BrainMemory");
            }

            // 读取
            if (tag.contains("ArmorItems", 9)) armorItems = tag.getList("ArmorItems", 10);
            if (tag.contains("HandItems", 9)) handItems = tag.getList("HandItems", 10);
            if (tag.contains("CuriosBackItem", 10)) curiosBackItem = tag.getCompound("CuriosBackItem");
        }
    }

    // [重构] 存储所有玩家的档案
    private final Map<UUID, PlayerProfile> playerProfiles = new HashMap<>();

    private long respawnReadyTime = 0;
    // [修改] 废弃 useHerobrineSkin，改为 skinVariant
    // private boolean useHerobrineSkin = true;
    private int skinVariant = 0; // 0 = Herobrine, 1 = Hero, ...
    private String customSkinName = ""; // 自定义皮肤名称

    private CompoundTag tempBrainData = null;

    // [新增] 存储当前活跃的 Hero 实体 UUID
    private UUID activeHeroUUID = null;

    // [新增] 记录 Hero 最后已知的位置 (用于 SourceFlowItem 跨维度定位)
    private GlobalPos lastKnownHeroPos = null;

    // [新增] 记录是否已经通过 hb 指令召唤过
    private boolean hasSpawnedFromChat = false;

    // [1.21.1 改动] 添加 HolderLookup.Provider 参数
    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider provider) {
        compound.putLong("RespawnReadyTime", this.respawnReadyTime);
        compound.putInt("SkinVariant", this.skinVariant);
        compound.putString("CustomSkinName", this.customSkinName);
        if (this.tempBrainData != null) {
            compound.put("TempBrainData", this.tempBrainData);
        }
        if (this.activeHeroUUID != null) {
            compound.putUUID("ActiveHeroUUID", this.activeHeroUUID);
        }
        if (this.lastKnownHeroPos != null) {
            compound.put("LastKnownHeroPos", writeGlobalPos(this.lastKnownHeroPos));
        }

        // [新增] 保存聊天指令召唤状态
        compound.putBoolean("HasSpawnedFromChat", this.hasSpawnedFromChat);

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

    // [1.21.1 改动] 添加 HolderLookup.Provider 参数
    public static HeroWorldData load(CompoundTag compound, HolderLookup.Provider provider) {
        HeroWorldData data = new HeroWorldData();
        if (compound.contains("RespawnReadyTime")) {
            data.respawnReadyTime = compound.getLong("RespawnReadyTime");
        }
        if (compound.contains("SkinVariant")) {
            data.skinVariant = compound.getInt("SkinVariant");
        } else if (compound.contains("UseHerobrineSkin")) {
            // 兼容旧数据
            data.skinVariant = compound.getBoolean("UseHerobrineSkin") ? 0 : 1;
        }

        if (compound.contains("CustomSkinName")) {
            data.customSkinName = compound.getString("CustomSkinName");
        }

        if (compound.contains("TempBrainData")) {
            data.tempBrainData = compound.getCompound("TempBrainData");
        }
        if (compound.hasUUID("ActiveHeroUUID")) {
            data.activeHeroUUID = compound.getUUID("ActiveHeroUUID");
        }
        if (compound.contains("LastKnownHeroPos")) {
            data.lastKnownHeroPos = readGlobalPos(compound.getCompound("LastKnownHeroPos"));
        }

        // [新增] 读取聊天指令召唤状态
        if (compound.contains("HasSpawnedFromChat")) {
            data.hasSpawnedFromChat = compound.getBoolean("HasSpawnedFromChat");
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

    // [1.21.1 修复] 弃用不稳定的 NbtUtils BlockPos，改用直接存取 XYZ 坐标
    private static CompoundTag writeGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        CompoundTag posTag = new CompoundTag();
        posTag.putInt("X", pos.pos().getX());
        posTag.putInt("Y", pos.pos().getY());
        posTag.putInt("Z", pos.pos().getZ());
        tag.put("Pos", posTag);
        return tag;
    }

    private static GlobalPos readGlobalPos(CompoundTag tag) {
        try {
            // [1.21.1 改动] 使用 ResourceLocation.parse()
            ResourceLocation dimLoc = ResourceLocation.parse(tag.getString("Dimension"));
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            CompoundTag posTag = tag.getCompound("Pos");
            BlockPos blockPos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
            return GlobalPos.of(dimKey, blockPos);
        } catch (Exception e) {
            return null;
        }
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

    // 兼容旧方法
    public int getSkinVariant() {
        return skinVariant;
    }

    public void setSkinVariant(int variant) {
        this.skinVariant = variant;
        this.setDirty();
    }

    public String getCustomSkinName() {
        return customSkinName;
    }

    public void setCustomSkinName(String name) {
        this.customSkinName = name;
        this.setDirty();
    }

    public CompoundTag getTempBrainData() {
        return this.tempBrainData;
    }

    public void setTempBrainData(CompoundTag data) {
        this.tempBrainData = data;
        this.setDirty();
    }

    public UUID getActiveHeroUUID() {
        return this.activeHeroUUID;
    }

    public void setActiveHeroUUID(UUID uuid) {
        this.activeHeroUUID = uuid;
        this.setDirty();
    }

    public GlobalPos getLastKnownHeroPos() {
        return this.lastKnownHeroPos;
    }

    public void setLastKnownHeroPos(GlobalPos pos) {
        this.lastKnownHeroPos = pos;
        this.setDirty();
    }

    // [新增] 装备存取
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

    // [新增] 聊天指令召唤状态的存取
    public boolean hasSpawnedFromChat() {
        return this.hasSpawnedFromChat;
    }

    public void setSpawnedFromChat(boolean spawned) {
        this.hasSpawnedFromChat = spawned;
        this.setDirty(); // 必须调用，通知游戏数据已更改需要保存
    }

    // [1.21.1 改动] 使用 FACTORY 注册获取 Data
    public static HeroWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(
                FACTORY,
                "herobrine_companion_data"
        );
    }
}