package com.whitecloud233.herobrine_companion.client.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端 agent 工具请求仓储：按 requestId 存留工具执行结果，
 * 并支持把结果关联到调用方 {@code CompletableFuture}（合成调用消费）。
 *
 * <p><b>单一职责</b>：只做"存 / 取 / 关联 / 释放"。不发请求、不渲染、不解析。</p>
 *
 * <p><b>释放路径</b>（交接 A2）：超时、取消、断线、正常完成都必须把 pending 里那条移除。
 * 旧实现只在结果包到达或退出世界时清理，{@code completeOnTimeout} 仅完成 future，
 * 于是长期无回包的请求会让 pending 无界增长。现在由 {@link #expire} 统一收口。</p>
 *
 * <p><b>锁外回调</b>：future 的完成动作一律在仓储锁<b>外</b>执行，避免下游合成逻辑
 * 重入本仓储或触发网络发送时死锁。</p>
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

    /** pending 上限：即使上游忘记调用 expire，也不会无限增长。 */
    private static final int MAX_PENDING = 32;

    private static final Map<UUID, AgentToolResultRecord> results = new LinkedHashMap<>();
    private static final Map<UUID, CompletableFuture<AgentToolResultRecord>> pending = new LinkedHashMap<>();
    private static volatile AgentToolResultRecord lastResult;

    private ClientAgentToolRequestStore() {
    }

    /** 登记一个待关联的调用；结果到达时自动完成 future。 */
    public static synchronized void track(UUID requestId, CompletableFuture<AgentToolResultRecord> future) {
        if (requestId == null || future == null) {
            return;
        }
        pending.put(requestId, future);
        // 兜底：pending 满了先淘汰最旧（并让它以 null 结束），绝不静默堆积。
        while (pending.size() > MAX_PENDING) {
            UUID oldest = pending.keySet().iterator().next();
            CompletableFuture<AgentToolResultRecord> stale = pending.remove(oldest);
            completeOutsideLock(stale, null);
        }
    }

    /**
     * 收到 S→C 结果时写入（唯一写入点）。
     *
     * <p>迟到结果仍然只归原 requestId：它可能已经超时释放，此时只记入有界结果表，
     * 不会变成"一次新的请求回复"，也不会再次执行任何东西。</p>
     */
    public static void complete(UUID requestId, String toolId, boolean ok, String message) {
        if (requestId == null) {
            return;
        }
        AgentToolResultRecord record = new AgentToolResultRecord(requestId, toolId, ok, message);
        CompletableFuture<AgentToolResultRecord> future;
        synchronized (ClientAgentToolRequestStore.class) {
            future = pending.remove(requestId);
            results.put(requestId, record);
            while (results.size() > MAX_RESULTS) {
                UUID oldest = results.keySet().iterator().next();
                results.remove(oldest);
            }
            lastResult = record;
        }
        completeOutsideLock(future, record);
    }

    /**
     * 请求超时 / 取消：完成并<b>移除</b> pending，杜绝无回包请求堆积。
     *
     * @return true 表示这次调用仍在等待、已被本项目释放；false 表示结果早已到达
     */
    public static boolean expire(UUID requestId) {
        CompletableFuture<AgentToolResultRecord> future;
        synchronized (ClientAgentToolRequestStore.class) {
            future = requestId == null ? null : pending.remove(requestId);
        }
        if (future == null) {
            return false;
        }
        completeOutsideLock(future, null);
        return true;
    }

    /** 取出某 requestId 的结果（消费一次后移除）。 */
    public static synchronized AgentToolResultRecord take(UUID requestId) {
        return requestId == null ? null : results.remove(requestId);
    }

    /** 最近一次结果（不消费）。 */
    public static AgentToolResultRecord recentResult() {
        return lastResult;
    }

    /** 当前等待中的条数（测试 / 可观测）。 */
    public static synchronized int pendingCount() {
        return pending.size();
    }

    /** 退出世界时清空，避免跨存档串数据。 */
    public static void clear() {
        Map<UUID, CompletableFuture<AgentToolResultRecord>> draining;
        synchronized (ClientAgentToolRequestStore.class) {
            draining = new LinkedHashMap<>(pending);
            pending.clear();
            results.clear();
            lastResult = null;
        }
        // 断线清理不阻塞、不串存档：先脱离锁，再逐个完成。
        draining.values().forEach(future -> completeOutsideLock(future, null));
    }

    /** 在仓储锁外完成 future（下游回调可能重入或发网络包）。 */
    private static void completeOutsideLock(CompletableFuture<AgentToolResultRecord> future,
                                            AgentToolResultRecord value) {
        if (future != null) {
            future.complete(value);
        }
    }
}
