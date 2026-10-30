package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 显式工具请求的<b>有界投递队列</b>（交接 A1/A2）。
 *
 * <p><b>解决什么</b>：原实现只有一个 {@code volatile pendingRequest} 字段，同一 Hero 在
 * 一个周期内收到两条请求时，后一条直接覆盖前一条——前一条既没执行也没回执，客户端只能
 * 等满超时。这里改成有容量上限的 FIFO：</p>
 * <ul>
 *   <li>同一 {@code requestId} 重放只保留一次（去重不执行第二次）；</li>
 *   <li>队满返回 {@link Offer#FULL}，由调用方立刻回 busy，<b>绝不覆盖</b>已在等待的请求；</li>
 *   <li>出队后可查最近终态，供迟到回执 / 重复确认判定"这个 ID 已经处理过了"；</li>
 *   <li>实体移除、换世界、切归属时 {@link #clear()} 统一释放。</li>
 * </ul>
 *
 * <p><b>单一职责</b>：只管"收 / 取 / 记终态"，不校验参数、不执行工具、不发送网络包。
 * 线程安全：网络收包线程写入、服务端主线程消费，所有入口 synchronized，
 * 并且<b>不在持锁期间回调外部逻辑</b>（交接 A2 要求回调在仓储锁外执行）。</p>
 */
public final class AgentToolInbox {

    /** 队列容量：显式请求是玩家触发的短动作，8 条足够覆盖连点，超出即 busy。 */
    public static final int CAPACITY = 8;

    /** 终态记录条数（用于迟到回执与重复确认判定）。 */
    public static final int TERMINAL_HISTORY = 32;

    /** 投递结果。 */
    public enum Offer {
        /** 已入队，等待服务端消费。 */
        ACCEPTED,
        /** 相同 requestId 已在队列或刚处理完：不再执行第二次，也不再重复回执。 */
        DUPLICATE,
        /** 队满：调用方必须立刻回 busy，且不得覆盖已有请求。 */
        FULL
    }

    /** 一个请求的终态（用于迟到结果归属判定）。 */
    public record Terminal(UUID requestId, boolean ok, String note) {
    }

    private final int capacity;
    private final Deque<AgentToolRequest> queue = new ArrayDeque<>();
    private final LinkedHashSet<UUID> queuedIds = new LinkedHashSet<>();
    private final Map<UUID, Terminal> recentTerminals = new HashMap<>();
    private final Deque<UUID> terminalOrder = new ArrayDeque<>();

    public AgentToolInbox() {
        this(CAPACITY);
    }

    public AgentToolInbox(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** 投递一条显式请求（幂等）。 */
    public synchronized Offer offer(AgentToolRequest request) {
        if (request == null || request.toolId() == null || request.toolId().isBlank()) {
            return Offer.DUPLICATE;
        }
        UUID id = request.requestId();
        if (id != null && (queuedIds.contains(id) || recentTerminals.containsKey(id))) {
            return Offer.DUPLICATE;
        }
        if (queue.size() >= capacity) {
            return Offer.FULL;
        }
        queue.addLast(request);
        if (id != null) {
            queuedIds.add(id);
        }
        return Offer.ACCEPTED;
    }

    /**
     * 取走一条待处理请求；没有则返回 null。
     *
     * <p>显式请求按 FIFO 处理，但如果某条请求比队头更早到期（例如队头正在等确认），
     * 调用方仍会先拿到队头——过期判定由消费方统一做，这里不自作主张丢弃。</p>
     */
    public synchronized AgentToolRequest poll() {
        AgentToolRequest head = queue.pollFirst();
        if (head != null && head.requestId() != null) {
            queuedIds.remove(head.requestId());
        }
        return head;
    }

    /** 当前排队条数。 */
    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    /** 该 requestId 是否已知（排队中或已到达终态）。重复确认判定用。 */
    public synchronized boolean knows(UUID requestId) {
        return requestId != null && (queuedIds.contains(requestId) || recentTerminals.containsKey(requestId));
    }

    /** 记录一个请求的终态；超出上限淘汰最旧。 */
    public synchronized void rememberTerminal(UUID requestId, boolean ok, String note) {
        if (requestId == null) {
            return;
        }
        if (recentTerminals.containsKey(requestId)) {
            return;
        }
        recentTerminals.put(requestId, new Terminal(requestId, ok, note == null ? "" : note));
        terminalOrder.addLast(requestId);
        while (terminalOrder.size() > TERMINAL_HISTORY) {
            recentTerminals.remove(terminalOrder.removeFirst());
        }
    }

    /** 查询终态；没有返回 null。 */
    public synchronized Terminal terminal(UUID requestId) {
        return requestId == null ? null : recentTerminals.get(requestId);
    }

    /** 最近终态（新的在前），供状态页展示。 */
    public synchronized List<Terminal> recentTerminals(int max) {
        List<Terminal> result = new ArrayList<>();
        Iterator<UUID> it = terminalOrder.descendingIterator();
        while (it.hasNext() && result.size() < Math.max(0, max)) {
            Terminal terminal = recentTerminals.get(it.next());
            if (terminal != null) {
                result.add(terminal);
            }
        }
        return List.copyOf(result);
    }

    /** 清空排队与终态（退出世界 / 实体移除 / 归属切换）。 */
    public synchronized void clear() {
        queue.clear();
        queuedIds.clear();
        recentTerminals.clear();
        terminalOrder.clear();
    }
}
