package com.whitecloud233.herobrine_companion.client.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 后台 AI 调用的<b>调试日志</b>（环形缓冲）。
 *
 * <p>所有 LLM 调用（无论成功/失败）在响应落地时写入一条 {@link Entry}，供
 * {@link com.whitecloud233.herobrine_companion.client.gui.AIDebugScreen}
 * 查看"思考过程（reasoning_content）+ 实际返回的原始响应"。LLM 调用跑在 ForkJoinPool
 * 线程，GUI 跑在渲染线程，用 {@link ConcurrentLinkedDeque} 保证无锁并发安全。</p>
 */
public final class AIDebugLog {

    private static final int MAX_ENTRIES = 50;
    private static final ConcurrentLinkedDeque<Entry> ENTRIES = new ConcurrentLinkedDeque<>();

    /** 一次 LLM 调用的完整记录。 */
    public record Entry(long timestamp, String task, int statusCode, String endpoint, String model,
                        String reasoning, String reply, String toolCall, String rawBody, String error) {
        public boolean isSuccess() {
            return statusCode == 200;
        }
    }

    private AIDebugLog() {
    }

    /** 写入一条记录（新条目在最前）。 */
    static void record(String task, int statusCode, String endpoint, String model,
                       String reasoning, String reply, String toolCall, String rawBody, String error) {
        ENTRIES.addFirst(new Entry(System.currentTimeMillis(), task, statusCode, endpoint, model,
                reasoning, reply, toolCall, rawBody, error));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeLast();
        }
    }

    /** 最近记录（新 → 旧），供 GUI 展示。 */
    public static List<Entry> recent() {
        return new ArrayList<>(ENTRIES);
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
