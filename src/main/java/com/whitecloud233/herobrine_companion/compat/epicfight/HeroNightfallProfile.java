package com.whitecloud233.herobrine_companion.compat.epicfight;

import yesman.epicfight.api.animation.LivingMotion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 夜幕武器 -> 连招/技能档案。纯数据记录：只描述"这把武器能打哪些招式"，
 * 不参与行为构建或判定。
 *
 * @param comboEffect 连段普攻命中时的特效（如赤月普攻叠血疫）；大多数武器为 null。
 */
record HeroNightfallProfile(
        Set<String> itemPaths,
        double attackRadius,
        boolean combatSafe,
        Map<LivingMotion, HeroNightfallAnimationField> livingOverrides,
        List<HeroNightfallAnimationField> comboAnimations,
        List<HeroNightfallSkillSeries> skillSeries,
        HeroNightfallSkillSpec skillEffect,
        HeroNightfallSkillSpec comboEffect
) {
}
