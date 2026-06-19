package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

public class HeroCombatProtection {

    public static boolean shouldPreventFriendlyFire(HeroEntity hero, @Nullable Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return false;
        }
        if (living == hero || living instanceof HeroEntity) {
            return true;
        }
        if (!hasCompanionFriendlyFireProtection(hero)) {
            return false;
        }
        if (living instanceof Player) {
            return true;
        }
        if (hero.isAlliedTo(living) || living.isAlliedTo(hero)) {
            return true;
        }

        UUID ownerUUID = hero.getOwnerUUID();
        if (ownerUUID != null && ownerUUID.equals(living.getUUID())) {
            return true;
        }

        Player owner = hero.getOwnerPlayer();
        return owner != null && (living.is(owner) || living.isAlliedTo(owner) || owner.isAlliedTo(living));
    }

    public static boolean hasCompanionFriendlyFireProtection(HeroEntity hero) {
        return hero.getOwnerUUID() != null && !hero.isChallengeActiveState();
    }

    public static void handleNonChallengeRetaliation(HeroEntity hero, DamageSource source) {
        if (hero.level().isClientSide || !hero.isBattleModeActive() || source == null) {
            return;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker
                && com.whitecloud233.herobrine_companion.entity.ai.goal.HeroBattleStanceGoal.canHeroAttackTarget(hero, livingAttacker)) {
            hero.setLastHurtByMob(livingAttacker);
            if (hero.getTarget() != livingAttacker) {
                hero.setTarget(livingAttacker);
            }
        }
    }
}
