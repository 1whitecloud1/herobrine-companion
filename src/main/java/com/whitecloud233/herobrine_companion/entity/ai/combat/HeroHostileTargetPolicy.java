package com.whitecloud233.herobrine_companion.entity.ai.combat;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 战斗模式的目标边界（单一职责：只回答「这只生物能不能被 Herobrine 打」）。
 *
 * <p>战斗模式只讨伐<b>模组添加</b>的敌对生物。原版生态一律不碰：</p>
 * <ul>
 *     <li>注册命名空间为 {@code minecraft} 的生物（僵尸、骷髅、苦力怕……）；</li>
 *     <li>被本模组点亮过的「觉醒怪物」——它们仍然是原版生物，只是多了个体意志，
 *     属于叙事内容而不是战斗模式该清掉的杂兵。</li>
 * </ul>
 *
 * <p>觉醒判定单独留一条，是因为觉醒状态与实体类型解耦：即便将来有模组生物被点亮，
 * 它也应当受保护，而不只是靠命名空间兜住。</p>
 *
 * <p>挑战模式是例外：那时 Herobrine 的对手是玩家，判定走 {@code HeroPhase1Goal}，
 * 这里的边界不介入。</p>
 */
public final class HeroHostileTargetPolicy {
    private static final String VANILLA_NAMESPACE = "minecraft";

    private HeroHostileTargetPolicy() {
    }

    /**
     * @return 该实体是否属于「战斗模式不该碰」的一类：原版生物或觉醒怪物。
     * 与 Hero 当前状态无关，供只需要「本体是否受保护」的调用方使用。
     */
    public static boolean isProtectedTarget(@Nullable Entity candidate) {
        return candidate instanceof LivingEntity living
                && (isAwakenedMob(living) || isVanillaMob(living));
    }

    /**
     * 战斗模式此刻是否应当对这只生物收手（选目标、近战、技能范围伤害共用这一条）。
     */
    public static boolean isProtectedFromBattle(@Nullable HeroEntity hero, @Nullable Entity candidate) {
        return hero != null
                && hero.isBattleModeActive()
                && !hero.isChallengeActiveState()
                && isProtectedTarget(candidate);
    }

    private static boolean isAwakenedMob(LivingEntity entity) {
        return entity instanceof AwakenedMobAccessor accessor && accessor.herobrineCompanion$isAwakenedMob();
    }

    private static boolean isVanillaMob(LivingEntity entity) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return entityId != null && VANILLA_NAMESPACE.equals(entityId.getNamespace());
    }
}
