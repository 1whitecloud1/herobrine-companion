package com.whitecloud233.herobrine_companion.entity.ai.agent.tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 工具调用审计日志（对应设计文档 4.4 第 5 条）。有界环形缓冲，记录每次调用尝试：
 * 谁（玩家）/ 何时 / 哪个工具 / 参数摘要 / 结果。
 *
 * <p>单一职责：只做"记录 + 回放"，不参与工具调度。任何尝试（含被校验/确认拒绝的）都留痕。</p>
 */
public final class AgentToolAudit {

    private static final int DEFAULT_CAPACITY = 64;

    private final int capacity;
    private final Deque<Entry> ring = new ArrayDeque<>();

    public AgentToolAudit() {
        this(DEFAULT_CAPACITY);
    }

    public AgentToolAudit(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** 追加一条审计记录；满员时淘汰最旧。 */
    public void record(Entry entry) {
        if (entry == null) {
            return;
        }
        ring.addLast(entry);
        while (ring.size() > capacity) {
            ring.removeFirst();
        }
    }

    /** 最近 n 条审计的可读列表（新的在前）。 */
    public List<String> recent(int n) {
        List<String> result = new ArrayList<>();
        var it = ring.descendingIterator();
        int count = 0;
        while (it.hasNext() && count < n) {
            result.add(it.next().line());
            count++;
        }
        return result;
    }

    public int size() {
        return ring.size();
    }

    /** 单条审计记录（不可变）。 */
    public record Entry(long gameTime, String toolId, String argsSummary, String action, String result) {
        String line() {
            return String.format("[t=%d] %s %s 参数[%s] -> %s", gameTime, toolId, action, argsSummary, result);
        }
    }
}