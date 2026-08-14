package com.whitecloud233.herobrine_companion.entity.ai.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 感知桶（perception bucket）：单次 Agent 循环内，各传感器写入观测的临时集散地。
 *
 * <p>单一职责：只负责"收集 + 限界 + 一次性取走"。上限防止无人消费时无限增长，
 * 也天然构成"注意力预算"的物理边界。</p>
 *
 * <p>低耦合：传感器向本桶写入，本桶向上游消费方一次性 {@link #takeAll() 取走}，
 * 双方互不知晓彼此存在。</p>
 */
public final class AgentPerceptionCollector {

    private static final int DEFAULT_CAPACITY = 64;

    private final int capacity;
    private final List<AgentObservation> bucket = new ArrayList<>();

    public AgentPerceptionCollector() {
        this(DEFAULT_CAPACITY);
    }

    public AgentPerceptionCollector(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** 写入一条观测；达到上限时丢弃并返回 false。 */
    public boolean add(AgentObservation observation) {
        if (observation == null) {
            return false;
        }
        if (bucket.size() >= capacity) {
            return false;
        }
        bucket.add(observation);
        return true;
    }

    /** 批量写入。 */
    public void addAll(Iterable<AgentObservation> observations) {
        if (observations == null) {
            return;
        }
        for (AgentObservation observation : observations) {
            add(observation);
        }
    }

    /** 取出当前全部观测并清空（一次性消费）。 */
    public List<AgentObservation> takeAll() {
        List<AgentObservation> snapshot = List.copyOf(bucket);
        bucket.clear();
        return snapshot;
    }

    public boolean isEmpty() {
        return bucket.isEmpty();
    }

    public int size() {
        return bucket.size();
    }
}