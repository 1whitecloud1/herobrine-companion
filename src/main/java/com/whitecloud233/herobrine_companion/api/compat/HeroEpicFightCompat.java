package com.whitecloud233.herobrine_companion.api.compat;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.player.Player;
import java.util.Set;
import com.whitecloud233.herobrine_companion.combat.HeroCombatStateMapper;

/** Bundled optional integration API. Callers never need third-party types. */
public final class HeroEpicFightCompat {
    private static final String TARGET = "epicfight.HeroEpicFightCompat";

    private HeroEpicFightCompat() { }

    public enum BridgeStatus { UNINITIALIZED, NOT_INSTALLED, READY, API_MISMATCH }

    public static void onPoemModeChanged(Player player) {
        OptionalCompat.call(TARGET, "onPoemModeChanged", new Class<?>[]{Player.class}, player);
    }

    public static boolean canUsePoemGestures(Player player) {
        Object result = OptionalCompat.call(TARGET, "canUsePoemGestures", new Class<?>[]{Player.class}, player);
        return Boolean.TRUE.equals(result);
    }

    public static void queuePoemGesture(Player player, int gesture, int slot, int mode) {
        OptionalCompat.call(TARGET, "queuePoemGesture", new Class<?>[]{Player.class, int.class, int.class, int.class}, player, gesture, slot, mode);
    }

    public static boolean isLoaded() {
        Object result = OptionalCompat.call(TARGET, "isLoaded", new Class<?>[]{});
        return Boolean.TRUE.equals(result);
    }

    public static BridgeStatus getBridgeStatus() {
        Object result = OptionalCompat.call(TARGET, "getBridgeStatus", new Class<?>[]{});
        return result instanceof Enum<?> status ? BridgeStatus.valueOf(status.name()) : BridgeStatus.NOT_INSTALLED;
    }

    public static boolean isRuntimeBridgeReady() {
        Object result = OptionalCompat.call(TARGET, "isRuntimeBridgeReady", new Class<?>[]{});
        return Boolean.TRUE.equals(result);
    }

    public static String getCurrentBattleStateKey(HeroEntity hero) {
        return HeroCombatStateMapper.mapHeroBattleState(hero);
    }

    public static String getCurrentWeaponStyleKey(HeroEntity hero) {
        return HeroCombatStateMapper.mapWeaponStyle(hero);
    }

    public static String describeCurrentSnapshot(HeroEntity hero) {
        return HeroCombatStateMapper.describeCurrentSnapshot(hero);
    }

    public static Set<String> getExportedStateNames() {
        return HeroCombatStateMapper.getExportedStateNames();
    }

    public static boolean shouldUseEpicFightPose(HeroEntity hero) {
        Object result = OptionalCompat.call(TARGET, "shouldUseEpicFightPose", new Class<?>[]{HeroEntity.class}, hero);
        return Boolean.TRUE.equals(result);
    }

    public static boolean shouldUseEpicFightCombatAI(HeroEntity hero) {
        Object result = OptionalCompat.call(TARGET, "shouldUseEpicFightCombatAI", new Class<?>[]{HeroEntity.class}, hero);
        return Boolean.TRUE.equals(result);
    }

    public static boolean isHeroMidAttack(HeroEntity hero) {
        Object result = OptionalCompat.call(TARGET, "isHeroMidAttack", new Class<?>[]{HeroEntity.class}, hero);
        return Boolean.TRUE.equals(result);
    }

    public static String triggerSkill(HeroEntity hero, String skillName) {
        Object result = OptionalCompat.call(TARGET, "triggerSkill", new Class<?>[]{HeroEntity.class, String.class}, hero, skillName);
        return result instanceof String text ? text : null;
    }

    public static boolean shouldUseEpicFightHeldItemLayer(HeroEntity hero) {
        Object result = OptionalCompat.call(TARGET, "shouldUseEpicFightHeldItemLayer", new Class<?>[]{HeroEntity.class}, hero);
        return Boolean.TRUE.equals(result);
    }

    public static String getInstallHintKey() {
        return "gui.herobrine_companion.battle_mode_epicfight_hint";
    }

}
