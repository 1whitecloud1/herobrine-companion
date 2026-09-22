package com.whitecloud233.herobrine_companion.entity.gift;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 赠礼运行时内存态(不持久,等价 Bedrock _get_hero_runtime / _get_player_runtime)。
 *
 * <p>单一职责:持有英雄侧冷却/生日轨道与玩家侧共享餐标记;重启清零(与 Bedrock runtime 语义一致)。
 */
public final class HeroGiftRuntimeState {

    private HeroGiftRuntimeState() {
    }

    private static final Map<UUID, HeroRuntime> HERO = new ConcurrentHashMap<>();
    private static final Map<UUID, SharedMeal> SHARED_MEAL = new ConcurrentHashMap<>();

    public static HeroRuntime hero(UUID heroUuid) {
        return HERO.computeIfAbsent(heroUuid, k -> new HeroRuntime());
    }

    public static void removeHero(UUID heroUuid) {
        HERO.remove(heroUuid);
    }

    public static void setSharedMeal(UUID playerUuid, SharedMeal meal) {
        if (meal == null) {
            SHARED_MEAL.remove(playerUuid);
        } else {
            SHARED_MEAL.put(playerUuid, meal);
        }
    }

    public static SharedMeal getSharedMeal(UUID playerUuid) {
        return SHARED_MEAL.get(playerUuid);
    }

    public static void removePlayer(UUID playerUuid) {
        SHARED_MEAL.remove(playerUuid);
        HeroGiftBehaviorContext.release(playerUuid);
        // 台词"不连发同一句"记忆随玩家离线清理(等价 Bedrock release_player)
        HeroGiftLines.releasePlayer(playerUuid);
    }

    /** 英雄侧运行状态。 */
    public static final class HeroRuntime {
        public long cooldownUntil = 0;
        public long moodCooldownUntil = 0;
        public long requestCooldownUntil = 0;
        public long hintCooldownUntil = 0;
        public long nightGraceCooldownUntil = 0;
        public boolean nightGraceBlessed = false;
        public long birthdayOrbitUntil = 0;
    }

    /** 共享餐:玩家收礼后自己进食的热窗口。 */
    public record SharedMeal(UUID heroId, long offerTick, long expireTick) {
    }
}