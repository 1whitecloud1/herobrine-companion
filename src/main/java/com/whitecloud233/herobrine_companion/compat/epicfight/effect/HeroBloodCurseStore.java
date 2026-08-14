package com.whitecloud233.herobrine_companion.compat.epicfight.effect;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 赤月「血疫」叠层的唯一存储点（单一职责：只负责层数的增删查，不参与伤害/粒子）。
 * 服务端 tick 使用；键用弱引用避免实体被移除后泄漏。
 */
public final class HeroBloodCurseStore {
    public static final int MAX_LEVEL = 10;

    private static final Map<LivingEntity, Integer> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());

    private HeroBloodCurseStore() {
    }

    public static int getLevel(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        Integer level = LEVELS.get(entity);
        return level == null ? 0 : level;
    }

    public static int addLevel(LivingEntity entity, int amount) {
        if (entity == null) {
            return 0;
        }
        int next = Math.min(MAX_LEVEL, getLevel(entity) + amount);
        LEVELS.put(entity, next);
        return next;
    }

    public static int consumeLevels(LivingEntity entity, int amount) {
        if (entity == null || amount <= 0) {
            return 0;
        }
        int current = getLevel(entity);
        int consumed = Math.min(current, amount);
        int left = current - consumed;
        if (left <= 0) {
            LEVELS.remove(entity);
        } else {
            LEVELS.put(entity, left);
        }
        return consumed;
    }

    public static int clear(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        Integer level = LEVELS.remove(entity);
        return level == null ? 0 : level;
    }

    public static boolean hasAtLeast(LivingEntity entity, int min) {
        return getLevel(entity) >= min;
    }
}
