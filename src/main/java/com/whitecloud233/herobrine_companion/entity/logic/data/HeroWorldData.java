package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.util.LanguageCodes;

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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** 玩家档案统一保存在主世界；调用方只能通过受控方法更新，不持有存档的可变成员。 */
public class HeroWorldData {
    public static final int DEFAULT_AUTO_HB_TURN_LIMIT = 8;
    public static final int MIN_AUTO_HB_TURN_LIMIT = 1;
    public static final int MAX_AUTO_HB_TURN_LIMIT = 20;

    public static class PlayerProfile extends SavedData {
        private int trust;
        private final Set<Integer> claimedRewards = new HashSet<>();
        private CompoundTag brainMemory = new CompoundTag();
        private CompoundTag heroMemory = new CompoundTag();
        private ListTag armorItems = new ListTag();
        private ListTag handItems = new ListTag();
        private CompoundTag curiosBackItem = new CompoundTag();
        private CompoundTag accessoriesData = new CompoundTag();
        private CompoundTag poseData = new CompoundTag();
        private CompoundTag giftProfile = new CompoundTag();
        private long birthdayCakeRewardTick;
        private UUID activeHeroUUID;
        private GlobalPos lastKnownHeroPos;
        private boolean hasSpawnedFromChat;
        private int skinVariant = com.whitecloud233.herobrine_companion.entity.HeroEntity.DEFAULT_SKIN_VARIANT;
        private String customSkinName = "";
        private byte[] customSkinData = new byte[0];
        private CompoundTag tempBrainData;
        private long respawnReadyTime;
        private boolean allowIncomingCrossChat = true;
        private String clientLanguageCode = "en_us";
        private int autoHbTurnLimit = DEFAULT_AUTO_HB_TURN_LIMIT;


        // 1.21.1：必须显式传入 HolderLookup.Provider
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putInt("Trust", trust);
            tag.putIntArray("ClaimedRewards", claimedRewards.stream().mapToInt(i -> i).toArray());
            tag.put("BrainMemory", brainMemory.copy());
            tag.put("HeroMemory", heroMemory.copy());
            tag.put("ArmorItems", armorItems.copy());
            tag.put("HandItems", handItems.copy());
            tag.put("CuriosBackItem", curiosBackItem.copy());
            tag.put("AccessoriesData", accessoriesData.copy());
            tag.put("PoseData", poseData.copy());
            tag.put("GiftProfile", giftProfile.copy());
            tag.putLong("BirthdayCakeRewardTick", birthdayCakeRewardTick);
            if (activeHeroUUID != null) tag.putUUID("ActiveHeroUUID", activeHeroUUID);
            if (lastKnownHeroPos != null) tag.put("LastKnownHeroPos", writeGlobalPos(lastKnownHeroPos));
            tag.putBoolean("HasSpawnedFromChat", hasSpawnedFromChat);
            tag.putInt("SkinVariant", skinVariant);
            tag.putString("CustomSkinName", customSkinName);
            tag.putByteArray("CustomSkinData", customSkinData.clone());
            if (tempBrainData != null) tag.put("TempBrainData", tempBrainData.copy());
            tag.putLong("RespawnReadyTime", respawnReadyTime);
            tag.putBoolean("AllowIncomingCrossChat", allowIncomingCrossChat);
            tag.putString("ClientLanguageCode", LanguageCodes.normalize(clientLanguageCode));
            tag.putInt("AutoHbTurnLimit", normalizeAutoHbTurnLimit(autoHbTurnLimit));
            return tag;
        }

        // 1.21.1：Load 方法也需要增加 Provider 参数以匹配 Factory
        public static PlayerProfile load(CompoundTag tag, HolderLookup.Provider provider) {
            PlayerProfile profile = new PlayerProfile();
            profile.trust = tag.getInt("Trust");
            for (int id : tag.getIntArray("ClaimedRewards")) profile.claimedRewards.add(id);
            profile.brainMemory = tag.getCompound("BrainMemory").copy();
            profile.heroMemory = tag.getCompound("HeroMemory").copy();
            profile.armorItems = tag.getList("ArmorItems", Tag.TAG_COMPOUND).copy();
            profile.handItems = tag.getList("HandItems", Tag.TAG_COMPOUND).copy();
            profile.curiosBackItem = tag.getCompound("CuriosBackItem").copy();
            profile.accessoriesData = tag.getCompound("AccessoriesData").copy();
            profile.poseData = tag.getCompound("PoseData").copy();
            profile.giftProfile = tag.getCompound("GiftProfile").copy();
            profile.birthdayCakeRewardTick = Math.max(0L, tag.getLong("BirthdayCakeRewardTick"));
            if (tag.hasUUID("ActiveHeroUUID")) profile.activeHeroUUID = tag.getUUID("ActiveHeroUUID");
            if (tag.contains("LastKnownHeroPos")) profile.lastKnownHeroPos = readGlobalPos(tag.getCompound("LastKnownHeroPos"));
            profile.hasSpawnedFromChat = tag.getBoolean("HasSpawnedFromChat");
            profile.skinVariant = tag.getInt("SkinVariant");
            profile.customSkinName = tag.getString("CustomSkinName");
            profile.customSkinData = tag.getByteArray("CustomSkinData").clone();
            if (tag.contains("TempBrainData", Tag.TAG_COMPOUND)) profile.tempBrainData = tag.getCompound("TempBrainData").copy();
            profile.respawnReadyTime = tag.getLong("RespawnReadyTime");
            if (tag.contains("AllowIncomingCrossChat")) profile.allowIncomingCrossChat = tag.getBoolean("AllowIncomingCrossChat");
            if (tag.contains("AutoHbTurnLimit")) profile.autoHbTurnLimit = normalizeAutoHbTurnLimit(tag.getInt("AutoHbTurnLimit"));
            if (tag.contains("ClientLanguageCode")) profile.clientLanguageCode = LanguageCodes.normalize(tag.getString("ClientLanguageCode"));
            return profile;
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
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static class GlobalData extends SavedData {
        private UUID activeChallengerUUID;
        private boolean migrated;

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

    private static class LegacyData extends SavedData {
        private CompoundTag rawData;
        @Override public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.Provider provider) { return rawData.copy(); }
        static LegacyData load(CompoundTag tag, HolderLookup.Provider provider) {
            LegacyData data = new LegacyData();
            data.rawData = tag.copy();
            return data;
        }
    }

    private final Function<UUID, PlayerProfile> profileLookup;
    private final GlobalData globalData;

    private HeroWorldData(ServerLevel overworld) {
        // 1.21.1：使用 SavedData.Factory 包装（load 需要 HolderLookup.Provider）
        this(uuid -> overworld.getDataStorage().computeIfAbsent(
                        new SavedData.Factory<>(PlayerProfile::new, PlayerProfile::load, null), profileKey(uuid)),
                overworld.getDataStorage().computeIfAbsent(
                        new SavedData.Factory<>(GlobalData::new, GlobalData::load, null), "herobrine_companion_global"));
        migrateIfNecessary(overworld);
    }

    HeroWorldData(Function<UUID, PlayerProfile> profileLookup, GlobalData globalData) {
        this.profileLookup = Objects.requireNonNull(profileLookup);
        this.globalData = Objects.requireNonNull(globalData);
    }

    public static HeroWorldData get(ServerLevel level) {
        ServerLevel overworld = Objects.requireNonNull(level.getServer().getLevel(Level.OVERWORLD));
        return new HeroWorldData(overworld);
    }

    private static String profileKey(UUID uuid) {
        return "herobrine_companion_player_" + uuid;
    }

    private void migrateIfNecessary(ServerLevel overworld) {
        if (globalData.migrated) return;
        SavedData.Factory<LegacyData> legacyFactory = new SavedData.Factory<>(LegacyData::new, LegacyData::load, null);
        LegacyData legacy = overworld.getDataStorage().get(legacyFactory, "herobrine_companion_data");
        if (legacy != null) {
            SavedData.Factory<PlayerProfile> profileFactory = new SavedData.Factory<>(PlayerProfile::new, PlayerProfile::load, null);
            migrateProfiles(legacy.rawData,
                    uuid -> overworld.getDataStorage().get(profileFactory, profileKey(uuid)),
                    (uuid, profile) -> overworld.getDataStorage().set(profileKey(uuid), profile),
                    overworld.registryAccess());
            if (globalData.activeChallengerUUID == null && legacy.rawData.hasUUID("ActiveChallengerUUID")) {
                globalData.activeChallengerUUID = legacy.rawData.getUUID("ActiveChallengerUUID");
            }
        }
        globalData.migrated = true;
        globalData.setDirty();
    }

    static void migrateProfiles(CompoundTag legacy, Function<UUID, PlayerProfile> existing,
                                BiConsumer<UUID, PlayerProfile> store, HolderLookup.Provider provider) {
        ListTag profiles = legacy.getList("PlayerProfiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < profiles.size(); i++) {
            CompoundTag tag = profiles.getCompound(i);
            if (!tag.hasUUID("UUID")) continue;
            UUID uuid = tag.getUUID("UUID");
            // 已有独立档案优先，迁移中断后重试也不能用旧档覆盖它。
            if (existing.apply(uuid) != null) continue;
            PlayerProfile profile = PlayerProfile.load(tag, provider);
            profile.setDirty();
            store.accept(uuid, profile);
        }
    }

    private PlayerProfile getProfile(UUID uuid) {
        return uuid == null ? new PlayerProfile() : profileLookup.apply(uuid);
    }

    public UUID getActiveChallengerUUID() { return globalData.activeChallengerUUID; }
    public void setActiveChallengerUUID(UUID uuid) {
        if (Objects.equals(globalData.activeChallengerUUID, uuid)) return;
        globalData.activeChallengerUUID = uuid;
        globalData.setDirty();
    }

    public int getTrust(UUID uuid) { return getProfile(uuid).trust; }
    public void setTrust(UUID uuid, int trust) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (profile.trust == trust) return;
        profile.trust = trust;
        profile.setDirty();
    }

    public void addClaimedReward(UUID uuid, int rewardId) { setRewardClaimed(uuid, rewardId, true); }
    public Set<Integer> getClaimedRewards(UUID uuid) { return Set.copyOf(getProfile(uuid).claimedRewards); }
    public boolean isRewardClaimed(UUID uuid, int id) { return getProfile(uuid).claimedRewards.contains(id); }
    public void setRewardClaimed(UUID uuid, int id, boolean claimed) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        boolean changed = claimed ? profile.claimedRewards.add(id) : profile.claimedRewards.remove(id);
        if (changed) profile.setDirty();
    }

    private void setCompound(UUID uuid, CompoundTag value, Function<PlayerProfile, CompoundTag> getter,
                             BiConsumer<PlayerProfile, CompoundTag> setter) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        CompoundTag normalized = value == null ? new CompoundTag() : value;
        if (normalized.equals(getter.apply(profile))) return;
        setter.accept(profile, normalized.copy());
        profile.setDirty();
    }

    public CompoundTag getBrainMemory(UUID uuid) { return getProfile(uuid).brainMemory.copy(); }
    public void setBrainMemory(UUID uuid, CompoundTag memory) { setCompound(uuid, memory, p -> p.brainMemory, (p, t) -> p.brainMemory = t); }
    public CompoundTag getHeroMemory(UUID uuid) { return getProfile(uuid).heroMemory.copy(); }
    public void setHeroMemory(UUID uuid, CompoundTag memory) { setCompound(uuid, memory, p -> p.heroMemory, (p, t) -> p.heroMemory = t); }
    public ListTag getArmorItems(UUID uuid) { return getProfile(uuid).armorItems.copy(); }
    public ListTag getHandItems(UUID uuid) { return getProfile(uuid).handItems.copy(); }

    private static boolean updateEquipment(PlayerProfile profile, ListTag armor, ListTag hands) {
        ListTag normalizedArmor = armor == null ? new ListTag() : armor;
        ListTag normalizedHands = hands == null ? new ListTag() : hands;
        boolean changed = false;
        if (!profile.armorItems.equals(normalizedArmor)) {
            profile.armorItems = normalizedArmor.copy();
            changed = true;
        }
        if (!profile.handItems.equals(normalizedHands)) {
            profile.handItems = normalizedHands.copy();
            changed = true;
        }
        return changed;
    }

    public void setEquipment(UUID uuid, ListTag armor, ListTag hands) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (updateEquipment(profile, armor, hands)) profile.setDirty();
    }

    public void setEquipmentSnapshot(UUID uuid, ListTag armor, ListTag hands, CompoundTag curios, CompoundTag accessories) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        boolean changed = updateEquipment(profile, armor, hands);
        CompoundTag normalizedCurios = curios == null ? new CompoundTag() : curios;
        CompoundTag normalizedAccessories = accessories == null ? new CompoundTag() : accessories;
        if (!profile.curiosBackItem.equals(normalizedCurios)) {
            profile.curiosBackItem = normalizedCurios.copy();
            changed = true;
        }
        if (!profile.accessoriesData.equals(normalizedAccessories)) {
            profile.accessoriesData = normalizedAccessories.copy();
            changed = true;
        }
        if (changed) profile.setDirty();
    }

    public CompoundTag getCuriosBackItem(UUID uuid) { return getProfile(uuid).curiosBackItem.copy(); }
    public void setCuriosBackItem(UUID uuid, CompoundTag tag) { setCompound(uuid, tag, p -> p.curiosBackItem, (p, t) -> p.curiosBackItem = t); }
    public CompoundTag getAccessoriesData(UUID uuid) { return getProfile(uuid).accessoriesData.copy(); }
    public void setAccessoriesData(UUID uuid, CompoundTag tag) { setCompound(uuid, tag, p -> p.accessoriesData, (p, t) -> p.accessoriesData = t); }
    public CompoundTag getPoseData(UUID uuid) { return getProfile(uuid).poseData.copy(); }
    public void setPoseData(UUID uuid, CompoundTag tag) { setCompound(uuid, tag, p -> p.poseData, (p, t) -> p.poseData = t); }
    public CompoundTag getGiftProfile(UUID uuid) { return getProfile(uuid).giftProfile.copy(); }
    public void setGiftProfile(UUID uuid, CompoundTag tag) { setCompound(uuid, tag, p -> p.giftProfile, (p, t) -> p.giftProfile = t); }

    public long getBirthdayCakeRewardTick(UUID uuid) { return getProfile(uuid).birthdayCakeRewardTick; }
    public void setBirthdayCakeRewardTick(UUID uuid, long tick) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        long normalized = Math.max(0L, tick);
        if (profile.birthdayCakeRewardTick == normalized) return;
        profile.birthdayCakeRewardTick = normalized;
        profile.setDirty();
    }

    public void setRespawnCooldown(UUID uuid, ServerLevel level, int minutes) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        long readyTime = level.getGameTime() + (long) minutes * 60 * 20;
        if (profile.respawnReadyTime == readyTime) return;
        profile.respawnReadyTime = readyTime;
        profile.setDirty();
    }

    public int getSkinVariant(UUID uuid) { return getProfile(uuid).skinVariant; }
    public void setSkinVariant(UUID uuid, int variant) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (profile.skinVariant == variant) return;
        profile.skinVariant = variant;
        profile.setDirty();
    }

    public String getCustomSkinName(UUID uuid) { return getProfile(uuid).customSkinName; }
    public void setCustomSkinName(UUID uuid, String name) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        String normalized = name == null ? "" : name;
        if (profile.customSkinName.equals(normalized)) return;
        profile.customSkinName = normalized;
        profile.setDirty();
    }

    public byte[] getCustomSkinData(UUID uuid) { return getProfile(uuid).customSkinData.clone(); }
    public void setCustomSkinData(UUID uuid, byte[] data) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        byte[] normalized = data == null ? new byte[0] : data;
        if (Arrays.equals(profile.customSkinData, normalized)) return;
        profile.customSkinData = normalized.clone();
        profile.setDirty();
    }

    public CompoundTag getTempBrainData(UUID uuid) {
        CompoundTag data = getProfile(uuid).tempBrainData;
        return data == null ? null : data.copy();
    }
    public void setTempBrainData(UUID uuid, CompoundTag data) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (Objects.equals(profile.tempBrainData, data)) return;
        profile.tempBrainData = data == null ? null : data.copy();
        profile.setDirty();
    }

    public UUID getActiveHeroUUID(UUID uuid) { return getProfile(uuid).activeHeroUUID; }
    public void setActiveHeroUUID(UUID uuid, UUID heroId) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (Objects.equals(profile.activeHeroUUID, heroId)) return;
        profile.activeHeroUUID = heroId;
        profile.setDirty();
    }

    public GlobalPos getLastKnownHeroPos(UUID uuid) { return getProfile(uuid).lastKnownHeroPos; }
    public void setLastKnownHeroPos(UUID uuid, GlobalPos pos) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        GlobalPos snapshot = pos == null ? null : GlobalPos.of(pos.dimension(), pos.pos().immutable());
        if (Objects.equals(profile.lastKnownHeroPos, snapshot)) return;
        profile.lastKnownHeroPos = snapshot;
        profile.setDirty();
    }

    public boolean hasSpawnedFromChat(UUID uuid) { return getProfile(uuid).hasSpawnedFromChat; }
    public void setSpawnedFromChat(UUID uuid, boolean spawned) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (profile.hasSpawnedFromChat == spawned) return;
        profile.hasSpawnedFromChat = spawned;
        profile.setDirty();
    }

    public boolean isAllowIncomingCrossChat(UUID uuid) { return getProfile(uuid).allowIncomingCrossChat; }
    public void setAllowIncomingCrossChat(UUID uuid, boolean allowed) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        if (profile.allowIncomingCrossChat == allowed) return;
        profile.allowIncomingCrossChat = allowed;
        profile.setDirty();
    }

    public String getClientLanguageCode(UUID uuid) { return getProfile(uuid).clientLanguageCode; }
    public void setClientLanguageCode(UUID uuid, String clientLanguageCode) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        String normalized = LanguageCodes.normalize(clientLanguageCode);
        if (profile.clientLanguageCode.equals(normalized)) return;
        profile.clientLanguageCode = normalized;
        profile.setDirty();
    }

    public int getAutoHbTurnLimit(UUID uuid) { return getProfile(uuid).autoHbTurnLimit; }
    public void setAutoHbTurnLimit(UUID uuid, int turnLimit) {
        if (uuid == null) return;
        PlayerProfile profile = getProfile(uuid);
        int normalized = PlayerProfile.normalizeAutoHbTurnLimit(turnLimit);
        if (profile.autoHbTurnLimit == normalized) return;
        profile.autoHbTurnLimit = normalized;
        profile.setDirty();
    }
}