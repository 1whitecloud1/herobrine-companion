package com.whitecloud233.herobrine_companion.entity.family;

import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedPlayerMemory;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

public final class HerobrineFamilyRelations {
    private static final int BASE_RELATION_SIMMONS = 46;
    private static final int BASE_RELATION_JEAN = 54;
    private static final int CEASEFIRE_TICKS_SIMMONS = 20 * 150;
    private static final int CEASEFIRE_TICKS_JEAN = 20 * 180;

    private HerobrineFamilyRelations() {
    }

    public static void bootstrapOwnerBond(Mob mob, HerobrineFamilyMemberType type, int heroTrust, UUID playerId) {
        if (mob == null || type == null || playerId == null || !(mob instanceof AwakenedMobAccessor accessor)) {
            return;
        }

        long now = mob.level().getGameTime();
        AwakenedPlayerMemory memory = accessor.herobrineCompanion$getOrCreatePlayerMemory(playerId);
        memory.touchSeen(now);
        memory.adjustRelation(computeInitialRelation(type, heroTrust));
        memory.grantCeasefire(now, type == HerobrineFamilyMemberType.JEAN ? CEASEFIRE_TICKS_JEAN : CEASEFIRE_TICKS_SIMMONS);
        memory.setNextReminderGameTime(now + 320L);
        memory.setNextRequestGameTime(now + 2000L);
    }

    private static int computeInitialRelation(HerobrineFamilyMemberType type, int heroTrust) {
        int baseRelation = type == HerobrineFamilyMemberType.JEAN ? BASE_RELATION_JEAN : BASE_RELATION_SIMMONS;
        int trustBonus = Math.max(0, heroTrust - type.requiredTrust()) / 2;
        return Math.min(90, baseRelation + trustBonus);
    }
}
