package com.whitecloud233.herobrine_companion.client;

import java.util.Collections;
import java.util.Map;

/**
 * 单一职责：缓存服务端同步过来的玩家委托状态：
 * <ul>
 *   <li>进行中的委托 ID（0 = 无）；</li>
 *   <li>各委托的冷却结束时刻（世界游戏刻）——供 UI 锁定展示；</li>
 * </ul>
 * 供客户端交互决策使用（例如手持交付物品时跳过 Hero 主界面、委托界面冷却锁定）。
 * 更新发生在联网线程，volatile + 不可变快照即可保证可见性与一致性。
 */
public final class ClientQuestState {

    private static volatile int activeQuestId = 0;
    private static volatile Map<Integer, Long> cooldownEnds = Collections.emptyMap();

    private ClientQuestState() {
    }

    public static void setQuestState(int activeQuestId, Map<Integer, Long> cooldownEnds) {
        ClientQuestState.activeQuestId = activeQuestId;
        ClientQuestState.cooldownEnds = Map.copyOf(cooldownEnds);
    }

    public static int activeQuestId() {
        return activeQuestId;
    }

    /** 委托冷却结束时刻（世界游戏刻）；无冷却记录返回 0。 */
    public static long cooldownEnd(int questId) {
        return cooldownEnds.getOrDefault(questId, 0L);
    }

    /** 距冷却结束的剩余游戏刻；无冷却则返回 0。 */
    public static long cooldownRemaining(int questId, long nowGameTime) {
        long end = cooldownEnd(questId);
        return end > nowGameTime ? end - nowGameTime : 0L;
    }
}