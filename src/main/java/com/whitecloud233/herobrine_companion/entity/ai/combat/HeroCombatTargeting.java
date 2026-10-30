package com.whitecloud233.herobrine_companion.entity.ai.combat;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.NbtKeys;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * 战斗模式的目标获取（单一职责：在"能打谁"的边界内，挑出该打谁）。
 *
 * <p>近战 {@code HeroBattleStanceGoal} 与远程 {@code HeroFallbackRangedAttackGoal}
 * 此前各写了一份逐字节相同的目标获取逻辑：沿用自己的当前目标 → 在 {@code FOLLOW_RANGE}
 * 内扫描 → 清掉「臣服」标记 → 取最近的一个。两份副本一旦漂移，就会出现
 * "近战会追的怪远程不追"这类只在特定武器下复现的怪现象。</p>
 *
 * <p>「能不能打」的判定不在这里，统一走 {@link #canHeroAttackTarget}；
 * 原版生物与觉醒怪的保护规则见 {@link HeroHostileTargetPolicy}。</p>
 */
public final class HeroCombatTargeting {

    /** 目标扫描的基础半径；实际取它与 {@code FOLLOW_RANGE} 的较大者。 */
    public static final double SEARCH_RANGE = 24.0D;

    private HeroCombatTargeting() {
    }

    /**
     * 战斗模式的攻击白名单闸门：所有选目标/结算伤害的路径都收敛到这里，
     * 避免换个 Goal 就绕过「不打原版怪、不打觉醒怪」的规则。
     */
    public static boolean canHeroAttackTarget(@Nullable HeroEntity hero, @Nullable LivingEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && !candidate.isRemoved()
                && !(candidate instanceof HeroEntity)
                && !(candidate instanceof net.minecraft.world.entity.player.Player)
                && (candidate instanceof net.minecraft.world.entity.monster.Enemy
                    || candidate instanceof net.minecraft.world.entity.monster.Monster)
                && !HeroHostileTargetPolicy.isProtectedTarget(candidate)
                && (hero == null || hero.canAttack(candidate));
    }

    /** 实际扫描半径：基础半径与实体跟随范围取大。 */
    public static double searchRange(HeroEntity hero) {
        return Math.max(SEARCH_RANGE, hero.getAttributeValue(Attributes.FOLLOW_RANGE));
    }

    /**
     * 战斗模式此刻是否「正在交战」：已经有可攻击、且仍在扫描范围内的目标。
     *
     * <p>陪伴 + 战斗同时开启时的唯一优先级开关：有敌人 → 战斗 AI 拿 MOVE/LOOK，
     * 没敌人 → 交还给陪伴模式的跟随 AI。</p>
     */
    public static boolean hasEngagedTarget(@Nullable HeroEntity hero) {
        if (hero == null || !hero.isBattleModeActive()) {
            return false;
        }
        LivingEntity target = hero.getTarget();
        if (!canHeroAttackTarget(hero, target)) {
            return false;
        }
        double range = searchRange(hero);
        return hero.distanceToSqr(target) <= range * range;
    }

    /**
     * 清除生物的「臣服」标记。
     *
     * <p>被 Herobrine 压制过的生物带 {@code MOB_SUBMISSION} 标记会保持静止；
     * 一旦它进入战斗模式的目标名单，就必须先把这个标记抹掉，否则会选出
     * 一个"站着不动、打不还手"的目标。</p>
     */
    public static void clearSubmission(LivingEntity candidate) {
        if (candidate instanceof Mob mob) {
            mob.getPersistentData().putBoolean(NbtKeys.MOB_SUBMISSION, false);
            mob.getPersistentData().remove(NbtKeys.MOB_SUBMISSION_YAW);
            if (mob.isSilent()) {
                mob.setSilent(false);
            }
        }
    }

    /**
     * 沿用自己的当前目标；当前目标失效时退回 {@code fallback}。
     *
     * @param fallback Goal 自己缓存的上一个目标（可能为 null）
     */
    @Nullable
    public static LivingEntity pickBattleTarget(HeroEntity hero, @Nullable LivingEntity fallback) {
        LivingEntity currentTarget = hero.getTarget();
        return canHeroAttackTarget(hero, currentTarget) ? currentTarget : fallback;
    }

    /**
     * 找最近的可攻击目标。
     *
     * <p>先在原目标与 {@code fallback} 里找现成的（且仍在扫描半径内），避免每 tick
     * 都做一次 AABB 扫描；找不到才扫描周围。</p>
     */
    @Nullable
    public static LivingEntity findNearestHostile(HeroEntity hero, @Nullable LivingEntity fallback) {
        LivingEntity existingTarget = pickBattleTarget(hero, fallback);
        if (canHeroAttackTarget(hero, existingTarget)
                && hero.distanceToSqr(existingTarget) <= SEARCH_RANGE * SEARCH_RANGE) {
            return existingTarget;
        }

        List<LivingEntity> targets = hero.level().getEntitiesOfClass(
                LivingEntity.class,
                hero.getBoundingBox().inflate(searchRange(hero)),
                candidate -> {
                    clearSubmission(candidate);
                    return canHeroAttackTarget(hero, candidate);
                }
        );

        // 原版生物与觉醒怪已在 canHeroAttackTarget 里被排除，这里只剩模组生物，不需要再排优先级。
        return targets.stream()
                .min(Comparator.comparingDouble(hero::distanceToSqr))
                .orElse(null);
    }
}
