package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class HeroWorldDataChecks {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static int checks;

    public static void main(String[] args) {
        Map<UUID, HeroWorldData.PlayerProfile> profiles = new HashMap<>();
        HeroWorldData data = new HeroWorldData(id -> profiles.computeIfAbsent(id, ignored -> new HeroWorldData.PlayerProfile()),
                new HeroWorldData.GlobalData());
        data.setTrust(OWNER, 50);
        HeroWorldData.PlayerProfile profile = profiles.get(OWNER);
        check(profile.isDirty(), "Changed trust must dirty profile");
        profile.setDirty(false);
        data.setTrust(OWNER, 50);
        check(!profile.isDirty(), "Equal trust must not dirty profile");

        compound(data, profile, data::getBrainMemory, data::setBrainMemory);
        compound(data, profile, data::getHeroMemory, data::setHeroMemory);
        compound(data, profile, data::getCuriosBackItem, data::setCuriosBackItem);
        compound(data, profile, data::getAccessoriesData, data::setAccessoriesData);
        compound(data, profile, data::getPoseData, data::setPoseData);
        compound(data, profile, data::getGiftProfile, data::setGiftProfile);

        ListTag armor = slots(4, "minecraft:diamond_boots");
        ListTag hands = slots(2, "minecraft:diamond_sword");
        CompoundTag curios = nested();
        CompoundTag accessories = nested();
        data.setEquipmentSnapshot(OWNER, armor, hands, curios, accessories);
        profile.setDirty(false);
        data.setEquipmentSnapshot(OWNER, armor.copy(), hands.copy(), curios.copy(), accessories.copy());
        check(!profile.isDirty(), "Equal equipment snapshot must not dirty profile");
        armor.getCompound(0).putString("id", "corrupt");
        hands.clear();
        curios.getCompound("nested").putInt("value", 99);
        accessories.getCompound("nested").putInt("value", 99);
        check(data.getArmorItems(OWNER).getCompound(0).getString("id").equals("minecraft:diamond_boots"), "Armor setter aliases input");
        check(data.getHandItems(OWNER).size() == 2, "Hand setter aliases input");
        check(data.getCuriosBackItem(OWNER).getCompound("nested").getInt("value") == 1, "Curios snapshot aliases input");
        check(data.getAccessoriesData(OWNER).getCompound("nested").getInt("value") == 1, "Accessory snapshot aliases input");
        data.getArmorItems(OWNER).clear();
        data.getHandItems(OWNER).getCompound(0).putString("id", "corrupt");
        check(data.getArmorItems(OWNER).size() == 4, "Armor getter exposes storage");
        check(data.getHandItems(OWNER).getCompound(0).getString("id").equals("minecraft:diamond_sword"), "Hands getter exposes storage");

        data.setEquipmentSnapshot(OWNER, slots(4, null), slots(2, null), new CompoundTag(), new CompoundTag());
        check(data.getArmorItems(OWNER).getCompound(0).isEmpty(), "Cleared armor must replace old items");
        check(data.getHandItems(OWNER).getCompound(0).isEmpty(), "Cleared hands must replace old items");
        check(data.getCuriosBackItem(OWNER).isEmpty() && data.getAccessoriesData(OWNER).isEmpty(), "Cleared accessories must replace old items");
        profile.setDirty(false);
        data.setEquipmentSnapshot(OWNER, slots(4, null), slots(2, null), new CompoundTag(), new CompoundTag());
        check(!profile.isDirty(), "Equal empty snapshot must not dirty profile");

        byte[] skin = {1, 2, 3};
        data.setCustomSkinData(OWNER, skin);
        skin[0] = 9;
        data.getCustomSkinData(OWNER)[1] = 9;
        check(data.getCustomSkinData(OWNER)[0] == 1 && data.getCustomSkinData(OWNER)[1] == 2, "Skin bytes expose mutable storage");
        profile.setDirty(false);
        data.setCustomSkinData(OWNER, new byte[]{1, 2, 3});
        check(!profile.isDirty(), "Equal skin bytes must not dirty profile");
        data.addClaimedReward(OWNER, 7);
        Set<Integer> rewards = data.getClaimedRewards(OWNER);
        try { rewards.add(8); } catch (UnsupportedOperationException expected) { }
        check(!data.isRewardClaimed(OWNER, 8), "Reward getter exposes mutable storage");
        profile.setDirty(false);
        data.addClaimedReward(OWNER, 7);
        check(!profile.isDirty(), "Duplicate reward must not dirty profile");
        data.setRewardClaimed(OWNER, 7, false);
        check(!data.isRewardClaimed(OWNER, 7) && rewards.contains(7), "Rewards must be detached snapshots");
        CompoundTag temporary = nested();
        data.setTempBrainData(OWNER, temporary);
        temporary.getCompound("nested").putInt("value", 3);
        data.getTempBrainData(OWNER).getCompound("nested").putInt("value", 4);
        check(data.getTempBrainData(OWNER).getCompound("nested").getInt("value") == 1, "Temporary memory aliases input/output");
        data.setTempBrainData(OWNER, null);
        check(data.getTempBrainData(OWNER) == null, "Missing temporary memory must remain null");

        data.setHeroMemory(OWNER, nested());
        data.setGiftProfile(OWNER, nested());
        data.setBirthdayCakeRewardTick(OWNER, 4321);
        data.setSkinVariant(OWNER, 999);
        data.setCustomSkinName(OWNER, "custom");
        data.setActiveHeroUUID(OWNER, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        data.setClientLanguageCode(OWNER, " ZH-CN ");
        data.setAutoHbTurnLimit(OWNER, 100);
        check(data.getClientLanguageCode(OWNER).equals("zh_cn") && data.getAutoHbTurnLimit(OWNER) == 20, "Configuration normalization changed");
        CompoundTag serialized = profile.save(new CompoundTag());
        CompoundTag stable = serialized.copy();
        serialized.getCompound("HeroMemory").getCompound("nested").putInt("value", 90);
        serialized.getByteArray("CustomSkinData")[0] = 90;
        check(profile.save(new CompoundTag()).equals(stable), "Profile save exposes mutable storage");
        HeroWorldData.PlayerProfile loaded = HeroWorldData.PlayerProfile.load(stable);
        CompoundTag loadedStable = loaded.save(new CompoundTag());
        stable.getCompound("GiftProfile").getCompound("nested").putInt("value", 90);
        stable.getByteArray("CustomSkinData")[0] = 90;
        check(loaded.save(new CompoundTag()).equals(loadedStable), "Profile load aliases input");
        check(loadedStable.getCompound("HeroMemory").getCompound("nested").getInt("value") == 1
                && loadedStable.getLong("BirthdayCakeRewardTick") == 4321, "Profile round trip dropped fields");

        CompoundTag legacy = new CompoundTag();
        ListTag oldProfiles = new ListTag();
        CompoundTag oldProfile = loadedStable.copy();
        oldProfile.putUUID("UUID", OWNER);
        oldProfiles.add(oldProfile);
        oldProfiles.add(new CompoundTag());
        legacy.put("PlayerProfiles", oldProfiles);
        Map<UUID, HeroWorldData.PlayerProfile> migrated = new HashMap<>();
        HeroWorldData.migrateProfiles(legacy, migrated::get, migrated::put);
        check(migrated.size() == 1 && migrated.get(OWNER).isDirty(), "Migration must skip missing UUID and dirty new profile");
        check(migrated.get(OWNER).save(new CompoundTag()).equals(loadedStable), "Migration must preserve every current profile field");
        oldProfile.putInt("Trust", 999);
        HeroWorldData.migrateProfiles(legacy, migrated::get, migrated::put);
        check(migrated.get(OWNER).save(new CompoundTag()).getInt("Trust") == 50, "Migration retry must not overwrite independent profile");
        check(data.getTrust(null) == 0 && data.getTempBrainData(null) == null, "Null owner defaults changed");
        System.out.println("HeroWorldDataChecks: " + checks + " checks passed");
    }

    private static void compound(HeroWorldData data, HeroWorldData.PlayerProfile profile,
                                 Function<UUID, CompoundTag> getter, BiConsumer<UUID, CompoundTag> setter) {
        CompoundTag input = nested();
        setter.accept(OWNER, input);
        profile.setDirty(false);
        setter.accept(OWNER, input.copy());
        check(!profile.isDirty(), "Equal NBT must not dirty profile");
        input.getCompound("nested").putInt("value", 2);
        getter.apply(OWNER).getCompound("nested").putInt("value", 3);
        check(getter.apply(OWNER).getCompound("nested").getInt("value") == 1, "NBT boundary must deep copy");
        setter.accept(OWNER, null);
        check(getter.apply(OWNER).isEmpty(), "Null NBT must normalize to empty");
    }

    private static CompoundTag nested() {
        CompoundTag tag = new CompoundTag();
        CompoundTag child = new CompoundTag();
        child.putInt("value", 1);
        tag.put("nested", child);
        return tag;
    }

    private static ListTag slots(int count, String firstItem) {
        ListTag list = new ListTag();
        for (int i = 0; i < count; i++) {
            CompoundTag tag = new CompoundTag();
            if (i == 0 && firstItem != null) {
                tag.putString("id", firstItem);
                tag.putByte("Count", (byte) 1);
            }
            list.add(tag);
        }
        return list;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
