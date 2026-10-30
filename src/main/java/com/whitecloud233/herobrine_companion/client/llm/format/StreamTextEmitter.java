package com.whitecloud233.herobrine_companion.client.llm.format;

import java.util.function.Consumer;

/**
 * 流式回复的节流推送器。
 *
 * <p>SSE 流每收到一个 chunk 都会追加文本，如果每个 chunk 都回调一次
 * {@code partialConsumer}，界面会在一个网络往返内被刷新几十次，造成卡顿与
 * 无意义的排版重算。这里做两级节流：<b>距上次推送不足 40ms 且新增不足 2 字符时跳过</b>，
 * 保证既不会过于频繁，也不会在长间隔后卡住不更新。</p>
 *
 * <p><b>为什么单独抽出来：</b>四个协议适配器（OpenAI Chat / OpenAI Responses /
 * Anthropic / Gemini）各自复制了一份逐字符相同的实现，连内部的
 * {@code StreamEmitState} 状态类都是复制的。节流参数一旦需要调整（比如放宽到 50ms
 * 让低端机更流畅），改一处漏三处会让不同模型表现出不同的打字速度。</p>
 *
 * <p>本类有可变状态，每次流式请求创建一个实例，不要跨请求复用。</p>
 */
public final class StreamTextEmitter {

    /** 两次推送之间的最小间隔（毫秒）。 */
    private static final long MIN_INTERVAL_MS = 40L;

    /** 累计新增字符达到该数量时，即使未到间隔也立即推送。 */
    private static final int MIN_GROWTH_CHARS = 2;

    private long lastEmitAt;
    private int lastEmitLength;

    /**
     * 按需推送当前累计文本。
     *
     * @param partialConsumer 接收完整累计文本的回调；为 {@code null} 时什么都不做
     * @param replyBuilder    已累积的回复文本
     */
    public void emit(Consumer<String> partialConsumer, StringBuilder replyBuilder) {
        if (partialConsumer == null || replyBuilder.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int currentLength = replyBuilder.length();
        if ((now - this.lastEmitAt) < MIN_INTERVAL_MS
                && (currentLength - this.lastEmitLength) < MIN_GROWTH_CHARS) {
            return;
        }
        this.lastEmitAt = now;
        this.lastEmitLength = currentLength;
        partialConsumer.accept(replyBuilder.toString());
    }
}
