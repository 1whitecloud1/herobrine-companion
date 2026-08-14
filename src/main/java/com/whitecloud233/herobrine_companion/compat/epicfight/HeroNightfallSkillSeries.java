package com.whitecloud233.herobrine_companion.compat.epicfight;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;

import java.util.List;

/**
 * 一条技能系列：一次"可被怪物 AI 选中的招式"（权重/冷却/距离/概率 + 招式动画 + 触发特效）。
 * {@link Trigger} 声明它的触发条件类型，构建层据此选择判定谓词——
 * 这是把夜幕"输入驱动分支"翻译成怪物侧条件的关键点。
 */
record HeroNightfallSkillSeries(
        float weight,
        int cooldown,
        double minDistance,
        double maxDistance,
        float chance,
        List<HeroNightfallAnimationField> animations,
        HeroNightfallSkillSpec effectSpec,
        int actionState,
        boolean anchorToWeaponJoint,
        boolean airAttack,
        Trigger trigger
) {
    enum Trigger {
        /** 通用技能：距离/朝向/前置动作满足即可，等效夜幕的"技能键"分支 */
        DEFAULT,
        /** 反击：目标正在攻击时触发，等效夜幕的"招架成功"分支 */
        COUNTER,
        /** 血疫收割：目标身上血疫层数达标时触发，等效夜幕赤月的"叠层→引爆" */
        BLOOD_HARVEST
    }

    boolean matches(AssetAccessor<? extends StaticAnimation> currentAnimation) {
        for (HeroNightfallAnimationField animation : this.animations) {
            if (animation.matches(currentAnimation)) {
                return true;
            }
        }
        return false;
    }
}
