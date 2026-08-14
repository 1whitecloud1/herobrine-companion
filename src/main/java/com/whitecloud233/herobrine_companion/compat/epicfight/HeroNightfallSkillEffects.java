package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.compat.epicfight.effect.AbstractHeroNightfallSkillEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.ArcSlashEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.BeastRoarEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.BloodLustEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.CrimsonSlashEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.GroundBurstEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.HeroNightfallSkillEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.JudgementCutEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.LightningCallEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.MurasamaZandatsuEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.ScytheBloodHarvestEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.ScytheBloodStackEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.ScytheScarletEndEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.YamatoBlastSwordEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.YamatoDamoclesEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.YamatoHeavyRainEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.YamatoJudgementCutEndEffect;
import com.whitecloud233.herobrine_companion.compat.epicfight.effect.YamatoSummonedSwordEffect;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Map;

/**
 * 特效门面：只负责「EffectType -> 特效实现」的工厂映射 + 中心点解析。
 * 每个特效实现（{@link HeroNightfallSkillEffect}）只做自己一件事，外部依赖隔离在
 * {@code effect/} 包内。调用方（HeroNightfallSkillTicker）只够到本门面。
 */
public final class HeroNightfallSkillEffects {
    public enum EffectType {
        ARC_SLASH,
        CRIMSON_SLASH,
        JUDGEMENT_CUT,
        GROUND_BURST,
        LIGHTNING_CALL,
        YAMATO_BLAST_SWORD,
        YAMATO_HEAVY_RAIN,
        YAMATO_DAMOCLES,
        YAMATO_SUMMONED_SWORD,
        YAMATO_JUDGEMENT_CUT_END,
        SCYTHE_BLOOD_STACK,
        SCYTHE_BLOOD_HARVEST,
        SCYTHE_SCARLET_END,
        MURASAMA_ZANDATSU,
        BEAST_ROAR,
        BLOOD_LUST
    }

    private static final Map<EffectType, HeroNightfallSkillEffect> EFFECTS = createEffects();

    private HeroNightfallSkillEffects() {
    }

    public static void apply(HeroEntity hero, HeroNightfallSkillSpec effect) {
        apply(null, hero, effect, false);
    }

    public static void apply(HeroEpicFightPatch patch, HeroEntity hero, HeroNightfallSkillSpec effect, boolean alignToWeaponJoint) {
        if (!(hero.level() instanceof ServerLevel) || effect == null) {
            return;
        }

        Vec3 center = AbstractHeroNightfallSkillEffect.resolveCenter(patch, hero, hero.getTarget(), alignToWeaponJoint);
        HeroNightfallSkillEffect impl = EFFECTS.get(effect.effectType());
        if (impl != null) {
            impl.apply(patch, hero, center, effect);
        }
    }

    private static Map<EffectType, HeroNightfallSkillEffect> createEffects() {
        Map<EffectType, HeroNightfallSkillEffect> effects = new EnumMap<>(EffectType.class);
        effects.put(EffectType.ARC_SLASH, new ArcSlashEffect());
        effects.put(EffectType.CRIMSON_SLASH, new CrimsonSlashEffect());
        effects.put(EffectType.JUDGEMENT_CUT, new JudgementCutEffect());
        effects.put(EffectType.GROUND_BURST, new GroundBurstEffect());
        effects.put(EffectType.LIGHTNING_CALL, new LightningCallEffect());
        effects.put(EffectType.YAMATO_BLAST_SWORD, new YamatoBlastSwordEffect());
        effects.put(EffectType.YAMATO_HEAVY_RAIN, new YamatoHeavyRainEffect());
        effects.put(EffectType.YAMATO_DAMOCLES, new YamatoDamoclesEffect());
        effects.put(EffectType.YAMATO_SUMMONED_SWORD, new YamatoSummonedSwordEffect());
        effects.put(EffectType.YAMATO_JUDGEMENT_CUT_END, new YamatoJudgementCutEndEffect());
        effects.put(EffectType.SCYTHE_BLOOD_STACK, new ScytheBloodStackEffect());
        effects.put(EffectType.SCYTHE_BLOOD_HARVEST, new ScytheBloodHarvestEffect());
        effects.put(EffectType.SCYTHE_SCARLET_END, new ScytheScarletEndEffect());
        effects.put(EffectType.MURASAMA_ZANDATSU, new MurasamaZandatsuEffect());
        effects.put(EffectType.BEAST_ROAR, new BeastRoarEffect());
        effects.put(EffectType.BLOOD_LUST, new BloodLustEffect());
        return Map.copyOf(effects);
    }
}
