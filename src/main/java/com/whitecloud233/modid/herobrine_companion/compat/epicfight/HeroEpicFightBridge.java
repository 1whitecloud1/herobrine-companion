package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationRegistryEvent;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

import java.util.Locale;
import java.util.Map;

public final class HeroEpicFightBridge {
    private static boolean registered;
    private static boolean armatureRegistered;
    private static final AssetAccessor<HumanoidArmature> HERO_NIGHTFALL_ARMATURE = Armatures.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "entity/hero_biped_nightfall"),
            HumanoidArmature::new
    );

    /**
     * hero_use_skill 工具接受的中文友好别名 → EFN 动画字段名。
     * 未命中的输入按原样（大小写不敏感）当作动画字段名匹配。
     */
    private static final Map<String, String> SKILL_ALIASES = Map.ofEntries(
            Map.entry("blast_sword", "YAMATO_DIVORCE_AUTO1"),
            Map.entry("heavy_rain", "YAMATO_DIVORCE_AUTO2"),
            Map.entry("damocles", "YAMATO_DIVORCE_AUTO3"),
            Map.entry("flarecut", "YAMATO_FLARECUT"),
            Map.entry("orbit", "YAMATO_ORBIT_1"),
            Map.entry("volcanol", "YAMATO_VOLCANOL"),
            Map.entry("upper_slash", "YAMATO_UPPERSLASH"),
            Map.entry("drive", "YAMATO_DRIVE"),
            Map.entry("repay_slash", "YAMATO_REPAIDSLASH"),
            Map.entry("judgement_cut", "YAMATO_JUDEMENCUT"),
            Map.entry("summoned_sword", "YAMATO_DIVORCE_AUTO1"),
            Map.entry("aerial_rave", "YAMATO_AERIALRAVE_AUTO1"),
            Map.entry("killer_bee", "YAMATO_KILLERBEE"),
            Map.entry("zandatsu", "HF_MURASAMA_ZANDATSU"),
            Map.entry("counter", "HF_MURASAMA_COUNTER"),
            Map.entry("murasama_combo", "HF_MURASAMA_XXXY"),
            Map.entry("beast_roar", "NF_CLAW_BEASTROAR"),
            Map.entry("blood_lust", "NF_TACHI_BLOODLUST"),
            Map.entry("blood_harvest", "SCYTHE_HARVEST"),
            Map.entry("scarlet_end", "SCYTHE_SCARLET_END"),
            Map.entry("mortal_blade", "MORTAL_BLADE_1"),
            Map.entry("ichimonji", "ICHIMONJI_1"),
            Map.entry("dragon_flash", "DRAGON_FLASH")
    );

    /**
     * P6：别名 → 期望特效。多个系列共享同一首动画（如 YAMATO_DIVORCE_AUTO1 同时是爆裂剑/召唤剑
     * 系列首动画）时，用期望特效消歧义，避免命中"首匹配"而非意图系列。
     */
    private static final Map<String, HeroNightfallSkillEffects.EffectType> SKILL_EFFECT_PREFERENCES = Map.of(
            "summoned_sword", HeroNightfallSkillEffects.EffectType.YAMATO_SUMMONED_SWORD
    );

    private HeroEpicFightBridge() {}

    public static synchronized void register(IEventBus modEventBus) {
        if (registered || modEventBus == null) {
            return;
        }
        registered = true;
        modEventBus.addListener(HeroEpicFightBridge::onEntityPatchRegistry);
        modEventBus.addListener(HeroEpicFightBridge::onAnimationRegistry);
        // 骨架注册放在 FMLCommonSetupEvent：那时实体已注册（ModEntities.HERO.get() 可用）、
        // 且 Armature 资源可加载（mod 已进 ModList）。此前该逻辑在懒加载路径从未触发，
        // 导致 Hero 从未用上自带 Claw_R/Claw_L/wheel 的骨架资源 → 爪/刺轮模型脱节。
        modEventBus.addListener(HeroEpicFightBridge::onCommonSetup);
    }

    public static synchronized void registerArmature() {
        if (armatureRegistered) {
            return;
        }
        armatureRegistered = true;
        // 给 Hero 骨架补上爪/刺轮武器专用关节（Claw_R/Claw_L/wheel），使 EFN 专属动画的
        // 武器模型与命中框能正确绑定，避免模型脱节/技能无命中框
        HeroNightfallArmatureJoints.addNightfallWeaponJoints(HERO_NIGHTFALL_ARMATURE.get());
        Armatures.registerEntityTypeArmature(ModEntities.HERO.get(), HERO_NIGHTFALL_ARMATURE);
    }

    public static boolean isPatched(HeroEntity hero) {
        return hero != null && EpicFightCapabilities.getEntityPatch(hero, HeroEpicFightPatch.class) != null;
    }

    /** Hero 是否正处在 Epic Fight 攻击/动作状态（飞行追击据此让出空中连段、不落地打断）。 */
    public static boolean isHeroMidAttack(HeroEntity hero) {
        if (hero == null) {
            return false;
        }
        HeroEpicFightPatch patch = EpicFightCapabilities.getEntityPatch(hero, HeroEpicFightPatch.class);
        if (patch == null) {
            return false;
        }
        yesman.epicfight.api.animation.types.EntityState state = patch.getEntityState();
        return state != null && (state.attacking() || state.inaction());
    }

    /**
     * 让 Hero 立即施放一个夜幕技能（agent 工具 {@code hero_use_skill} 的桥接实现）。
     *
     * <p>按当前主手武器解析夜幕 profile，用技能名（别名或 EFN 动画字段名）定位技能系列，
     * 解析重映射动画后同步播放。特效由 {@code HeroNightfallSkillTicker} 在后续 tick
     * 按动画匹配自动触发，无需手动施加伤害。返回约定 {@code "OK|…"} / {@code "FAIL|…"}。</p>
     *
     * <p>强制动画在非战斗态会被 {@code sanitizeAnimatorBeforeTick} 重置，因此触发前确保战斗态。</p>
     */
    public static String triggerSkill(HeroEntity hero, String skillName) {
        if (hero == null) {
            return "FAIL|英雄不存在";
        }
        if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return "FAIL|试炼进行中，无法使用夜幕技能";
        }
        String raw = skillName == null ? "" : skillName.trim();
        if (raw.isEmpty()) {
            return "FAIL|未指定技能";
        }
        HeroNightfallProfile profile = HeroNightfallProfiles.resolve(hero.getMainHandItem());
        if (profile == null) {
            return "FAIL|未持有夜幕(EFN)武器，无法使用技能";
        }
        String alias = SKILL_ALIASES.get(raw.toLowerCase(Locale.ROOT));
        String fieldName = alias != null ? alias : raw;
        // P6：共享首动画的系列按期望特效消歧义（如 summoned_sword → 召唤剑系列而非首匹配的爆裂剑）。
        HeroNightfallSkillEffects.EffectType preference = alias == null ? null
                : SKILL_EFFECT_PREFERENCES.get(raw.toLowerCase(Locale.ROOT));
        HeroNightfallSkillSeries series = findSeriesByField(profile, fieldName, preference);
        if (series == null) {
            return "FAIL|当前武器没有技能 " + raw;
        }
        AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = series.animations().get(0).resolve();
        if (animation == null || animation.isEmpty()) {
            return "FAIL|技能动画未加载: " + fieldName;
        }
        HeroEpicFightPatch patch = EpicFightCapabilities.getEntityPatch(hero, HeroEpicFightPatch.class);
        if (patch == null) {
            return "FAIL|英雄战斗补丁未就绪";
        }

        // P6：冷却（受 heroSkillSession 门控）。
        if (Config.heroSkillSession) {
            int cooldownTicks = series.cooldown();
            if (cooldownTicks > 0 && HeroSkillCooldownStore.isOnCooldown(
                    hero, fieldName, cooldownTicks, hero.level().getGameTime())) {
                return "FAIL|技能冷却中，请稍后再试";
            }
        }

        // P6：记录"工具是否自动进了战斗态"，动画结束后由 HeroSkillSessionTracker 决定是否退出。
        boolean autoEnteredBattleMode = !hero.isBattleModeActive();
        if (autoEnteredBattleMode) {
            hero.setBattleModeActiveFrom("HeroEpicFightBridge.triggerSkill", true);
        }
        if (series.actionState() >= 0) {
            hero.beginBattleAction(series.actionState());
        }
        patch.playAnimationSynchronized(animation, 0.0F);

        if (Config.heroSkillSession) {
            HeroSkillCooldownStore.markUsed(hero, fieldName, hero.level().getGameTime());
            HeroSkillSessionTracker.begin(hero, series, autoEnteredBattleMode);
        }
        return "OK|已施放技能 " + raw;
    }

    private static HeroNightfallSkillSeries findSeriesByField(HeroNightfallProfile profile, String fieldName,
                                                              @org.jetbrains.annotations.Nullable HeroNightfallSkillEffects.EffectType preference) {
        HeroNightfallSkillSeries fallback = null;
        for (HeroNightfallSkillSeries series : profile.skillSeries()) {
            boolean fieldMatch = false;
            for (HeroNightfallAnimationField field : series.animations()) {
                if (field.fieldName().equalsIgnoreCase(fieldName)) {
                    fieldMatch = true;
                    break;
                }
            }
            if (!fieldMatch) {
                continue;
            }
            if (preference != null && series.effectSpec() != null && series.effectSpec().effectType() == preference) {
                return series;
            }
            if (fallback == null) {
                fallback = series;
            }
        }
        return fallback;
    }

    static AssetAccessor<HumanoidArmature> heroNightfallArmature() {
        return HERO_NIGHTFALL_ARMATURE;
    }

    private static void onEntityPatchRegistry(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(ModEntities.HERO.get(), entity -> HeroEpicFightPatch::new);
    }

    private static void onAnimationRegistry(AnimationRegistryEvent event) {
        HeroNightfallAnimationRegistry.onAnimationRegistry(event);
    }

    private static void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(HeroEpicFightBridge::registerArmature);
    }
}


