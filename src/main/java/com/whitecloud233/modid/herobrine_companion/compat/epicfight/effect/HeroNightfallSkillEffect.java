package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 夜幕技能特效：单个 {@link HeroNightfallSkillSpec.EffectType} 对应一个实现类。
 * 接口放领域包，实现各自隔离外部依赖；{@code HeroNightfallSkillEffects} 只做 EffectType -> 实现的工厂映射。
 */
public interface HeroNightfallSkillEffect {
    /**
     * @param patch  Hero 的 Epic Fight mob patch（可能为 null，用于武器关节对齐）
     * @param hero   Hero 本体
     * @param center 特效中心点（已由门面按武器关节/目标/视线解析）
     * @param spec   特效规格（触发时间/额外伤害/半径）
     */
    void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec);
}
