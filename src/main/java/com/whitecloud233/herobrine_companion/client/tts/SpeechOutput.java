package com.whitecloud233.herobrine_companion.client.tts;

import java.util.concurrent.CompletableFuture;

/**
 * "把一句话念出来"的一种实现。
 *
 * <p>约定：{@link #speak} 必须立刻返回、内部自行异步，绝不阻塞调用线程（聊天主线程）；
 * 失败通过 future 的异常表达，由 {@link TtsSpeechService} 统一记录，不影响聊天本身。</p>
 */
public interface SpeechOutput {

    CompletableFuture<Void> speak(String text, TtsOptions options);

    /** 立即打断当前正在播报的内容。 */
    void stop();

    /** 当前平台上该实现是否可用（决定设置页提示与回退）。 */
    boolean isAvailable();
}
