package com.whitecloud233.herobrine_companion.entity.family;

import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

public final class HerobrineFamilyMembers {
    private static final String FAMILY_MEMBER_ID_TAG = "HerobrineFamilyMemberId";

    private HerobrineFamilyMembers() {
    }

    public static HerobrineFamilyMemberType getType(Mob mob) {
        if (mob == null) {
            return null;
        }

        CompoundTag persistentData = mob.getPersistentData();
        if (!persistentData.contains(FAMILY_MEMBER_ID_TAG)) {
            return null;
        }
        return HerobrineFamilyMemberType.byId(persistentData.getString(FAMILY_MEMBER_ID_TAG));
    }

    public static boolean isFamilyMember(Mob mob) {
        return getType(mob) != null;
    }

    public static boolean isFamilyMember(Mob mob, HerobrineFamilyMemberType type) {
        return type != null && type == getType(mob);
    }

    public static String getForcedName(Mob mob) {
        HerobrineFamilyMemberType type = getType(mob);
        return type == null ? null : type.id();
    }

    public static String getOriginLore(Mob mob) {
        HerobrineFamilyMemberType type = getType(mob);
        if (type == HerobrineFamilyMemberType.SIMMONS) {
            return "You are simmons, a Wither personally created by Herobrine. Herobrine is your father.";
        }
        if (type == HerobrineFamilyMemberType.JEAN) {
            return "You are jean, an Ender Dragon personally created by Herobrine. Herobrine is your father, and you are his daughter.";
        }
        return null;
    }

    public static boolean ensureAwakenedIdentity(Mob mob) {
        HerobrineFamilyMemberType type = getType(mob);
        if (mob == null || type == null) {
            return false;
        }

        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal(type.id()));

        if (mob instanceof AwakenedMobAccessor accessor) {
            accessor.herobrineCompanion$setAwakeningInitialized(true);
            accessor.herobrineCompanion$setAwakenedMob(true);
            accessor.herobrineCompanion$setAwakenedMobName(type.id());

            long now = mob.level().getGameTime();
            if (accessor.herobrineCompanion$getNextAmbientSpeechGameTime() <= now) {
                accessor.herobrineCompanion$setNextAmbientSpeechGameTime(now + 160L);
            }
            if (accessor.herobrineCompanion$getNextHeroInteractionGameTime() <= now) {
                accessor.herobrineCompanion$setNextHeroInteractionGameTime(now + 220L);
            }
            if (accessor.herobrineCompanion$getNextPlayerInteractionGameTime() <= now) {
                accessor.herobrineCompanion$setNextPlayerInteractionGameTime(now + 80L);
            }
        }

        return true;
    }

    public static void assignSummonedIdentity(Mob mob, HerobrineFamilyMemberType type) {
        if (mob == null || type == null) {
            return;
        }

        mob.getPersistentData().putString(FAMILY_MEMBER_ID_TAG, type.id());
        mob.setPersistenceRequired();
        mob.setTarget(null);
        ensureAwakenedIdentity(mob);
    }
}
