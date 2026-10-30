package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroHostileTargetPolicy;
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

    /**
     * 战斗模式下的目标边界兜底：原版生物与觉醒怪物不吃 Herobrine 的任何战斗伤害。
     *
     * <p>放在事件层而不是只靠 {@code HeroEntity#canAttack}，是因为技能特效大多直接
     * {@code target.hurt(...)}，绕过了 {@code doHurtTarget} 那层 {@code canAttack} 校验。
     * 事件冒泡统一拦一次，保证「新加一个特效」不会悄悄破例。</p>
     */
    public static boolean shouldPreventProtectedBattleHit(HeroEntity hero, @Nullable Entity target) {
        return HeroHostileTargetPolicy.isProtectedFromBattle(hero, target);
    }

    public static void handleNonChallengeRetaliation(HeroEntity hero, DamageSource source) {
        if (hero.level().isClientSide || !hero.isBattleModeActive() || source == null) {
            return;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker
                && com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroBattleStanceGoal.canHeroAttackTarget(hero, livingAttacker)) {
            hero.setLastHurtByMob(livingAttacker);
            if (hero.getTarget() != livingAttacker) {
                hero.setTarget(livingAttacker);
            }
        }
    }
}
