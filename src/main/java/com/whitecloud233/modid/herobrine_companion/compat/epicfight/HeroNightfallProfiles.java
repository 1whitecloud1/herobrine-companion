package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSeries.Trigger;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 夜幕武器静态数据注册表（单一职责）：每把武器的档案（招式动画 + 技能系列 + 特效规格）。
 * 只定义"是什么"，不负责构建行为图或判定。新增武器/招式都在这里加。
 */
final class HeroNightfallProfiles {
    private static final String YAMATO_ANIMS = "com.hm.efn.gameasset.animations.EFNYamatoAnimations";
    private static final String MURASAMA_ANIMS = "com.hm.efn.gameasset.animations.EFNMurasamaAnimations";
    private static final String HF_BLADE_ANIMS = "com.hm.efn.gameasset.animations.EFNHfBladeAnimations";
    private static final String SEKIRO_ANIMS = "com.hm.efn.gameasset.animations.EFNSekiroAnimations";
    private static final String BROADBLADE_ANIMS = "com.hm.efn.gameasset.animations.EFNBroadBladeAnimations";
    private static final String FALCHION_ANIMS = "com.hm.efn.gameasset.animations.EFNFalchionAnimations";
    private static final String EXSILIUM_ANIMS = "com.hm.efn.gameasset.animations.EFNExsiliumgladiusAnimations";
    private static final String LANCE_ANIMS = "com.hm.efn.gameasset.animations.EFNLanceAnimations";
    private static final String THORNWHEEL_ANIMS = "com.hm.efn.gameasset.animations.EFNThornWheelAnimations";
    private static final String CLAW_ANIMS = "com.hm.efn.gameasset.animations.EFNClawAnimations";
    private static final String GREATSWORD_ANIMS = "com.hm.efn.gameasset.animations.EFNGreatSwordAnimations";
    private static final String TACHI_ANIMS = "com.hm.efn.gameasset.animations.EFNTachiAnimations";
    private static final String SWORD_ANIMS = "com.hm.efn.gameasset.animations.EFNSwordAnimations";
    private static final String DUAL_ANIMS = "com.hm.efn.gameasset.animations.EFNDualSwordAnimations";
    private static final String SHORTSWORD_ANIMS = "com.hm.efn.gameasset.animations.EFNShortSwordAnimations";
    private static final String SCYTHE_ANIMS = "com.hm.efn.gameasset.animations.EFNScytheAnimations";
    private static final String EPICFIGHT_ANIMS = "yesman.epicfight.gameasset.Animations";

    private static final Map<String, HeroNightfallProfile> PROFILES = new LinkedHashMap<>();

    static {
        register(profile(
                paths("yamato_dmc", "yamato_dmc4", "yamato_dmc_in_sheath", "yamato_dmc4_in_sheath"),
                4.8D,
                true,
                living(
                        LivingMotions.IDLE, field(YAMATO_ANIMS, "YAMATO_IDLE"),
                        LivingMotions.WALK, field(YAMATO_ANIMS, "YAMATO_WALK"),
                        LivingMotions.CHASE, field(YAMATO_ANIMS, "YAMATO_RUN"),
                        LivingMotions.JUMP, field(YAMATO_ANIMS, "YAMATO_JUMP"),
                        LivingMotions.FALL, field(YAMATO_ANIMS, "YAMATO_IDLE")
                ),
                combo(
                        field(YAMATO_ANIMS, "YAMATO_NORMAL_AUTO1"),
                        field(YAMATO_ANIMS, "YAMATO_NORMAL_AUTO2"),
                        field(YAMATO_ANIMS, "YAMATO_NORMAL_AUTO3"),
                        field(YAMATO_ANIMS, "YAMATO_EXTEND_AUTO3"),
                        field(YAMATO_ANIMS, "YAMATO_EXTEND_AUTO4"),
                        field(YAMATO_ANIMS, "YAMATO_EXTEND_AUTO5")
                ),
                List.of(
                        skill(18.0F, 60, 0.0D, 5.2D, 0.9F,
                                effect(HeroNightfallSkillEffects.EffectType.YAMATO_BLAST_SWORD, 0.20F, 4.5F, 2.4D),
                                field(YAMATO_ANIMS, "YAMATO_DIVORCE_AUTO1")),
                        skill(16.0F, 70, 0.0D, 5.4D, 0.85F,
                                effect(HeroNightfallSkillEffects.EffectType.YAMATO_HEAVY_RAIN, 0.10F, 5.0F, 2.9D),
                                field(YAMATO_ANIMS, "YAMATO_DIVORCE_AUTO2")),
                        skill(14.0F, 85, 0.0D, 5.6D, 0.8F,
                                effect(HeroNightfallSkillEffects.EffectType.YAMATO_DAMOCLES, 0.05F, 7.0F, 3.1D),
                                field(YAMATO_ANIMS, "YAMATO_DIVORCE_AUTO3")),
                        skill(15.0F, 95, 1.0D, 6.0D, 0.75F,
                                field(YAMATO_ANIMS, "YAMATO_FLARECUT"),
                                field(YAMATO_ANIMS, "YAMATO_FLARECUT_RISING"),
                                field(YAMATO_ANIMS, "YAMATO_FLARECUT_REPAID")),
                        skill(13.0F, 105, 0.0D, 5.8D, 0.7F,
                                field(YAMATO_ANIMS, "YAMATO_ORBIT_1"),
                                field(YAMATO_ANIMS, "YAMATO_ORBIT_2")),
                        skill(16.0F, 95, 0.0D, 5.8D, 0.75F,
                                field(YAMATO_ANIMS, "YAMATO_VOLCANOL"),
                                field(YAMATO_ANIMS, "YAMATO_VOLCANOL_CHARGE"),
                                field(YAMATO_ANIMS, "YAMATO_VOLCANOL_ALL")),
                        skill(13.0F, 90, 0.0D, 5.2D, 0.7F,
                                field(YAMATO_ANIMS, "YAMATO_UPPERSLASH"),
                                field(YAMATO_ANIMS, "YAMATO_UPPERSLASH_HOLD")),
                        skill(12.0F, 100, 1.5D, 7.0D, 0.65F,
                                field(YAMATO_ANIMS, "YAMATO_DRIVE"),
                                field(YAMATO_ANIMS, "YAMATO_DRIVE_MOB")),
                        skill(12.0F, 95, 1.5D, 6.5D, 0.65F,
                                field(YAMATO_ANIMS, "YAMATO_REPAIDSLASH"),
                                field(YAMATO_ANIMS, "YAMATO_REPAIDSLASH_MOB")),
                        skill(9.0F, 130, 2.0D, 8.5D, 0.5F,
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT"),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT_CHARGE"),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT_JUST"),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT_JUST_MOB"),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT_ALL")),
                        // 3.4.0：幻影剑连射
                        skill(6.0F, 140, 0.0D, 6.5D, 0.35F,
                                effect(HeroNightfallSkillEffects.EffectType.YAMATO_SUMMONED_SWORD, 0.25F, 4.0F, 3.4D),
                                field(YAMATO_ANIMS, "YAMATO_DIVORCE_AUTO1"),
                                field(YAMATO_ANIMS, "YAMATO_DIVORCE_AUTO2")),
                        // 3.4.0：次元斩绝（处决）
                        skill(4.0F, 220, 0.0D, 8.0D, 0.25F,
                                effect(HeroNightfallSkillEffects.EffectType.YAMATO_JUDGEMENT_CUT_END, 0.35F, 10.0F, 5.0D),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT"),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT_JUST"),
                                field(YAMATO_ANIMS, "YAMATO_JUDEMENCUT_ALL")),
                        airSkill(14.0F, 90, 0.0D, 5.4D, 0.75F,
                                field(YAMATO_ANIMS, "YAMATO_AERIALRAVE_AUTO1"),
                                field(YAMATO_ANIMS, "YAMATO_AERIALRAVE_AUTO2"),
                                field(YAMATO_ANIMS, "YAMATO_AERIALRAVE_AUTO3")),
                        airSkill(12.0F, 105, 0.0D, 5.6D, 0.65F,
                                field(YAMATO_ANIMS, "YAMATO_AIRFLUSH"),
                                field(YAMATO_ANIMS, "YAMATO_HELMBREAKER")),
                        airSkill(10.0F, 115, 0.0D, 6.0D, 0.55F,
                                field(YAMATO_ANIMS, "YAMATO_KILLERBEE"),
                                field(YAMATO_ANIMS, "YAMATO_KILLERBEE_HIT")),
                        airSkill(8.0F, 125, 0.0D, 5.2D, 0.45F,
                                field(YAMATO_ANIMS, "YAMATO_STOMP"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.JUDGEMENT_CUT, 0.16F, 6.0F, 3.25D)
        ));

        register(profile(
                paths("hf_murasama"),
                4.9D,
                true,
                living(
                        LivingMotions.IDLE, field(MURASAMA_ANIMS, "HF_MURASAMA_IDLE_COMBAT"),
                        LivingMotions.WALK, field(MURASAMA_ANIMS, "HF_MURASAMA_WALK_COMBAT"),
                        LivingMotions.CHASE, field(MURASAMA_ANIMS, "HF_MURASAMA_RUN_COMBAT_1"),
                        LivingMotions.FALL, field(MURASAMA_ANIMS, "HF_MURASAMA_IDLE_AIR"),
                        LivingMotions.BLOCK, field(MURASAMA_ANIMS, "HF_MURASAMA_GUARD")
                ),
                combo(
                        field(MURASAMA_ANIMS, "HF_MURASAMA_DASH_X"),
                        field(MURASAMA_ANIMS, "HF_MURASAMA_X"),
                        field(MURASAMA_ANIMS, "HF_MURASAMA_XX"),
                        field(MURASAMA_ANIMS, "HF_MURASAMA_XXX"),
                        field(MURASAMA_ANIMS, "HF_MURASAMA_XXXX")
                ),
                List.of(
                        skill(18.0F, 70, 1.8D, 6.8D, 0.85F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_DASH_Y"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_DASH_Y_SP")),
                        skill(14.0F, 85, 0.0D, 4.9D, 0.75F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_Y"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_Y_CHARGE")),
                        skill(12.0F, 100, 2.0D, 7.8D, 0.65F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_Y_CHARGE_THROUGH")),
                        skill(16.0F, 80, 0.0D, 5.2D, 0.8F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XY"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XY_CHARGE")),
                        skill(14.0F, 90, 0.0D, 5.4D, 0.75F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XXY"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XXY_CHARGE")),
                        skill(12.0F, 105, 0.0D, 5.6D, 0.7F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XXXY"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XXXY_CHARGE")),
                        skill(9.0F, 90, 0.0D, 4.6D, 0.55F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_KICK_Y")),
                        counterSkill(7.0F, 120, 0.0D, 4.8D, 0.45F,
                                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.2F, 4.0F, 2.8D),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_COUNTER")),
                        skill(6.0F, 180, 0.0D, 4.4D, 0.35F,
                                effect(HeroNightfallSkillEffects.EffectType.MURASAMA_ZANDATSU, 0.3F, 8.0F, 3.2D),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_ZANDATSU")),
                        airSkill(11.0F, 95, 0.0D, 5.2D, 0.7F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_X_AIR"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_XX_AIR")),
                        airSkill(9.0F, 115, 0.0D, 5.4D, 0.6F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_Y_AIR"),
                                field(MURASAMA_ANIMS, "HF_MURASAMA_Y_CHARGE_AIR")),
                        airSkill(5.0F, 190, 0.0D, 4.8D, 0.35F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_ZANDATSU_AIR"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.CRIMSON_SLASH, 0.14F, 5.0F, 3.0D)
        ));

        register(profile(
                paths("hf_blade"),
                4.9D,
                true,
                living(
                        LivingMotions.IDLE, field(HF_BLADE_ANIMS, "HF_BLADE_IDLE_COMBAT"),
                        LivingMotions.WALK, field(HF_BLADE_ANIMS, "HF_BLADE_WALK_COMBAT"),
                        LivingMotions.CHASE, field(HF_BLADE_ANIMS, "HF_BLADE_RUN_COMBAT_1"),
                        LivingMotions.BLOCK, field(HF_BLADE_ANIMS, "HF_BLADE_GUARD")
                ),
                combo(
                        field(HF_BLADE_ANIMS, "HF_BLADE_X"),
                        field(HF_BLADE_ANIMS, "HF_BLADE_XX"),
                        field(HF_BLADE_ANIMS, "HF_BLADE_XXX"),
                        field(HF_BLADE_ANIMS, "HF_BLADE_XXXX")
                ),
                List.of(
                        skill(0.9F, 70, 1.5D, 6.5D, field(HF_BLADE_ANIMS, "HF_BLADE_DASH_Y_SP")),
                        skill(0.7F, 95, 2.0D, 7.5D, field(HF_BLADE_ANIMS, "HF_BLADE_Y_CHARGE_THROUGH")),
                        skill(0.55F, 120, 0.0D, 4.8D, field(HF_BLADE_ANIMS, "HF_BLADE_XXXY_CHARGE")),
                        counterSkill(6.0F, 120, 0.0D, 4.8D, 0.4F,
                                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.2F, 4.0F, 2.8D),
                                field(HF_BLADE_ANIMS, "HF_BLADE_COUNTER")),
                        skill(5.0F, 180, 0.0D, 4.4D, 0.3F,
                                effect(HeroNightfallSkillEffects.EffectType.MURASAMA_ZANDATSU, 0.3F, 8.0F, 3.2D),
                                field(HF_BLADE_ANIMS, "HF_BLADE_ZANDATSU")),
                        airSkill(8.0F, 110, 0.0D, 5.0D, 0.55F, field(EPICFIGHT_ANIMS, "SWORD_AIR_SLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.CRIMSON_SLASH, 0.14F, 5.0F, 3.0D)
        ));

        register(profile(
                paths("kusabimaru"),
                4.6D,
                true,
                living(
                        LivingMotions.IDLE, field(SEKIRO_ANIMS, "KUSABIMARU_IDLE")
                ),
                combo(
                        field(SEKIRO_ANIMS, "KUSABIMARU_AUTO1"),
                        field(SEKIRO_ANIMS, "KUSABIMARU_AUTO2"),
                        field(SEKIRO_ANIMS, "KUSABIMARU_AUTO3"),
                        field(SEKIRO_ANIMS, "KUSABIMARU_AUTO4"),
                        field(SEKIRO_ANIMS, "KUSABIMARU_AUTO5")
                ),
                List.of(
                        skill(0.8F, 100, 3.0D, 8.5D, field(SEKIRO_ANIMS, "DRAGON_FLASH")),
                        skill(0.7F, 85, 0.0D, 4.8D, field(SEKIRO_ANIMS, "ICHIMONJI_1"), field(SEKIRO_ANIMS, "ICHIMONJI_2")),
                        skill(5.0F, 150, 1.0D, 6.5D, 0.3F,
                                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.3F, 7.0F, 3.4D),
                                field(SEKIRO_ANIMS, "MORTAL_BLADE_1"),
                                field(SEKIRO_ANIMS, "MORTAL_BLADE_2")),
                        airSkill(8.0F, 105, 0.0D, 5.0D, 0.55F, field(TACHI_ANIMS, "NF_TACHI_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.16F, 4.5F, 2.75D)
        ));

        register(profile(
                paths("broadblade"),
                4.8D,
                true,
                living(
                        LivingMotions.IDLE, field(BROADBLADE_ANIMS, "BROADBLADE_IDLE"),
                        LivingMotions.WALK, field(BROADBLADE_ANIMS, "BROADBLADE_WALK"),
                        LivingMotions.CHASE, field(BROADBLADE_ANIMS, "BROADBLADE_RUN"),
                        LivingMotions.BLOCK, field(BROADBLADE_ANIMS, "BROADBLADE_GUARD")
                ),
                combo(
                        field(BROADBLADE_ANIMS, "BROADBLADE_AUTO1"),
                        field(BROADBLADE_ANIMS, "BROADBLADE_AUTO2"),
                        field(BROADBLADE_ANIMS, "BROADBLADE_AUTO3"),
                        field(BROADBLADE_ANIMS, "BROADBLADE_AUTO4")
                ),
                List.of(
                        skill(0.85F, 90, 1.5D, 6.0D, field(BROADBLADE_ANIMS, "BROADBLADE_DASHSLASH")),
                        counterSkill(0.45F, 100, 0.0D, 4.0D, 0.5F,
                                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.2F, 5.0F, 3.0D),
                                field(BROADBLADE_ANIMS, "BROADBLADE_COUNTER")),
                        airSkill(9.0F, 110, 0.0D, 5.4D, 0.6F, field(BROADBLADE_ANIMS, "BROADBLADE_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.GROUND_BURST, 0.18F, 6.0F, 3.4D)
        ));

        register(profile(
                paths("crescentmoon", "crescent_moon"),
                4.4D,
                true,
                living(
                        LivingMotions.IDLE, field(FALCHION_ANIMS, "FALCHION_IDLE"),
                        LivingMotions.CHASE, field(FALCHION_ANIMS, "FALCHION_RUN"),
                        LivingMotions.BLOCK, field(FALCHION_ANIMS, "FALCHION_GUARD")
                ),
                combo(
                        field(FALCHION_ANIMS, "FALCHION_AUTO1"),
                        field(FALCHION_ANIMS, "FALCHION_AUTO2"),
                        field(FALCHION_ANIMS, "FALCHION_AUTO3")
                ),
                List.of(
                        skill(14.0F, 70, 0.0D, 4.8D, 0.12F, field(FALCHION_ANIMS, "FALCHION_EX1"), field(FALCHION_ANIMS, "FALCHION_EX2")),
                        skill(8.0F, 110, 1.5D, 6.0D, 0.07F, field(FALCHION_ANIMS, "FALCHION_SKILL")),
                        skill(12.0F, 90, 1.0D, 5.0D, 0.10F, field(FALCHION_ANIMS, "FALCHION_STRIKE")),
                        airSkill(8.0F, 105, 0.0D, 5.0D, 0.55F, field(FALCHION_ANIMS, "FALCHION_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.14F, 4.0F, 2.6D)
        ));

        register(withComboEffect(profile(
                paths("crimsonmoon", "crimson_moon"),
                4.8D,
                true,
                living(
                        LivingMotions.IDLE, field(SCYTHE_ANIMS, "SCYTHE_IDLE_COMBAT"),
                        LivingMotions.WALK, field(SCYTHE_ANIMS, "SCYTHE_WALK_COMBAT"),
                        LivingMotions.CHASE, field(SCYTHE_ANIMS, "SCYTHE_RUN_COMBAT"),
                        LivingMotions.BLOCK, field(SCYTHE_ANIMS, "SCYTHE_BLOCK")
                ),
                combo(
                        field(SCYTHE_ANIMS, "SCYTHE_AUTO1"),
                        field(SCYTHE_ANIMS, "SCYTHE_AUTO2"),
                        field(SCYTHE_ANIMS, "SCYTHE_AUTO3"),
                        field(SCYTHE_ANIMS, "SCYTHE_AUTO4"),
                        field(SCYTHE_ANIMS, "SCYTHE_AUTO5")
                ),
                List.of(
                        bloodHarvestSkill(18.0F, 110, 0.0D, 4.8D, 0.18F,
                                HeroEntity.BATTLE_ACTION_HEAVY_HOLD,
                                effect(HeroNightfallSkillEffects.EffectType.SCYTHE_BLOOD_HARVEST, 0.87F, 5.2F, 3.15D),
                                true,
                                field(SCYTHE_ANIMS, "SCYTHE_HARVEST")),
                        skill(500.0F, 0, 0.0D, 5.8D, 1.0F,
                                HeroEntity.BATTLE_ACTION_HEAVY_RELEASE,
                                effect(HeroNightfallSkillEffects.EffectType.SCYTHE_SCARLET_END, 1.30F, 7.5F, 3.65D),
                                true,
                                field(SCYTHE_ANIMS, "SCYTHE_SCARLET_END")),
                        airSkill(7.0F, 120, 0.0D, 5.2D, 0.45F, field(SCYTHE_ANIMS, "SCYTHE_AIR_SLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.SCYTHE_BLOOD_STACK, 0.14F, 0.5F, 2.8D)
        ), effect(HeroNightfallSkillEffects.EffectType.SCYTHE_BLOOD_STACK, 0.14F, 0.5F, 2.8D)));

        register(profile(
                paths("exsiliumgladius", "fire_exsiliumgladius"),
                4.6D,
                true,
                living(
                        LivingMotions.IDLE, field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_IDLE"),
                        LivingMotions.WALK, field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_WALK"),
                        LivingMotions.CHASE, field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_RUN")
                ),
                combo(
                        field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_A"),
                        field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_AA"),
                        field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_AAA"),
                        field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_AB")
                ),
                List.of(
                        skill(0.8F, 95, 1.5D, 6.0D, field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_DASH")),
                        skill(0.65F, 120, 2.5D, 7.5D, field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_D"), field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_DD"), field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_DDD")),
                        airSkill(9.0F, 115, 0.0D, 5.4D, 0.6F, field(EXSILIUM_ANIMS, "EXSILIUMGLADIUS_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.16F, 4.5F, 2.8D)
        ));

        register(profile(
                paths("meen_spear"),
                5.8D,
                true,
                living(
                        LivingMotions.IDLE, field(LANCE_ANIMS, "NF_MEEN_IDLE"),
                        LivingMotions.WALK, field(LANCE_ANIMS, "NF_MEEN_WALK"),
                        LivingMotions.CHASE, field(LANCE_ANIMS, "NF_MEEN_RUN")
                ),
                combo(
                        field(LANCE_ANIMS, "NF_MEEN_AUTO1"),
                        field(LANCE_ANIMS, "NF_MEEN_AUTO2"),
                        field(LANCE_ANIMS, "NF_MEEN_AUTO3"),
                        field(LANCE_ANIMS, "NF_MEEN_AUTO4")
                ),
                List.of(
                        skill(0.75F, 85, 2.0D, 7.0D, field(LANCE_ANIMS, "NF_MEEN_DASH")),
                        skill(0.55F, 130, 2.5D, 8.0D, field(LANCE_ANIMS, "NF_MEEN_CHARGING_MOB"), field(LANCE_ANIMS, "NF_MEEN_CHARGE3"), field(LANCE_ANIMS, "NF_MEEN_FINISHER")),
                        airSkill(8.0F, 115, 0.0D, 5.8D, 0.55F, field(LANCE_ANIMS, "NF_MEEN_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.LIGHTNING_CALL, 0.18F, 6.5F, 3.5D)
        ));

        register(profile(
                paths("thornwheel"),
                4.4D,
                true,
                // 刺轮 EFN 专属动画（THORNWHEEL_*）已由 Hero 骨架（自带 wheel 关节）绑定。
                // 注意：EF 的标准 GREATSWORD_DASH/AIR_SLASH/GUARD accessor 是空的（注册失败），
                // 因此冲刺/空中斩/格挡改用 EFN 的 NG_GREATSWORD_*（非空、可用）。
                living(
                        LivingMotions.IDLE, field(THORNWHEEL_ANIMS, "THORNWHEEL_IDLE"),
                        LivingMotions.WALK, field(GREATSWORD_ANIMS, "NG_GREATSWOED_WALK"),
                        LivingMotions.CHASE, field(GREATSWORD_ANIMS, "NG_GREATSWORD_RUN")
                ),
                combo(
                        field(THORNWHEEL_ANIMS, "THORNWHEEL_AUTO1"),
                        field(THORNWHEEL_ANIMS, "THORNWHEEL_AUTO2"),
                        field(THORNWHEEL_ANIMS, "THORNWHEEL_AUTO3")
                ),
                List.of(
                        skill(10.0F, 110, 1.0D, 5.0D, 0.65F,
                                field(THORNWHEEL_ANIMS, "THORNWHEEL_SKILL_START"),
                                field(THORNWHEEL_ANIMS, "THORNWHEEL_SKILL_LOOP"),
                                field(THORNWHEEL_ANIMS, "THORNWHEEL_SKILL_END")),
                        skill(8.0F, 125, 0.0D, 4.8D, 0.55F,
                                field(THORNWHEEL_ANIMS, "THORNWHEEL_SKILL_START_N"),
                                field(THORNWHEEL_ANIMS, "THORNWHEEL_SKILL_LOOP_N"),
                                field(THORNWHEEL_ANIMS, "THORNWHEEL_SKILL_END_N")),
                        skill(7.0F, 95, 1.5D, 6.0D, 0.55F, field(GREATSWORD_ANIMS, "NG_GREATSWORD_DASH")),
                        airSkill(7.0F, 125, 0.0D, 5.2D, 0.45F, field(GREATSWORD_ANIMS, "NG_GREATSWORD_AIRSLASH_NEW"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.GROUND_BURST, 0.18F, 5.0F, 3.0D)
        ));

        register(profile(
                paths("nf_claw"),
                4.2D,
                true,
                // 爪的 EFN 专属动画（NF_CLAW_*）绑定专用 weapon/nf_claw 骨架的 Claw_R/Claw_L 关节，
                // 由 HeroNightfallArmatureJoints 已补到 Hero 骨架上 → 模型/命中框正常绑定
                living(
                        LivingMotions.IDLE, field(CLAW_ANIMS, "NF_CLAW_IDLE"),
                        LivingMotions.WALK, field(CLAW_ANIMS, "NF_CLAW_WALK"),
                        LivingMotions.CHASE, field(CLAW_ANIMS, "NF_CLAW_RUN")
                ),
                combo(
                        field(CLAW_ANIMS, "NF_CLAW_AUTO1"),
                        field(CLAW_ANIMS, "NF_CLAW_AUTO2"),
                        field(CLAW_ANIMS, "NF_CLAW_AUTO3")
                ),
                List.of(
                        skill(8.0F, 80, 1.0D, 6.0D, 0.65F, field(CLAW_ANIMS, "NF_CLAW_DASH")),
                        skill(6.0F, 120, 0.0D, 4.8D, 0.45F,
                                effect(HeroNightfallSkillEffects.EffectType.BEAST_ROAR, 0.15F, 0.0F, 0.0D),
                                field(CLAW_ANIMS, "NF_CLAW_BEASTROAR")),
                        airSkill(7.0F, 105, 0.0D, 5.0D, 0.5F, field(CLAW_ANIMS, "NF_CLAW_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.16F, 3.5F, 2.2D)
        ));

        register(profile(
                paths("ruinsgreatsword"),
                5.2D,
                true,
                living(
                        LivingMotions.IDLE, field(GREATSWORD_ANIMS, "NG_GREATSWORD_IDLE"),
                        LivingMotions.WALK, field(GREATSWORD_ANIMS, "NG_GREATSWOED_WALK"),
                        LivingMotions.CHASE, field(GREATSWORD_ANIMS, "NG_GREATSWORD_RUN")
                ),
                combo(
                        field(GREATSWORD_ANIMS, "NG_GREATSWORD_AUTO1"),
                        field(GREATSWORD_ANIMS, "NG_GREATSWORD_AUTO2"),
                        field(GREATSWORD_ANIMS, "NG_GREATSWORD_AUTO3")
                ),
                List.of(
                        skill(0.8F, 90, 1.5D, 6.5D, field(GREATSWORD_ANIMS, "NG_GREATSWORD_DASH")),
                        skill(0.55F, 130, 2.5D, 8.5D, field(GREATSWORD_ANIMS, "NG_GREATSWORD_CHARGING_MOB"), field(GREATSWORD_ANIMS, "NG_GREATSWORD_CHARG1MAX_FIRST"), field(GREATSWORD_ANIMS, "NG_GREATSWORD_CHARG1MAX_SECOND")),
                        airSkill(8.0F, 125, 0.0D, 5.8D, 0.55F, field(GREATSWORD_ANIMS, "NG_GREATSWORD_AIRSLASH_NEW"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.GROUND_BURST, 0.2F, 7.0F, 3.8D)
        ));

        register(profile(
                paths("air_tachi", "co_tachi"),
                4.8D,
                true,
                living(
                        LivingMotions.IDLE, field(TACHI_ANIMS, "NF_TACHI_IDLE"),
                        LivingMotions.WALK, field(TACHI_ANIMS, "NF_TACHI_WALK"),
                        LivingMotions.CHASE, field(TACHI_ANIMS, "NF_TACHI_RUN")
                ),
                combo(
                        field(TACHI_ANIMS, "NF_TACHI_AUTO1"),
                        field(TACHI_ANIMS, "NF_TACHI_AUTO2"),
                        field(TACHI_ANIMS, "NF_TACHI_AUTO3"),
                        field(TACHI_ANIMS, "NF_TACHI_AUTO4"),
                        field(TACHI_ANIMS, "NF_TACHI_AUTO5")
                ),
                List.of(
                        skill(0.75F, 85, 1.5D, 6.0D, field(TACHI_ANIMS, "NF_TACHI_DASH")),
                        skill(0.55F, 120, 1.0D, 5.0D, 0.55F,
                                effect(HeroNightfallSkillEffects.EffectType.BLOOD_LUST, 0.2F, 0.0F, 0.0D),
                                field(TACHI_ANIMS, "NF_TACHI_BLOODLUST"),
                                field(TACHI_ANIMS, "NF_TACHI_BLOODLUST_END")),
                        airSkill(9.0F, 105, 0.0D, 5.2D, 0.6F, field(TACHI_ANIMS, "NF_TACHI_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.15F, 4.75F, 2.9D)
        ));

        register(profile(
                paths("sword_of_pioneer", "excalibur"),
                4.6D,
                true,
                living(
                        LivingMotions.IDLE, field(SWORD_ANIMS, "NF_SWORD_IDLE"),
                        LivingMotions.WALK, field(SWORD_ANIMS, "NF_SWORD_WALK"),
                        LivingMotions.CHASE, field(SWORD_ANIMS, "NF_SWORD_RUN"),
                        LivingMotions.BLOCK, field(SWORD_ANIMS, "NF_SWORD_GUARD")
                ),
                combo(
                        field(SWORD_ANIMS, "NF_SWORD_AUTO1"),
                        field(SWORD_ANIMS, "NF_SWORD_AUTO2"),
                        field(SWORD_ANIMS, "NF_SWORD_AUTO3"),
                        field(SWORD_ANIMS, "NF_SWORD_AUTO4")
                ),
                List.of(
                        skill(0.8F, 90, 1.5D, 6.0D, field(SWORD_ANIMS, "NF_SWORD_DASH")),
                        skill(0.55F, 120, 1.0D, 5.5D, field(SWORD_ANIMS, "NF_SWORD_SKILL_FIRST"), field(SWORD_ANIMS, "NF_SWORD_SKILL_SECOND")),
                        airSkill(8.0F, 105, 0.0D, 5.2D, 0.55F, field(SWORD_ANIMS, "NF_SWORD_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.14F, 4.5F, 2.8D)
        ));

        register(profile(
                paths("nf_dual_sword"),
                4.5D,
                true,
                living(
                        LivingMotions.IDLE, field(DUAL_ANIMS, "NF_DUAL_IDLE"),
                        LivingMotions.WALK, field(DUAL_ANIMS, "NF_DUAL_WALK"),
                        LivingMotions.CHASE, field(DUAL_ANIMS, "NF_DUAL_RUN")
                ),
                combo(
                        field(DUAL_ANIMS, "NF_DUAL_AUTO1"),
                        field(DUAL_ANIMS, "NF_DUAL_AUTO2"),
                        field(DUAL_ANIMS, "NF_DUAL_AUTO3"),
                        field(DUAL_ANIMS, "NF_DUAL_AUTO4")
                ),
                List.of(
                        skill(0.7F, 90, 1.5D, 6.0D, field(DUAL_ANIMS, "NF_DUAL_SKILL"), field(DUAL_ANIMS, "NF_DUAL_SKILL_EXTEND")),
                        skill(0.5F, 120, 0.0D, 5.0D, field(DUAL_ANIMS, "NF_DUAL_STORMATK")),
                        airSkill(8.0F, 105, 0.0D, 5.0D, 0.55F, field(DUAL_ANIMS, "NF_DUAL_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.14F, 4.25F, 2.7D)
        ));

        register(profile(
                paths("nf_shortsword", "nf_shortsword_2"),
                4.3D,
                true,
                living(),
                combo(
                        field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AUTO1"),
                        field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AUTO2"),
                        field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AUTO3"),
                        field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AUTO4"),
                        field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AUTO5"),
                        field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AUTO6")
                ),
                List.of(
                        skill(0.75F, 85, 1.0D, 6.0D, field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_DASH")),
                        skill(0.45F, 110, 0.0D, 4.8D, field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_SKILL")),
                        airSkill(8.0F, 100, 0.0D, 4.8D, 0.55F, field(SHORTSWORD_ANIMS, "NF_SHORTSWORD_AIRSLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.12F, 3.75F, 2.4D)
        ));
    }

    private HeroNightfallProfiles() {
    }

    @Nullable
    static HeroNightfallProfile resolve(ItemStack stack) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("efn") || stack == null || stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !"efn".equals(itemId.getNamespace())) {
            return null;
        }

        return PROFILES.get(itemId.getPath().toLowerCase(Locale.ROOT));
    }

    static Collection<HeroNightfallProfile> all() {
        return PROFILES.values();
    }

    private static void register(HeroNightfallProfile profile) {
        for (String itemPath : profile.itemPaths()) {
            PROFILES.put(itemPath, profile);
        }
    }

    private static HeroNightfallProfile profile(Set<String> itemPaths,
                                                double attackRadius,
                                                boolean combatSafe,
                                                Map<LivingMotion, HeroNightfallAnimationField> livingOverrides,
                                                List<HeroNightfallAnimationField> comboAnimations,
                                                List<HeroNightfallSkillSeries> skillSeries,
                                                @Nullable HeroNightfallSkillSpec skillEffect) {
        return new HeroNightfallProfile(itemPaths, attackRadius, combatSafe, livingOverrides, comboAnimations, skillSeries, skillEffect, null);
    }

    /** 给档案额外指定"连段普攻命中特效"（如赤月普攻叠血疫）。 */
    private static HeroNightfallProfile withComboEffect(HeroNightfallProfile profile, @Nullable HeroNightfallSkillSpec comboEffect) {
        return new HeroNightfallProfile(profile.itemPaths(), profile.attackRadius(), profile.combatSafe(),
                profile.livingOverrides(), profile.comboAnimations(), profile.skillSeries(), profile.skillEffect(), comboEffect);
    }

    private static Set<String> paths(String... itemPaths) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Arrays.stream(itemPaths).map(String::toLowerCase).forEach(set::add);
        return Set.copyOf(set);
    }

    private static Map<LivingMotion, HeroNightfallAnimationField> living(Object... entries) {
        LinkedHashMap<LivingMotion, HeroNightfallAnimationField> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put((LivingMotion) entries[i], (HeroNightfallAnimationField) entries[i + 1]);
        }
        return Map.copyOf(map);
    }

    private static List<HeroNightfallAnimationField> combo(HeroNightfallAnimationField... animations) {
        return List.of(animations);
    }

    private static HeroNightfallSkillSeries skill(float weight, int cooldown, double minDistance, double maxDistance, HeroNightfallAnimationField... animations) {
        return skill(weight, cooldown, minDistance, maxDistance, 1.0F, animations);
    }

    private static HeroNightfallSkillSeries skill(float weight, int cooldown, double minDistance, double maxDistance, float chance, HeroNightfallAnimationField... animations) {
        return new HeroNightfallSkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), null, -1, false, false, Trigger.DEFAULT);
    }

    private static HeroNightfallSkillSeries skill(float weight,
                                                  int cooldown,
                                                  double minDistance,
                                                  double maxDistance,
                                                  float chance,
                                                  @Nullable HeroNightfallSkillSpec effectSpec,
                                                  HeroNightfallAnimationField... animations) {
        return new HeroNightfallSkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), effectSpec, -1, false, false, Trigger.DEFAULT);
    }

    private static HeroNightfallSkillSeries airSkill(float weight, int cooldown, double minDistance, double maxDistance, float chance, HeroNightfallAnimationField... animations) {
        return new HeroNightfallSkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), null, -1, false, true, Trigger.DEFAULT);
    }

    private static HeroNightfallSkillSeries skill(float weight,
                                                  int cooldown,
                                                  double minDistance,
                                                  double maxDistance,
                                                  float chance,
                                                  int actionState,
                                                  @Nullable HeroNightfallSkillSpec effectSpec,
                                                  boolean anchorToWeaponJoint,
                                                  HeroNightfallAnimationField... animations) {
        return new HeroNightfallSkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), effectSpec, actionState, anchorToWeaponJoint, false, Trigger.DEFAULT);
    }

    private static HeroNightfallSkillSeries counterSkill(float weight,
                                                         int cooldown,
                                                         double minDistance,
                                                         double maxDistance,
                                                         float chance,
                                                         @Nullable HeroNightfallSkillSpec effectSpec,
                                                         HeroNightfallAnimationField... animations) {
        return new HeroNightfallSkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), effectSpec, -1, false, false, Trigger.COUNTER);
    }

    private static HeroNightfallSkillSeries bloodHarvestSkill(float weight,
                                                              int cooldown,
                                                              double minDistance,
                                                              double maxDistance,
                                                              float chance,
                                                              int actionState,
                                                              @Nullable HeroNightfallSkillSpec effectSpec,
                                                              boolean anchorToWeaponJoint,
                                                              HeroNightfallAnimationField... animations) {
        return new HeroNightfallSkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), effectSpec, actionState, anchorToWeaponJoint, false, Trigger.BLOOD_HARVEST);
    }

    private static HeroNightfallSkillSpec effect(HeroNightfallSkillEffects.EffectType effectType, float triggerTime, float bonusDamage, double radius) {
        return new HeroNightfallSkillSpec(effectType, triggerTime, bonusDamage, radius);
    }

    private static HeroNightfallAnimationField field(String owner, String fieldName) {
        return new HeroNightfallAnimationField(owner, fieldName);
    }
}
