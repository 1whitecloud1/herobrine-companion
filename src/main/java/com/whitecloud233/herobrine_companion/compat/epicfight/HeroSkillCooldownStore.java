package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能冷却追踪（P6）：按 Hero 记录每个技能键的最后施放 tick，供 {@code hero_use_skill} 工具
 * 在触发前检查 {@code HeroNightfallSkillSeries.cooldown()}。
 *
 * <p><b>单一职责</b>：只做"记 / 查"。冷却判定与响应文本在 {@code HeroEpicFightBridge}。</p>
 */
public final class HeroSkillCooldownStore {

    private static final Map<UUID, Map<String, Long>> LAST_USED = new ConcurrentHashMap<>();

    private HeroSkillCooldownStore() {
    }

    /** 该技能键是否在冷却中（cooldownTicks 为 0 表示无冷却，恒 false）。 */
    public static boolean isOnCooldown(HeroEntity hero, String skillKey, int cooldownTicks, long gameTime) {
        if (hero == null || skillKey == null || cooldownTicks <= 0) {
            return false;
        }
        Map<String, Long> byHero = LAST_USED.get(hero.getUUID());
        Long last = byHero == null ? null : byHero.get(skillKey);
        return last != null && gameTime - last < cooldownTicks;
    }

    /** 记录一次施放。 */
    public static void markUsed(HeroEntity hero, String skillKey, long gameTime) {
        if (hero == null || skillKey == null) {
            return;
        }
        LAST_USED.computeIfAbsent(hero.getUUID(), k -> new ConcurrentHashMap<>()).put(skillKey, gameTime);
    }

    /** 清空某 Hero 的冷却（实体销毁/重生时）。 */
    public static void clear(HeroEntity hero) {
        if (hero != null) {
            LAST_USED.remove(hero.getUUID());
        }
    }
}
