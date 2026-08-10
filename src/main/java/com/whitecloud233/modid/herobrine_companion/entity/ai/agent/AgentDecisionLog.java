package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 决策日志：有界环形缓冲，保留最近 N 次决策，供"Agent 解释自己"与调试（对应设计文档 4.9）。
 *
 * <p>单一职责：只做"记录 + 回放最近决策"，不参与决策逻辑。</p>
 */
public final class AgentDecisionLog {

    private static final int DEFAULT_CAPACITY = 48;

    private final int capacity;
    private final Deque<AgentDecision> ring = new ArrayDeque<>();

    public AgentDecisionLog() {
        this(DEFAULT_CAPACITY);
    }

    public AgentDecisionLog(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** 记录一次决策；满员时淘汰最旧一条。 */
    public void record(AgentDecision decision) {
        if (decision == null) {
            return;
        }
        ring.addLast(decision);
        while (ring.size() > capacity) {
            ring.removeFirst();
        }
    }

    /** 最近一次决策的单行描述；无记录时返回说明文本。 */
    public String explainLast() {
        if (ring.isEmpty()) {
            return "（尚无决策）";
        }
        return ring.peekLast().line();
    }

    /** 最近 n 次决策的可读列表（新的在前）。 */
    public List<String> recent(int n) {
        List<String> result = new ArrayList<>();
        java.util.Iterator<AgentDecision> it = ring.descendingIterator();
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
}