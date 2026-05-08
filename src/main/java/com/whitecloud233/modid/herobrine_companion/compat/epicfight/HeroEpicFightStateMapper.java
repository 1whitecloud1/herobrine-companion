package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class HeroEpicFightStateMapper {
    public static final String HERO_BATTLE_IDLE = "hero_battle_idle";
    public static final String HERO_APPROACH = "hero_approach";
    public static final String HERO_LIGHT_COMBO_1 = "hero_light_combo_1";
    public static final String HERO_LIGHT_COMBO_2 = "hero_light_combo_2";
    public static final String HERO_HEAVY_FINISHER = "hero_heavy_finisher";
    public static final String HERO_CAST_THUNDER = "hero_cast_thunder";
    public static final String HERO_CHALLENGE_IDLE = "hero_challenge_idle";
    public static final String HERO_VOID_SHATTER_CHAIN = "hero_void_shatter_chain";
    public static final String HERO_REALM_BREAKER = "hero_realm_breaker";
    public static final String HERO_WEAPON_STYLE_BASIC = "hero_weapon_style_basic";
    public static final String HERO_WEAPON_STYLE_REALM_BREAKER = "hero_weapon_style_realm_breaker";
    public static final String HERO_WEAPON_STYLE_THUNDER = "hero_weapon_style_thunder";
    public static final String HERO_WEAPON_STYLE_VOID_SHATTER = "hero_weapon_style_void_shatter";
    private static final Set<String> EXPORTED_STATE_NAMES = createExportedStateNames();

    private HeroEpicFightStateMapper() {}

    private static Set<String> createExportedStateNames() {
        LinkedHashSet<String> states = new LinkedHashSet<>();
        states.add(HERO_BATTLE_IDLE);
        states.add(HERO_APPROACH);
        states.add(HERO_LIGHT_COMBO_1);
        states.add(HERO_LIGHT_COMBO_2);
        states.add(HERO_HEAVY_FINISHER);
        states.add(HERO_CAST_THUNDER);
        states.add(HERO_CHALLENGE_IDLE);
        states.add(HERO_VOID_SHATTER_CHAIN);
        states.add(HERO_REALM_BREAKER);
        states.add(HERO_WEAPON_STYLE_BASIC);
        states.add(HERO_WEAPON_STYLE_REALM_BREAKER);
        states.add(HERO_WEAPON_STYLE_THUNDER);
        states.add(HERO_WEAPON_STYLE_VOID_SHATTER);
        return Collections.unmodifiableSet(states);
    }

    public static Set<String> getExportedStateNames() {
        return EXPORTED_STATE_NAMES;
    }

    public static String mapHeroBattleState(HeroEntity hero) {
        if (hero == null) {
            return HERO_BATTLE_IDLE;
        }
        if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
            return HERO_CHALLENGE_IDLE;
        }
        if (hero.isCastingThunder()) {
            return HERO_CAST_THUNDER;
        }
        return switch (hero.getBattleActionState()) {
            case HeroEntity.BATTLE_ACTION_APPROACH -> HERO_APPROACH;
            case HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 -> HERO_LIGHT_COMBO_1;
            case HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2 -> HERO_LIGHT_COMBO_2;
            case HeroEntity.BATTLE_ACTION_HEAVY_HOLD,
                 HeroEntity.BATTLE_ACTION_HEAVY_RELEASE -> HERO_HEAVY_FINISHER;
            default -> HERO_BATTLE_IDLE;
        };
    }

    public static String mapPoemMode(ItemStack stack) {
        if (!(stack.getItem() instanceof PoemOfTheEndItem poem)) {
            return HERO_WEAPON_STYLE_BASIC;
        }
        return poem.getEpicFightWeaponStyleKey(stack);
    }

    public static String mapWeaponStyle(HeroEntity hero) {
        return hero == null ? HERO_WEAPON_STYLE_BASIC : mapPoemMode(hero.getMainHandItem());
    }

    public static String describeCurrentSnapshot(HeroEntity hero) {
        return mapHeroBattleState(hero) + "|" + mapWeaponStyle(hero);
    }
}

