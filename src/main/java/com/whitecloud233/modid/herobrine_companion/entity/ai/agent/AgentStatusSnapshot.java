package com.whitecloud233.modid.herobrine_companion.entity.ai.agent;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 状态快照（M5）：服务端 agent 内部状态的<b>不可变只读投影</b>，用于跨端展示（设计文档 4.9）。
 *
 * <p><b>单一职责</b>：只承载数据 + 自我序列化。不采集（见 {@link AgentStatusCollector}）、
 * 不渲染（见客户端面板）、不做网络收发（见 {@code AgentStatusPacket}）。</p>
 *
 * <p>快照是"投影"而非"引用"：所有列表在构造期已 {@code List.copyOf} 成不可变副本，
 * 因此可以安全地从网络线程交给渲染线程，不与服务端可变状态共享内存。</p>
 */
public record AgentStatusSnapshot(
        long gameTime,
        String intention,
        String lastDecision,
        String mindState,
        int queueSize,
        String queueProgress,
        int memoryEpisodes,
        int memoryFacts,
        List<String> decisions,
        List<String> audits,
        List<String> queueLog,
        List<String> toolCatalog,
        List<String> memoryLines
) {

    /** 每类明细的传输条数上限：够看清因果链，又不让单包膨胀。 */
    public static final int MAX_LINES = 12;

    /** 单行最大长度（网络与渲染共用，避免 readUtf 越界）。 */
    public static final int MAX_LINE_CHARS = 256;

    public AgentStatusSnapshot {
        decisions = copyBounded(decisions);
        audits = copyBounded(audits);
        queueLog = copyBounded(queueLog);
        toolCatalog = copyBounded(toolCatalog);
        memoryLines = copyBounded(memoryLines);
        intention = truncate(intention);
        lastDecision = truncate(lastDecision);
        mindState = truncate(mindState);
        queueProgress = truncate(queueProgress);
    }

    /** 空快照：客户端尚未收到数据时的占位，避免 GUI 处理 null。 */
    public static AgentStatusSnapshot empty() {
        return new AgentStatusSnapshot(0L, "—", "（尚无决策）", "—", 0, "",
                0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** 队列是否空闲（GUI 用，避免重复判断 size）。 */
    public boolean isQueueIdle() {
        return queueSize <= 0;
    }

    // ---- 序列化（与 decode 严格对称） ----

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(gameTime);
        buf.writeUtf(intention, MAX_LINE_CHARS);
        buf.writeUtf(lastDecision, MAX_LINE_CHARS);
        buf.writeUtf(mindState, MAX_LINE_CHARS);
        buf.writeVarInt(queueSize);
        buf.writeUtf(queueProgress, MAX_LINE_CHARS);
        buf.writeVarInt(memoryEpisodes);
        buf.writeVarInt(memoryFacts);
        writeLines(buf, decisions);
        writeLines(buf, audits);
        writeLines(buf, queueLog);
        writeLines(buf, toolCatalog);
        writeLines(buf, memoryLines);
    }

    public static AgentStatusSnapshot decode(FriendlyByteBuf buf) {
        long gameTime = buf.readLong();
        String intention = buf.readUtf(MAX_LINE_CHARS);
        String lastDecision = buf.readUtf(MAX_LINE_CHARS);
        String mindState = buf.readUtf(MAX_LINE_CHARS);
        int queueSize = buf.readVarInt();
        String queueProgress = buf.readUtf(MAX_LINE_CHARS);
        int memoryEpisodes = buf.readVarInt();
        int memoryFacts = buf.readVarInt();
        return new AgentStatusSnapshot(gameTime, intention, lastDecision, mindState,
                queueSize, queueProgress, memoryEpisodes, memoryFacts,
                readLines(buf), readLines(buf), readLines(buf), readLines(buf), readLines(buf));
    }

    private static void writeLines(FriendlyByteBuf buf, List<String> lines) {
        buf.writeVarInt(lines.size());
        for (String line : lines) {
            buf.writeUtf(line, MAX_LINE_CHARS);
        }
    }

    private static List<String> readLines(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAX_LINES);
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(buf.readUtf(MAX_LINE_CHARS));
        }
        return lines;
    }

    /** 截断到 MAX_LINES 条、每条 MAX_LINE_CHARS 字符，保证 encode 一定不越界。 */
    private static List<String> copyBounded(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> bounded = new ArrayList<>(Math.min(source.size(), MAX_LINES));
        for (String line : source) {
            if (bounded.size() >= MAX_LINES) {
                break;
            }
            bounded.add(truncate(line));
        }
        return List.copyOf(bounded);
    }

    private static String truncate(String value) {
        String text = value == null ? "" : value;
        return text.length() <= MAX_LINE_CHARS ? text : text.substring(0, MAX_LINE_CHARS);
    }
}
