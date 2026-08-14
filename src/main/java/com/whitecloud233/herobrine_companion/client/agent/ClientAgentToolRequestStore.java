package com.whitecloud233.herobrine_companion.client.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端 agent 工具请求仓储（M4 共享基础设施）：按 requestId 存留工具执行结果，
 * 并支持把结果关联到调用方 {@code CompletableFuture}（P2 合成调用消费）。
 *
 * <p><b>单一职责</b>：只做"存 / 取 / 关联"。不发请求、不渲染、不解析。收包线程（主线程）
 * 与 LLM 编排线程都可能访问，方法统一 synchronized 保证安全。</p>
 */
public final class ClientAgentToolRequestStore {

    /** 单条工具结果（不可变）。 */
    public record AgentToolResultRecord(UUID requestId, String toolId, boolean ok, String message) {
        /** 供 LLM 上下文注入用的单行文本。 */
        public String line() {
            return toolId + " -> " + (ok ? "成功: " : "失败: ") + message;
        }
    }

    private static final int MAX_RESULTS = 16;

    private static final Map<UUID, AgentToolResultRecord> results = new LinkedHashMap<>();
    private static final Map<UUID, CompletableFuture<AgentToolResultRecord>> pending = new LinkedHashMap<>();
    private static volatile AgentToolResultRecord lastResult;

    private ClientAgentToolRequestStore() {
    }

    /** 登记一个待关联的调用（P2 合成路径用）；结果到达时自动完成 future。 */
    public static synchronized void track(UUID requestId, CompletableFuture<AgentToolResultRecord> future) {
        if (requestId == null || future == null) {
            return;
        }
        pending.put(requestId, future);
    }

    /** 收到 S→C 结果时写入（唯一写入点）。 */
    public static synchronized void complete(UUID requestId, String toolId, boolean ok, String message) {
        if (requestId == null) {
            return;
        }
        AgentToolResultRecord record = new AgentToolResultRecord(requestId, toolId, ok, message);
        CompletableFuture<AgentToolResultRecord> future = pending.remove(requestId);
        if (future != null) {
            future.complete(record);
        }
        results.put(requestId, record);
        while (results.size() > MAX_RESULTS) {
            UUID oldest = results.keySet().iterator().next();
            results.remove(oldest);
        }
        lastResult = record;
    }

    /** 取出某 requestId 的结果（消费一次后移除，供 P2 注入）。 */
    public static synchronized AgentToolResultRecord take(UUID requestId) {
        return requestId == null ? null : results.remove(requestId);
    }

    /** 最近一次结果（供 P2 下轮注入，不消费）。 */
    public static AgentToolResultRecord recentResult() {
        return lastResult;
    }

    /** 退出世界时清空，避免跨存档串数据。 */
    public static synchronized void clear() {
        for (CompletableFuture<AgentToolResultRecord> future : pending.values()) {
            future.complete(null);
        }
        pending.clear();
        results.clear();
        lastResult = null;
    }
}
