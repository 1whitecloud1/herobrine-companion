package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 待确认工具请求仓储：按 requestId 存留"需要玩家确认"的工具调用，等待审批包取走。
 *
 * <p><b>单一职责</b>：只做"入队 / 按 requestId 取走 / 过期判定"。审批动作在网络包侧
 * （{@code ApproveToolPacket} / {@code RejectToolPacket}），与注册表、执行器解耦。</p>
 *
 * <p><b>期限</b>（交接 A3）：审批有效期与服务端请求期是两件事，这里只管审批窗口。
 * 取值需<b>大于</b>客户端 30 秒等待上限，否则玩家在确认屏上正常停留也会被判过期；
 * 而服务端请求期（{@code AgentToolRequest.REQUEST_TTL_TICKS} = 20 秒）约束的是"从客户端
 * 发出到服务端受理/首次执行"，两者的语义不同，不能共用一个常量。</p>
 *
 * <p>有界：满员淘汰最旧，不覆盖；过期条目在 {@code take} 时按 gameTime 惰性丢弃。</p>
 */
public final class AgentToolConfirmationStore {

    private static final int MAX_ENTRIES = 32;

    /** 审批有效期（tick）：45 秒，须大于客户端 30 秒等待上限。 */
    public static final long APPROVAL_TTL_TICKS = 45L * 20L;

    private static final Map<UUID, ConfirmationEntry> PENDING = new LinkedHashMap<>();

    private AgentToolConfirmationStore() {
    }

    /**
     * 一条待确认的工具调用（不可变）。
     *
     * @param requesterUuid 发起请求的玩家；审批时必须与确认者一致
     * @param dimensionId   受理时所处维度；换维度后原审批不再适用（防止操作已过期的对象）
     */
    public record ConfirmationEntry(
            UUID requestId,
            String toolId,
            AgentToolArgs args,
            UUID requesterUuid,
            long gameTime,
            String description,
            String dimensionId) {
    }

    /** 入队（幂等覆盖同 requestId；满员淘汰最旧）。 */
    public static synchronized void enqueue(ConfirmationEntry entry) {
        if (entry == null || entry.requestId() == null) {
            return;
        }
        PENDING.put(entry.requestId(), entry);
        while (PENDING.size() > MAX_ENTRIES) {
            UUID oldest = PENDING.keySet().iterator().next();
            PENDING.remove(oldest);
        }
    }

    /** 按 requestId 取走（消费一次即移除）；过期条目直接丢弃返回 null。 */
    public static synchronized ConfirmationEntry take(UUID requestId, long currentGameTime) {
        ConfirmationEntry entry = requestId == null ? null : PENDING.remove(requestId);
        if (entry != null && isExpired(entry, currentGameTime)) {
            return null;
        }
        return entry;
    }

    /** 只查看不取走（重复确认判定 / 状态页）。 */
    public static synchronized ConfirmationEntry peek(UUID requestId) {
        return requestId == null ? null : PENDING.get(requestId);
    }

    /** 该 requestId 是否仍在等待审批且未过期。 */
    public static synchronized boolean isAwaiting(UUID requestId, long currentGameTime) {
        ConfirmationEntry entry = peek(requestId);
        return entry != null && !isExpired(entry, currentGameTime);
    }

    /** 主动作废（拒绝 / 取消 / 归属变化）。 */
    public static synchronized void discard(UUID requestId) {
        if (requestId != null) {
            PENDING.remove(requestId);
        }
    }

    public static boolean isExpired(ConfirmationEntry entry, long currentGameTime) {
        return entry == null || currentGameTime - entry.gameTime() > APPROVAL_TTL_TICKS;
    }

    /** 待确认条目数（调试 / 可观测）。 */
    public static synchronized int pendingCount() {
        return PENDING.size();
    }

    /** 清空（退出世界 / 调试）。 */
    public static synchronized void clear() {
        PENDING.clear();
    }
}
