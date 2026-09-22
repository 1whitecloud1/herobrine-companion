package com.whitecloud233.herobrine_companion.entity.gift;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 赠礼行为上下文 —— Bedrock hero_player_offer_service 的运行时事件计数(内存态)。
 *
 * <p>单一职责:维护每玩家近 2400 tick 窗口内的击杀/补种修复/村庄伤害时间戳列表,
 * 剪枝并提供计数。窗口很短,按 Bedrock 同样只放内存(重启清零)。
 */
public final class HeroGiftBehaviorContext {

    public static final int WINDOW_TICKS = 2400;
    private static final int MAX_EVENTS = 32;

    private final List<Integer> killTicks = new ArrayList<>();
    private final List<Integer> careTicks = new ArrayList<>();
    private final List<Integer> villageHarmTicks = new ArrayList<>();

    // 服务端单例(primary server level 维度无关,按玩家 UUID)
    private static final Map<UUID, HeroGiftBehaviorContext> BY_PLAYER = new ConcurrentHashMap<>();

    public static HeroGiftBehaviorContext of(UUID playerUuid) {
        return BY_PLAYER.computeIfAbsent(playerUuid, k -> new HeroGiftBehaviorContext());
    }

    public static void release(UUID playerUuid) {
        BY_PLAYER.remove(playerUuid);
    }

    /** 当前窗口内的行为计数快照。 */
    public record Recent(int recentViolence, int recentCare, int recentVillageHarm) {
    }

    public void recordKill(int tick) {
        push(killTicks, tick);
    }

    public void recordCare(int tick) {
        push(careTicks, tick);
    }

    public void recordVillageHarm(int tick) {
        push(villageHarmTicks, tick);
    }

    /** 20 tick 内是否已记过村庄伤害(去抖,等价 Bedrock lastVillageHarmTick)。 */
    public boolean villageHarmRecently(int tick) {
        if (villageHarmTicks.isEmpty()) {
            return false;
        }
        int last = villageHarmTicks.get(villageHarmTicks.size() - 1);
        return tick - last < 20;
    }

    public Recent counts(int tick) {
        return new Recent(prune(killTicks, tick).size(),
                prune(careTicks, tick).size(),
                prune(villageHarmTicks, tick).size());
    }

    private void push(List<Integer> list, int tick) {
        list.add(tick);
        while (list.size() > MAX_EVENTS) {
            list.remove(0);
        }
    }

    private static List<Integer> prune(List<Integer> values, int tick) {
        int minimum = tick - WINDOW_TICKS;
        values.removeIf(v -> v < minimum);
        while (values.size() > MAX_EVENTS) {
            values.remove(0);
        }
        return values;
    }
}