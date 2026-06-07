package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroCombatPlanner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HeroNightfallMovesets {
    private static final String EFN_MOD_ID = "efn";
    private static final String IMPACTFUL_MOD_ID = "impactful";
    private static final String EFN_ANIMATION_OWNER_PREFIX = "com.hm.efn.gameasset.animations.";
    private static final String EPICFIGHT_ANIMS = "yesman.epicfight.gameasset.Animations";
    private static final double AIR_ATTACK_JUMP_Y = 0.62D;
    private static final double AIR_ATTACK_FORWARD_SPEED = 0.36D;
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
    private static final String CRIMSON_MOON_PATH = "crimson_moon";

    private static final Map<String, NightfallProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<String, AnimationAccessor<? extends StaticAnimation>> ANIMATION_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED_FIELDS = ConcurrentHashMap.newKeySet();

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
                        skill(7.0F, 120, 0.0D, 4.8D, 0.45F,
                                field(MURASAMA_ANIMS, "HF_MURASAMA_COUNTER")),
                        skill(6.0F, 180, 0.0D, 4.4D, 0.35F,
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
                        skill(0.45F, 100, 0.0D, 4.0D, field(BROADBLADE_ANIMS, "BROADBLADE_COUNTER")),
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

        register(profile(
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
                        skill(18.0F, 110, 0.0D, 4.8D, 0.18F,
                                HeroEntity.BATTLE_ACTION_HEAVY_HOLD,
                                effect(HeroNightfallSkillEffects.EffectType.CRIMSON_SLASH, 0.87F, 5.2F, 3.15D),
                                true,
                                field(SCYTHE_ANIMS, "SCYTHE_HARVEST")),
                        skill(500.0F, 0, 0.0D, 5.8D, 1.0F,
                                HeroEntity.BATTLE_ACTION_HEAVY_RELEASE,
                                effect(HeroNightfallSkillEffects.EffectType.CRIMSON_SLASH, 1.30F, 7.5F, 3.65D),
                                true,
                                field(SCYTHE_ANIMS, "SCYTHE_SCARLET_END")),
                        airSkill(7.0F, 120, 0.0D, 5.2D, 0.45F, field(SCYTHE_ANIMS, "SCYTHE_AIR_SLASH"))
                ),
                null
        ));

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
                living(
                        LivingMotions.IDLE, field(THORNWHEEL_ANIMS, "THORNWHEEL_IDLE"),
                        LivingMotions.WALK, field(GREATSWORD_ANIMS, "NG_GREATSWOED_WALK"),
                        LivingMotions.CHASE, field(GREATSWORD_ANIMS, "NG_GREATSWORD_RUN"),
                        LivingMotions.BLOCK, field(EPICFIGHT_ANIMS, "GREATSWORD_GUARD")
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
                        skill(7.0F, 95, 1.5D, 6.0D, 0.55F, field(EPICFIGHT_ANIMS, "GREATSWORD_DASH")),
                        airSkill(7.0F, 125, 0.0D, 5.2D, 0.45F, field(EPICFIGHT_ANIMS, "GREATSWORD_AIR_SLASH"))
                ),
                effect(HeroNightfallSkillEffects.EffectType.GROUND_BURST, 0.18F, 5.0F, 3.0D)
        ));

        register(profile(
                paths("nf_claw"),
                4.2D,
                true,
                living(
                        LivingMotions.IDLE, field(CLAW_ANIMS, "NF_CLAW_IDLE"),
                        LivingMotions.WALK, field(CLAW_ANIMS, "NF_CLAW_WALK"),
                        LivingMotions.CHASE, field(CLAW_ANIMS, "NF_CLAW_RUN")
                ),
                combo(
                        field(EPICFIGHT_ANIMS, "SWORD_AUTO1"),
                        field(EPICFIGHT_ANIMS, "SWORD_AUTO2"),
                        field(EPICFIGHT_ANIMS, "SWORD_AUTO3")
                ),
                List.of(
                        skill(8.0F, 80, 1.0D, 6.0D, 0.65F, field(EPICFIGHT_ANIMS, "SWORD_DASH")),
                        skill(6.0F, 120, 0.0D, 4.8D, 0.45F,
                                effect(HeroNightfallSkillEffects.EffectType.ARC_SLASH, 0.18F, 4.0F, 2.4D),
                                field(CLAW_ANIMS, "NF_CLAW_BEASTROAR")),
                        airSkill(7.0F, 105, 0.0D, 5.0D, 0.5F, field(EPICFIGHT_ANIMS, "SWORD_AIR_SLASH"))
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
                        skill(0.55F, 120, 1.0D, 5.0D, field(TACHI_ANIMS, "NF_TACHI_BLOODLUST"), field(TACHI_ANIMS, "NF_TACHI_BLOODLUST_END")),
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

    private HeroNightfallMovesets() {
    }

    public static boolean isSupported(ItemStack stack) {
        return resolve(stack) != null;
    }

    public static double getAttackRadius(ItemStack stack, double fallback) {
        NightfallProfile profile = resolve(stack);
        return profile == null || !profile.combatSafe() ? fallback : profile.attackRadius();
    }

    static Set<AnimationAccessor<? extends StaticAnimation>> collectReferencedOriginalAnimations() {
        if (!ModList.get().isLoaded(EFN_MOD_ID)) {
            return Set.of();
        }

        LinkedHashSet<AnimationAccessor<? extends StaticAnimation>> animations = new LinkedHashSet<>();
        for (NightfallProfile profile : PROFILES.values()) {
            profile.livingOverrides().values().forEach(animationField -> addResolvedOriginalAnimation(animations, animationField));
            profile.comboAnimations().forEach(animationField -> addResolvedOriginalAnimation(animations, animationField));
            for (SkillSeries skillSeries : profile.skillSeries()) {
                skillSeries.animations().forEach(animationField -> addResolvedOriginalAnimation(animations, animationField));
            }
        }

        return Set.copyOf(animations);
    }

    public static void applyLivingAnimations(ItemStack stack, Map<LivingMotion, AssetAccessor<? extends StaticAnimation>> livingAnimations) {
        NightfallProfile profile = resolve(stack);
        if (profile == null) {
            return;
        }

        for (Map.Entry<LivingMotion, AnimationField> entry : profile.livingOverrides().entrySet()) {
            AnimationAccessor<? extends StaticAnimation> animation = entry.getValue().resolve();
            if (isUsableAnimation(animation, entry.getValue())) {
                livingAnimations.put(entry.getKey(), animation);
            }
        }
    }

    private static boolean isUsableAnimation(@Nullable AnimationAccessor<? extends StaticAnimation> animation, AnimationField source) {
        return animation != null && (!animation.isEmpty() || isDeferredEfnAnimationOwner(source.owner()));
    }

    private static boolean isDeferredEfnAnimationOwner(String owner) {
        return owner != null && owner.startsWith(EFN_ANIMATION_OWNER_PREFIX);
    }

    @Nullable
    public static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCombatBehaviors(HeroEpicFightPatch patch, ItemStack stack) {
        NightfallProfile profile = resolve(stack);
        if (profile == null || !profile.combatSafe()) {
            return null;
        }

        return isCrimsonMoon(stack) ? buildCrimsonMoonCombatBehaviors(profile) : buildDefaultCombatBehaviors(profile);
    }

    @Nullable
    public static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCombatBehaviors(ItemStack stack) {
        return buildCombatBehaviors(null, stack);
    }

    @Nullable
    private static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildDefaultCombatBehaviors(NightfallProfile profile) {
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        boolean addedAnySeries = false;
        double comboRange = Math.max(3.6D, profile.attackRadius() + 0.6D);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(100.0F)
                .cooldown(3)
                .canBeInterrupted(false)
                .looping(false);
        boolean hasComboAnimation = false;

        for (int comboIndex = 0; comboIndex < profile.comboAnimations().size(); comboIndex++) {
            final int behaviorComboIndex = comboIndex;
            AnimationField animationField = profile.comboAnimations().get(comboIndex);
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isUsableAnimation(animation, animationField)) {
                continue;
            }

            comboSeries.nextBehavior(createAnimationBehavior(animation, resolveComboActionState(behaviorComboIndex), true)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation)
                            && (behaviorComboIndex == 0
                            ? canStartDefaultCombo(mobPatch, profile, behaviorComboIndex, comboRange)
                            : canContinueDefaultCombo(mobPatch, profile, behaviorComboIndex, comboRange))));
            hasComboAnimation = true;
        }

        if (hasComboAnimation) {
            builder.newBehaviorSeries(comboSeries);
            addedAnySeries = true;
        }

        for (SkillSeries skillSeries : profile.skillSeries()) {
            if (appendSkillSeries(builder, skillSeries)) {
                addedAnySeries = true;
            }
        }

        return addedAnySeries ? builder : null;
    }

    private static boolean appendSkillSeries(CombatBehaviors.Builder<HumanoidMobPatch<?>> builder, SkillSeries skillSeries) {
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> behaviorSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(skillSeries.weight())
                .cooldown(skillSeries.cooldown())
                .canBeInterrupted(false)
                .looping(false);
        boolean hasSkillAnimation = false;
        boolean firstBehavior = true;

        for (AnimationField animationField : skillSeries.animations()) {
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isUsableAnimation(animation, animationField)) {
                continue;
            }

            CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> behavior = createAnimationBehavior(animation, skillSeries.actionState(), false, skillSeries.airAttack());
            if (firstBehavior) {
                behavior.randomChance(skillSeries.chance())
                        .custom(mobPatch -> isPlayableAttackAnimation(animation) && canStartSkillSeries(mobPatch, skillSeries));
                firstBehavior = false;
            } else {
                behavior.custom(HeroNightfallMovesets::canContinueSkillSeries);
            }
            behaviorSeries.nextBehavior(behavior);
            hasSkillAnimation = true;
        }

        if (hasSkillAnimation) {
            builder.newBehaviorSeries(behaviorSeries);
        }
        return hasSkillAnimation;
    }

    @Nullable
    private static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCrimsonMoonCombatBehaviors(NightfallProfile profile) {
        if (profile.skillSeries().size() < 2) {
            return buildDefaultCombatBehaviors(profile);
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        double comboRange = Math.max(3.8D, profile.attackRadius() + 0.6D);
        SkillSeries harvest = profile.skillSeries().get(0);
        SkillSeries scarletEnd = profile.skillSeries().get(1);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(100.0F)
                .cooldown(3)
                .canBeInterrupted(false)
                .looping(false);
        boolean hasComboAnimation = false;

        for (int comboIndex = 0; comboIndex < profile.comboAnimations().size(); comboIndex++) {
            final int behaviorComboIndex = comboIndex;
            AnimationField animationField = profile.comboAnimations().get(comboIndex);
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isUsableAnimation(animation, animationField)) {
                continue;
            }

            comboSeries.nextBehavior(createAnimationBehavior(animation, resolveComboActionState(behaviorComboIndex), true)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation)
                            && (behaviorComboIndex == 0
                            ? canStartCrimsonMoonCombo(mobPatch, profile, behaviorComboIndex, comboRange)
                            : canContinueCrimsonMoonCombo(mobPatch, profile, behaviorComboIndex, comboRange))));
            hasComboAnimation = true;
        }

        if (!hasComboAnimation) {
            return buildDefaultCombatBehaviors(profile);
        }

        builder.newBehaviorSeries(comboSeries);

        if (isImpactfulCrimsonMoonSkillUnsafe()) {
            for (int skillIndex = 2; skillIndex < profile.skillSeries().size(); skillIndex++) {
                appendSkillSeries(builder, profile.skillSeries().get(skillIndex));
            }
            return builder;
        }

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> harvestSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(harvest.weight())
                .cooldown(harvest.cooldown())
                .canBeInterrupted(false)
                .looping(false);
        boolean hasHarvestAnimation = false;

        for (AnimationField animationField : harvest.animations()) {
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isUsableAnimation(animation, animationField)) {
                continue;
            }

            harvestSeries.nextBehavior(createAnimationBehavior(animation, harvest.actionState(), false)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation) && canStartCrimsonMoonHarvest(mobPatch, harvest))
                    .randomChance(harvest.chance()));
            hasHarvestAnimation = true;
        }

        if (!hasHarvestAnimation) {
            return buildDefaultCombatBehaviors(profile);
        }

        builder.newBehaviorSeries(harvestSeries);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> releaseSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(scarletEnd.weight())
                .cooldown(scarletEnd.cooldown())
                .canBeInterrupted(false)
                .looping(false);
        boolean hasReleaseAnimation = false;

        for (AnimationField animationField : scarletEnd.animations()) {
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isUsableAnimation(animation, animationField)) {
                continue;
            }

            releaseSeries.nextBehavior(createAnimationBehavior(animation, scarletEnd.actionState(), false)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation) && canStartCrimsonMoonRelease(mobPatch, scarletEnd)));
            hasReleaseAnimation = true;
        }

        if (!hasReleaseAnimation) {
            return buildDefaultCombatBehaviors(profile);
        }

        builder.newBehaviorSeries(releaseSeries);
        for (int skillIndex = 2; skillIndex < profile.skillSeries().size(); skillIndex++) {
            appendSkillSeries(builder, profile.skillSeries().get(skillIndex));
        }
        return builder;
    }

    private static boolean isImpactfulCrimsonMoonSkillUnsafe() {
        return ModList.get().isLoaded(IMPACTFUL_MOD_ID);
    }

    public static void tickSkillEffects(HeroEpicFightPatch patch, HeroEntity hero) {
        if (patch == null || hero == null || hero.level().isClientSide) {
            return;
        }

        NightfallProfile profile = resolve(hero.getMainHandItem());
        if (profile == null) {
            HeroEpicFightDebugLog.transition(hero, "HeroNightfallMovesets.tickSkillEffects", "profile=null|anim=" + HeroEpicFightDebugLog.animatorId(patch.getAnimator()) + "|action=" + hero.getBattleActionState(), "profile=null,state=" + HeroEpicFightDebugLog.heroCoreState(hero) + ",anim=" + HeroEpicFightDebugLog.animatorId(patch.getAnimator()));
            return;
        }

        AnimationPlayer player = patch.getAnimator() != null ? patch.getAnimator().getPlayerFor(null) : null;
        if (player == null || player.isEmpty()) {
            HeroEpicFightDebugLog.transition(hero, "HeroNightfallMovesets.tickSkillEffects", "profile=" + HeroEpicFightDebugLog.itemKey(hero.getMainHandItem()) + "|anim=empty|action=" + hero.getBattleActionState(), "profile=" + HeroEpicFightDebugLog.itemKey(hero.getMainHandItem()) + ",anim=empty,state=" + HeroEpicFightDebugLog.heroCoreState(hero));
            syncBattleActionState(hero, profile, null, null);
            return;
        }

        AssetAccessor<? extends StaticAnimation> currentAnimation = player.getRealAnimation();
        if (currentAnimation == null || currentAnimation.isEmpty()) {
            HeroEpicFightDebugLog.transition(hero, "HeroNightfallMovesets.tickSkillEffects", "profile=" + HeroEpicFightDebugLog.itemKey(hero.getMainHandItem()) + "|anim=realEmpty|action=" + hero.getBattleActionState(), "profile=" + HeroEpicFightDebugLog.itemKey(hero.getMainHandItem()) + ",anim=realEmpty,state=" + HeroEpicFightDebugLog.heroCoreState(hero));
            syncBattleActionState(hero, profile, null, null);
            return;
        }

        SkillSeries matchedSkillSeries = findMatchingSkillSeries(profile, currentAnimation);
        HeroEpicFightDebugLog.transition(hero, "HeroNightfallMovesets.tickSkillEffects", HeroEpicFightDebugLog.itemKey(hero.getMainHandItem()) + "|anim=" + currentAnimation.registryName() + "|skill=" + (matchedSkillSeries != null ? matchedSkillSeries.actionState() : -1) + "|action=" + hero.getBattleActionState(), "profile=" + HeroEpicFightDebugLog.itemKey(hero.getMainHandItem()) + ",anim=" + currentAnimation.registryName() + ",matchedSkillAction=" + (matchedSkillSeries != null ? matchedSkillSeries.actionState() : -1) + ",state=" + HeroEpicFightDebugLog.heroCoreState(hero));
        syncBattleActionState(hero, profile, currentAnimation, matchedSkillSeries);

        if (matchedSkillSeries == null) {
            return;
        }

        HeroNightfallSkillSpec effect = matchedSkillSeries.effectSpec() != null ? matchedSkillSeries.effectSpec() : profile.skillEffect();
        if (effect == null) {
            return;
        }

        if (player.getPrevElapsedTime() >= effect.triggerTime() || player.getElapsedTime() < effect.triggerTime()) {
            return;
        }

        HeroNightfallSkillEffects.apply(patch, hero, effect, matchedSkillSeries.anchorToWeaponJoint());
    }

    private static void syncBattleActionState(HeroEntity hero,
                                              NightfallProfile profile,
                                              @Nullable AssetAccessor<? extends StaticAnimation> currentAnimation,
                                              @Nullable SkillSeries matchedSkillSeries) {
        if (hero == null) {
            return;
        }

        int desiredState = -1;
        if (matchedSkillSeries != null && matchedSkillSeries.actionState() >= 0) {
            desiredState = matchedSkillSeries.actionState();
        } else if (currentAnimation != null) {
            int comboIndex = findMatchingComboIndex(profile, currentAnimation);
            if (comboIndex >= 0) {
                desiredState = resolveComboActionState(comboIndex);
            }
        }

        if (desiredState < 0) {
            if (hero.getBattleActionState() == HeroEntity.BATTLE_ACTION_HEAVY_HOLD) {
                int waitingTicks = hero.getBattleActionTicks() + 1;
                hero.setBattleActionTicks(waitingTicks);
                if (waitingTicks <= 8) {
                    return;
                }
            }

            if (isTrackedAttackState(hero.getBattleActionState())) {
                HeroEpicFightDebugLog.event(hero, "HeroNightfallMovesets.syncBattleActionState", "resetTrackedState,state=" + HeroEpicFightDebugLog.heroCoreState(hero) + ",currentAnimation=" + (currentAnimation != null ? currentAnimation.registryName() : "null"));
                hero.resetBattleActionTimeline();
            }
            return;
        }

        if (hero.getBattleActionState() != desiredState) {
            HeroEpicFightDebugLog.event(hero, "HeroNightfallMovesets.syncBattleActionState", "desiredState=" + desiredState + ",prevState=" + hero.getBattleActionState() + ",currentAnimation=" + (currentAnimation != null ? currentAnimation.registryName() : "null"));
            hero.setBattleActionState(desiredState);
            hero.setBattleActionTicks(0);
        }
    }

    private static int findMatchingComboIndex(NightfallProfile profile, AssetAccessor<? extends StaticAnimation> currentAnimation) {
        for (int comboIndex = 0; comboIndex < profile.comboAnimations().size(); comboIndex++) {
            if (profile.comboAnimations().get(comboIndex).matches(currentAnimation)) {
                return comboIndex;
            }
        }

        return -1;
    }

    @Nullable
    private static SkillSeries findMatchingSkillSeries(NightfallProfile profile, AssetAccessor<? extends StaticAnimation> currentAnimation) {
        for (SkillSeries skillSeries : profile.skillSeries()) {
            if (skillSeries.matches(currentAnimation)) {
                return skillSeries;
            }
        }

        return null;
    }

    @Nullable
    private static NightfallProfile resolve(ItemStack stack) {
        if (!ModList.get().isLoaded(EFN_MOD_ID) || stack == null || stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !EFN_MOD_ID.equals(itemId.getNamespace())) {
            return null;
        }

        return PROFILES.get(itemId.getPath().toLowerCase());
    }

    private static void register(NightfallProfile profile) {
        for (String itemPath : profile.itemPaths()) {
            PROFILES.put(itemPath, profile);
        }
    }

    private static NightfallProfile profile(Set<String> itemPaths,
                                            double attackRadius,
                                            boolean combatSafe,
                                            Map<LivingMotion, AnimationField> livingOverrides,
                                            List<AnimationField> comboAnimations,
                                            List<SkillSeries> skillSeries,
                                            @Nullable HeroNightfallSkillSpec skillEffect) {
        return new NightfallProfile(itemPaths, attackRadius, combatSafe, livingOverrides, comboAnimations, skillSeries, skillEffect);
    }

    private static Set<String> paths(String... itemPaths) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Arrays.stream(itemPaths).map(String::toLowerCase).forEach(set::add);
        return Set.copyOf(set);
    }

    private static Map<LivingMotion, AnimationField> living(Object... entries) {
        LinkedHashMap<LivingMotion, AnimationField> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put((LivingMotion) entries[i], (AnimationField) entries[i + 1]);
        }
        return Map.copyOf(map);
    }

    private static List<AnimationField> combo(AnimationField... animations) {
        return List.of(animations);
    }

    private static SkillSeries skill(float weight, int cooldown, double minDistance, double maxDistance, AnimationField... animations) {
        return skill(weight, cooldown, minDistance, maxDistance, 1.0F, animations);
    }

    private static SkillSeries skill(float weight, int cooldown, double minDistance, double maxDistance, float chance, AnimationField... animations) {
        return new SkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), null, -1, false, false);
    }

    private static SkillSeries skill(float weight,
                                     int cooldown,
                                     double minDistance,
                                     double maxDistance,
                                     float chance,
                                     @Nullable HeroNightfallSkillSpec effectSpec,
                                     AnimationField... animations) {
        return new SkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), effectSpec, -1, false, false);
    }

    private static SkillSeries airSkill(float weight, int cooldown, double minDistance, double maxDistance, float chance, AnimationField... animations) {
        return new SkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), null, -1, false, true);
    }

    private static SkillSeries skill(float weight,
                                     int cooldown,
                                     double minDistance,
                                     double maxDistance,
                                     float chance,
                                     int actionState,
                                     @Nullable HeroNightfallSkillSpec effectSpec,
                                     boolean anchorToWeaponJoint,
                                     AnimationField... animations) {
        return new SkillSeries(weight, cooldown, minDistance, maxDistance, chance, List.of(animations), effectSpec, actionState, anchorToWeaponJoint, false);
    }

    @Nullable
    private static HeroNightfallSkillSpec effect(HeroNightfallSkillEffects.EffectType effectType, float triggerTime, float bonusDamage, double radius) {
        return new HeroNightfallSkillSpec(effectType, triggerTime, bonusDamage, radius);
    }

    private static AnimationField field(String owner, String fieldName) {
        return new AnimationField(owner, fieldName);
    }

    private static CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createAnimationBehavior(AnimationAccessor<? extends StaticAnimation> animation,
                                                                                                  int actionState,
                                                                                                  boolean swingMainHand) {
        return createAnimationBehavior(animation, actionState, swingMainHand, false);
    }

    private static CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createAnimationBehavior(AnimationAccessor<? extends StaticAnimation> animation,
                                                                                                   int actionState,
                                                                                                   boolean swingMainHand,
                                                                                                   boolean launchAirAttack) {
        return CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder().behavior(mobPatch -> {
            if (!isPlayableAttackAnimation(animation)) {
                return;
            }

            if (mobPatch instanceof HeroEpicFightPatch heroPatch) {
                HeroEntity hero = heroPatch.getOriginal();
                if (hero != null && launchAirAttack) {
                    launchHeroAirAttack(hero);
                }
                if (hero != null && actionState >= 0) {
                    hero.beginBattleAction(actionState);
                }
                if (hero != null) {
                    hero.swing(InteractionHand.MAIN_HAND);
                }
            }

            mobPatch.playAnimationSynchronized(animation, 0.0F);
        });
    }

    private static boolean isPlayableAttackAnimation(@Nullable AnimationAccessor<? extends StaticAnimation> animation) {
        return animation != null && !animation.isEmpty();
    }

    private static boolean canStartAirAttack(HumanoidMobPatch<?> mobPatch, double maxDistance) {
        HeroEntity hero = getHero(mobPatch);
        if (hero == null || !hero.isAlive() || hero.isPassenger() || hero.isInWater() || hero.isInLava()) {
            return false;
        }

        LivingEntity target = getTrackedTarget(hero);
        NightfallProfile profile = resolve(hero.getMainHandItem());
        HeroCombatPlanner.CombatTuning tuning = profile != null ? getNightfallCombatTuning(profile) : HeroCombatPlanner.CombatTuning.comboOnly(maxDistance);
        return target != null
                && (hero.onGround() || hero.getDeltaMovement().y > -0.7D)
                && !HeroCombatPlanner.isAttackReplayLocked(hero, 8)
                && HeroCombatPlanner.canStartAirAttack(hero, target, maxDistance, 5)
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.AIR);
    }

    private static boolean canContinueSkillSeries(HumanoidMobPatch<?> mobPatch) {
        HeroEntity hero = getHero(mobPatch);
        if (hero == null) {
            return true;
        }

        LivingEntity target = getTrackedTarget(hero);
        return target != null && hero.isBattleModeActive();
    }

    private static boolean canStartSkillSeries(HumanoidMobPatch<?> mobPatch, SkillSeries skillSeries) {
        if (skillSeries.airAttack()) {
            return canStartAirAttack(mobPatch, skillSeries.maxDistance());
        }

        HeroEntity hero = getHero(mobPatch);
        if (hero == null) {
            return true;
        }

        LivingEntity target = getTrackedTarget(hero);
        if (target == null || !hero.isBattleModeActive() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }

        NightfallProfile profile = resolve(hero.getMainHandItem());
        HeroCombatPlanner.CombatTuning tuning = profile != null ? getNightfallCombatTuning(profile) : HeroCombatPlanner.CombatTuning.comboOnly(skillSeries.maxDistance());

        if (isProtectedComboStartup(hero) || HeroCombatPlanner.isAttackReplayLocked(hero, 7)) {
            return false;
        }

        int windupTicks = estimateSkillWindupTicks(skillSeries);
        double predictedDistanceSqr = HeroCombatPlanner.predictedDistanceSqr(hero, target, windupTicks, 0.35D);
        double relaxedMinDistance = Math.max(0.0D, skillSeries.minDistance() - 0.35D);
        if (predictedDistanceSqr < relaxedMinDistance * relaxedMinDistance) {
            return false;
        }

        return HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, skillSeries.maxDistance(), windupTicks)
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.SKILL);
    }

    private static boolean isProtectedComboStartup(HeroEntity hero) {
        return hero.isBattleTapAction() && hero.getBattleActionTicks() > 0 && hero.getBattleActionTicks() <= 5;
    }

    private static int estimateSkillWindupTicks(SkillSeries skillSeries) {
        double range = skillSeries.maxDistance() - skillSeries.minDistance();
        if (range >= 5.0D) {
            return 8;
        }
        if (range >= 3.0D) {
            return 6;
        }
        return 5;
    }

    private static void launchHeroAirAttack(HeroEntity hero) {
        hero.setFloating(false);
        hero.setNoGravity(false);

        Vec3 currentMovement = hero.getDeltaMovement();
        double forwardX = currentMovement.x;
        double forwardZ = currentMovement.z;
        LivingEntity target = hero.getTarget();
        if (target != null) {
            double dx = target.getX() - hero.getX();
            double dz = target.getZ() - hero.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 1.0E-4D) {
                forwardX = dx / horizontalDistance * AIR_ATTACK_FORWARD_SPEED;
                forwardZ = dz / horizontalDistance * AIR_ATTACK_FORWARD_SPEED;
            }
        }

        double jumpY = hero.onGround() ? AIR_ATTACK_JUMP_Y : Math.max(currentMovement.y, AIR_ATTACK_JUMP_Y * 0.55D);
        hero.setDeltaMovement(forwardX, jumpY, forwardZ);
        hero.hasImpulse = true;
    }

    private static boolean canStartCrimsonMoonCombo(HumanoidMobPatch<?> mobPatch, NightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        return (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD)
                && canStartDefaultCombo(mobPatch, profile, comboIndex, comboRange);
    }

    private static boolean canContinueCrimsonMoonCombo(HumanoidMobPatch<?> mobPatch, NightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        return (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD)
                && canContinueDefaultCombo(mobPatch, profile, comboIndex, comboRange);
    }

    private static boolean canStartCrimsonMoonHarvest(HumanoidMobPatch<?> mobPatch, SkillSeries harvest) {
        HeroEntity hero = getHero(mobPatch);
        return (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD)
                && canStartSkillSeries(mobPatch, harvest);
    }

    private static boolean canStartCrimsonMoonRelease(HumanoidMobPatch<?> mobPatch, SkillSeries scarletEnd) {
        HeroEntity hero = getHero(mobPatch);
        if (hero == null || hero.getBattleActionState() != HeroEntity.BATTLE_ACTION_HEAVY_HOLD || hero.getBattleActionTicks() < 6) {
            return false;
        }

        LivingEntity target = getTrackedTarget(hero);
        return target != null
                && HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, scarletEnd.maxDistance(), estimateSkillWindupTicks(scarletEnd));
    }

    @Nullable
    private static HeroEntity getHero(HumanoidMobPatch<?> mobPatch) {
        return mobPatch instanceof HeroEpicFightPatch heroPatch ? heroPatch.getOriginal() : null;
    }

    @Nullable
    private static LivingEntity getTrackedTarget(HeroEntity hero) {
        if (hero == null) {
            return null;
        }

        LivingEntity target = hero.getTarget();
        return target != null && target.isAlive() && !target.isRemoved() ? target : null;
    }

    private static boolean canStartDefaultCombo(HumanoidMobPatch<?> mobPatch, NightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        LivingEntity target = getTrackedTarget(hero);
        if (hero == null || target == null) {
            return hero == null;
        }

        HeroCombatPlanner.CombatTuning tuning = getNightfallCombatTuning(profile);

        return hero.isBattleModeActive()
                && !hero.isBattleHoldAction()
                && !hero.isBattleReleaseAction()
                && HeroCombatPlanner.canStartPredictedMeleeCombo(hero, target, Math.max(comboRange, profile.attackRadius() + 0.45D), 4 + Math.min(comboIndex, 3))
                && HeroCombatPlanner.prefersAction(hero, target, tuning, HeroCombatPlanner.PlannedAction.LIGHT_COMBO);
    }

    private static boolean canContinueDefaultCombo(HumanoidMobPatch<?> mobPatch, NightfallProfile profile, int comboIndex, double comboRange) {
        HeroEntity hero = getHero(mobPatch);
        LivingEntity target = getTrackedTarget(hero);
        if (hero == null || target == null) {
            return hero == null;
        }

        if (!hero.isBattleModeActive() || hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            return false;
        }

        double followUpReach = Math.max(comboRange, profile.attackRadius() + 0.75D);
        return HeroCombatPlanner.canQueueComboFollowUp(hero, target, followUpReach * followUpReach, 3 + Math.min(comboIndex, 2));
    }

    private static HeroCombatPlanner.CombatTuning getNightfallCombatTuning(NightfallProfile profile) {
        double comboMaxDistance = Math.max(3.6D, profile.attackRadius() + 0.6D);
        double airMaxDistance = 0.0D;
        double skillMinDistance = Double.MAX_VALUE;
        double skillMaxDistance = 0.0D;
        boolean airAvailable = false;
        boolean skillAvailable = false;

        for (SkillSeries skillSeries : profile.skillSeries()) {
            if (skillSeries.airAttack()) {
                airAvailable = true;
                airMaxDistance = Math.max(airMaxDistance, skillSeries.maxDistance());
            } else {
                skillAvailable = true;
                skillMinDistance = Math.min(skillMinDistance, skillSeries.minDistance());
                skillMaxDistance = Math.max(skillMaxDistance, skillSeries.maxDistance());
            }
        }

        if (!skillAvailable) {
            skillMinDistance = 0.0D;
        }

        return new HeroCombatPlanner.CombatTuning(
                comboMaxDistance,
                0.0D,
                0.0D,
                airMaxDistance,
                skillMinDistance,
                skillMaxDistance,
                false,
                airAvailable,
                skillAvailable
        );
    }

    private static int resolveComboActionState(int comboIndex) {
        return comboIndex % 2 == 0 ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
    }

    private static boolean isTrackedAttackState(int actionState) {
        return actionState == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1
                || actionState == HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2
                || actionState == HeroEntity.BATTLE_ACTION_HEAVY_HOLD
                || actionState == HeroEntity.BATTLE_ACTION_HEAVY_RELEASE;
    }

    private static boolean isCrimsonMoon(ItemStack stack) {
        ResourceLocation itemId = stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null && EFN_MOD_ID.equals(itemId.getNamespace()) && CRIMSON_MOON_PATH.equals(itemId.getPath().toLowerCase(Locale.ROOT));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static AnimationAccessor<? extends StaticAnimation> resolveField(String owner, String fieldName) {
        AnimationAccessor<? extends StaticAnimation> original = resolveOriginalField(owner, fieldName);
        return HeroNightfallAnimationRegistry.remap(original);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static AnimationAccessor<? extends StaticAnimation> resolveOriginalField(String owner, String fieldName) {
        String key = owner + "#" + fieldName;
        AnimationAccessor<? extends StaticAnimation> cached = ANIMATION_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED_FIELDS.contains(key)) {
            return null;
        }

        try {
            Class<?> ownerClass = Class.forName(owner, false, HeroNightfallMovesets.class.getClassLoader());
            Field field;
            try {
                field = ownerClass.getField(fieldName);
            } catch (NoSuchFieldException ignored) {
                field = ownerClass.getDeclaredField(fieldName);
                field.setAccessible(true);
            }
            Object value = field.get(null);
            if (value instanceof AnimationAccessor<?> animation && (!animation.isEmpty() || isDeferredEfnAnimationOwner(owner))) {
                AnimationAccessor<? extends StaticAnimation> casted = (AnimationAccessor<? extends StaticAnimation>) animation;
                ANIMATION_CACHE.put(key, casted);
                return casted;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        try {
            AnimationAccessor<? extends StaticAnimation> animation = AnimationManager.byKey(ResourceLocation.fromNamespaceAndPath(EFN_MOD_ID, fieldName.toLowerCase(Locale.ROOT)));
            if (animation != null && !animation.isEmpty()) {
                ANIMATION_CACHE.put(key, animation);
                return animation;
            }
        } catch (RuntimeException ignored) {
        }

        if (!isDeferredEfnAnimationOwner(owner)) {
            FAILED_FIELDS.add(key);
        }
        return null;
    }

    private static void addResolvedOriginalAnimation(Set<AnimationAccessor<? extends StaticAnimation>> animations, AnimationField animationField) {
        AnimationAccessor<? extends StaticAnimation> animation = animationField.resolveOriginal();
        if (animation != null && !animation.isEmpty() && EFN_MOD_ID.equals(animation.registryName().getNamespace())) {
            animations.add(animation);
        }
    }

    private record NightfallProfile(Set<String> itemPaths,
                                    double attackRadius,
                                    boolean combatSafe,
                                    Map<LivingMotion, AnimationField> livingOverrides,
                                    List<AnimationField> comboAnimations,
                                    List<SkillSeries> skillSeries,
                                    @Nullable HeroNightfallSkillSpec skillEffect) {
    }

    private record SkillSeries(float weight,
                               int cooldown,
                               double minDistance,
                               double maxDistance,
                               float chance,
                               List<AnimationField> animations,
                               @Nullable HeroNightfallSkillSpec effectSpec,
                               int actionState,
                               boolean anchorToWeaponJoint,
                               boolean airAttack) {
        private boolean matches(AssetAccessor<? extends StaticAnimation> currentAnimation) {
            for (AnimationField animation : this.animations) {
                if (animation.matches(currentAnimation)) {
                    return true;
                }
            }

            return false;
        }
    }

    private record AnimationField(String owner, String fieldName) {
        @Nullable
        private AnimationAccessor<? extends StaticAnimation> resolve() {
            return HeroNightfallMovesets.resolveField(this.owner, this.fieldName);
        }

        @Nullable
        private AnimationAccessor<? extends StaticAnimation> resolveOriginal() {
            return HeroNightfallMovesets.resolveOriginalField(this.owner, this.fieldName);
        }

        private boolean matches(AssetAccessor<? extends StaticAnimation> currentAnimation) {
            AnimationAccessor<? extends StaticAnimation> resolved = this.resolve();
            if (resolved != null && resolved.equals(currentAnimation)) {
                return true;
            }

            AnimationAccessor<? extends StaticAnimation> original = this.resolveOriginal();
            return original != null && original.equals(currentAnimation);
        }
    }
}


