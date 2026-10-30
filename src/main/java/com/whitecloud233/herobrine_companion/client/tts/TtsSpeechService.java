package com.whitecloud233.herobrine_companion.client.tts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * TTS 播报的唯一入口（客户端门面）。
 *
 * <p>职责边界：只做"是否该念 → 选引擎 → 去重 → 打断 → 派发"的编排，
 * 具体合成由 {@link SystemSpeechOutput} / {@link CloudSpeechOutput} 完成，
 * 配置读写归 {@link TtsSettings}，文本净化归 {@link TtsTextNormalizer}。</p>
 *
 * <p>任何失败都只写日志，绝不向聊天链路抛异常——语音是附加表现，不能影响对话。</p>
 */
public final class TtsSpeechService {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 同一句话在这个时间窗内重复到达只念一次（防多渠道重复触发/重复点击）。 */
    private static final long DUPLICATE_WINDOW_MS = 2_500L;

    private static final TtsSpeechService INSTANCE = new TtsSpeechService();

    private final Object lock = new Object();
    private SpeechOutput output;
    private TtsEngine outputEngine;
    private String lastSpokenText = "";
    private long lastSpokenAt = 0L;
    private boolean unavailabilityLogged = false;
    /** 最近一次失败原因（供设置页显示）；成功即清空。 */
    private volatile String lastError;
    private long attemptCount;
    private long successCount;

    private TtsSpeechService() {
    }

    public static TtsSpeechService getInstance() {
        return INSTANCE;
    }

    /**
     * 朗读 Herbrine 在<b>玩家发起的聊天</b>里的回复。
     *
     * <p>由 {@code ClientChatHandler} 的四个回复分支调用；总开关关闭时立即返回。</p>
     */
    public void speakChatReply(String replyText) {
        TtsOptions options = TtsSettings.activeSnapshot();
        if (options == null) {
            return;
        }
        speakNormalized(TtsTextNormalizer.normalize(replyText, options.maxChars()), options, false);
    }

    /**
     * 设置页「试听」：忽略总开关与去重，直接用当前参数念一句示例。
     */
    public CompletableFuture<Void> speakSample(String sampleText) {
        TtsOptions options = TtsSettings.snapshot();
        return speakNormalized(TtsTextNormalizer.normalize(sampleText, options.maxChars()), options, true);
    }

    /** 立即停止当前播报（新台词打断、退出世界、玩家主动停止）。 */
    public void stop() {
        SpeechOutput current;
        synchronized (this.lock) {
            current = this.output;
        }
        if (current != null) {
            current.stop();
        }
    }

    /** 设置保存后调用：丢弃按旧引擎构建的实现，下次朗读按新引擎重建。 */
    public void reload() {
        SpeechOutput previous;
        synchronized (this.lock) {
            previous = this.output;
            this.output = null;
            this.outputEngine = null;
            this.lastSpokenText = "";
            this.lastSpokenAt = 0L;
        }
        if (previous != null) {
            previous.stop();
        }
    }

    private CompletableFuture<Void> speakNormalized(String text, TtsOptions options, boolean bypassDedupe) {
        if (text.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!bypassDedupe && isDuplicate(text)) {
            return CompletableFuture.completedFuture(null);
        }

        SpeechOutput speaker = resolveOutput(options.engine());
        if (!speaker.isAvailable()) {
            logUnavailabilityOnce(options.engine());
            return CompletableFuture.completedFuture(null);
        }
        if (options.interruptCurrent()) {
            speaker.stop();
        }
        clearLastError();
        markAttempted();
        return speaker.speak(text, options).whenComplete((ignored, failure) -> {
            if (failure != null) {
                String reason = describe(failure);
                recordError(reason);
                LOGGER.warn("TTS 播报失败（{}）: {}", options.engine().id(), reason);
            } else {
                markSucceeded();
            }
        });
    }

    // ---------------- 供设置页显示的状态 ----------------

    /** 最近一次播报失败的原因；成功后清空。供设置页把问题直接显示给玩家。 */
    public String lastError() {
        return this.lastError;
    }

    /** 本实例已尝试过的播报次数（含试听），用于区分"还没点过"和"点了没反应"。 */
    public long attemptCount() {
        return this.attemptCount;
    }

    /** 本实例成功播出的次数。 */
    public long successCount() {
        return this.successCount;
    }

    public boolean isSpeaking() {
        SpeechOutput current;
        synchronized (this.lock) {
            current = this.output;
        }
        return current instanceof CloudSpeechOutput cloud && cloud.isPlaying()
                || current instanceof LocalSpeechOutput local && local.isPlaying();
    }

    /** 清掉错误记录（重新保存设置或再次试听时调用）。 */
    public void clearLastError() {
        synchronized (this.lock) {
            this.lastError = null;
        }
    }

    private void recordError(String reason) {
        synchronized (this.lock) {
            this.lastError = reason == null || reason.isBlank() ? "未知错误" : reason;
        }
    }

    private void markAttempted() {
        synchronized (this.lock) {
            this.attemptCount++;
        }
    }

    private void markSucceeded() {
        synchronized (this.lock) {
            this.successCount++;
            this.lastError = null;
        }
    }

    private boolean isDuplicate(String text) {
        synchronized (this.lock) {
            long now = System.currentTimeMillis();
            boolean duplicated = text.equals(this.lastSpokenText) && now - this.lastSpokenAt < DUPLICATE_WINDOW_MS;
            this.lastSpokenText = text;
            this.lastSpokenAt = now;
            return duplicated;
        }
    }

    /** 按引擎取（或重建）实现；引擎切换时先停掉旧实现，避免两种声音叠着响。 */
    private SpeechOutput resolveOutput(TtsEngine engine) {
        synchronized (this.lock) {
            if (this.output != null && this.outputEngine == engine) {
                return this.output;
            }
            if (this.output != null) {
                this.output.stop();
            }
            this.outputEngine = engine;
            this.output = createOutput(engine);
            this.unavailabilityLogged = false;
            return this.output;
        }
    }

    private static SpeechOutput createOutput(TtsEngine engine) {
        return switch (engine) {
            case SYSTEM -> new SystemSpeechOutput();
            case CLOUD -> new CloudSpeechOutput();
            case LOCAL -> new LocalSpeechOutput();
        };
    }

    private void logUnavailabilityOnce(TtsEngine engine) {
        synchronized (this.lock) {
            if (this.unavailabilityLogged) {
                return;
            }
            this.unavailabilityLogged = true;
        }
        LOGGER.warn("语音引擎 {} 在当前平台不可用，请在 TTS 设置页改用其他引擎", engine.id());
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
