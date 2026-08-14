package com.whitecloud233.herobrine_companion.client.agent;

import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;

/**
 * 客户端快照仓储（M5）：持有最近一份 agent 状态快照。
 *
 * <p><b>单一职责</b>：只做"存 / 取"。不发请求（由面板或命令发起），不渲染，不解析。
 * 面板与快照到达时机由此解耦——面板打开时先画已有快照，新包到达后自然刷新。</p>
 *
 * <p>字段 volatile：写在网络线程 enqueueWork 后的主线程、读在渲染线程，保证可见性。</p>
 */
public final class ClientAgentStatusStore {

    private static volatile AgentStatusSnapshot snapshot = AgentStatusSnapshot.empty();
    private static volatile long receivedAtMillis;

    private ClientAgentStatusStore() {
    }

    /** 收到新快照时覆盖（S→C 包唯一写入点）。 */
    public static void accept(AgentStatusSnapshot incoming) {
        snapshot = incoming == null ? AgentStatusSnapshot.empty() : incoming;
        receivedAtMillis = System.currentTimeMillis();
    }

    /** 最近一份快照；从未收到时返回空快照（不会是 null）。 */
    public static AgentStatusSnapshot snapshot() {
        return snapshot;
    }

    /** 是否已收到过至少一份快照。 */
    public static boolean hasData() {
        return receivedAtMillis > 0L;
    }

    /** 距上次收到快照的秒数；从未收到返回 -1。 */
    public static long ageSeconds() {
        return receivedAtMillis == 0L ? -1L : (System.currentTimeMillis() - receivedAtMillis) / 1000L;
    }

    /** 退出世界时清空，避免跨存档串数据。 */
    public static void clear() {
        snapshot = AgentStatusSnapshot.empty();
        receivedAtMillis = 0L;
    }
}
