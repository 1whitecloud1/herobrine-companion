package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.util.NbtKeys;

import com.whitecloud233.herobrine_companion.fight.ChallengeSession;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.fight.HeroChallengeState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.UUID;

public final class HeroEntityPersistence {
    private HeroEntityPersistence() {
    }

    public static void save(HeroEntity hero, CompoundTag compound) {
        compound.putInt("TrustLevel", hero.getTrustLevel());
        compound.putInt("PatrolTimer", hero.patrolTimer);
        compound.putBoolean("CompanionMode", hero.isCompanionMode());
        compound.putBoolean("BattleModeActive", hero.isBattleModeActive());
        compound.putInt("SkinVariant", hero.getSkinVariant());
        compound.putString("CustomSkinName", hero.getCustomSkinName());
        UUID owner = hero.getOwnerUUID();
        if (owner != null) {
            compound.putUUID("OwnerUUID", owner);
            compound.putString("OwnerUUID_String", owner.toString());
        }
        HeroDataHandler.savePoseData(hero, compound);
        // saveWithoutId/load 重建路径也必须携带第三方饰品。
        compound.put("CuriosBackItem", hero.getCuriosBackItemTag());
        compound.put("AccessoriesData", hero.getAccessoriesDataTag());
        hero.getHeroAgent().saveTasks(compound);
    }

    public static void load(HeroEntity hero, CompoundTag compound) {
        HeroChallengeState.onRestoreFromDisk(hero);

        if (compound.contains("TrustLevel")) hero.setTrustLevel(compound.getInt("TrustLevel"));
        if (compound.contains("PatrolTimer")) hero.patrolTimer = compound.getInt("PatrolTimer");
        if (compound.contains("BattleModeActive")) hero.setBattleModeActive(compound.getBoolean("BattleModeActive"));
        if (compound.contains("CompanionMode")) hero.setCompanionMode(compound.getBoolean("CompanionMode"));
        if (ChallengeSession.isHeroInChallenge(hero)) {
            hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
            // 恢复同步通道中的进度 tick，确保客户端动画同步。
            int savedTicks = hero.getPersistentData().getInt(NbtKeys.HERO_CHALLENGE_PHASE_TICKS);
            hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, savedTicks);
        }

        // 恢复本地快照时不经 setter 回写全局皮肤，避免覆盖玩家资料。
        hero.getEntityData().set(HeroEntity.SKIN_VARIANT, readSkinVariant(compound));
        if (compound.contains("CustomSkinName")) {
            hero.getEntityData().set(HeroEntity.CUSTOM_SKIN_NAME, compound.getString("CustomSkinName"));
        }
        UUID owner = readOwnerUUID(compound);
        if (owner != null) hero.setOwnerUUID(owner);
        HeroDataHandler.loadPoseData(hero, compound);
        if (compound.contains("CuriosBackItem", Tag.TAG_COMPOUND)) {
            hero.setCuriosBackItemFromTag(compound.getCompound("CuriosBackItem").copy());
        }
        if (compound.contains("AccessoriesData", Tag.TAG_COMPOUND)) {
            hero.setAccessoriesDataFromTag(compound.getCompound("AccessoriesData").copy());
        }
        hero.getPersistenceState().loaded(hasCompleteEquipment(compound));
        hero.getHeroAgent().loadTasks(compound);
    }

    static int readSkinVariant(CompoundTag compound) {
        if (compound.contains("SkinVariant")) return compound.getInt("SkinVariant");
        if (compound.contains("UseHerobrineSkin")) {
            return compound.getBoolean("UseHerobrineSkin") ? HeroEntity.SKIN_HEROBRINE : HeroEntity.SKIN_HERO;
        }
        return HeroEntity.DEFAULT_SKIN_VARIANT;
    }

    @Nullable
    static UUID readOwnerUUID(CompoundTag compound) {
        if (compound.hasUUID("OwnerUUID")) return compound.getUUID("OwnerUUID");
        if (compound.contains("OwnerUUID_String")) {
            try {
                return UUID.fromString(compound.getString("OwnerUUID_String"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    static boolean hasCompleteEquipment(CompoundTag compound) {
        return compound.getList("ArmorItems", Tag.TAG_COMPOUND).size() == 4
                && compound.getList("HandItems", Tag.TAG_COMPOUND).size() == 2;
    }
}
