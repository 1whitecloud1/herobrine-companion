package com.whitecloud233.herobrine_companion.client.tts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 播放云端/本地服务返回的 WAV 音频（{@code javax.sound.sampled} 原生支持，无需额外解码库）。
 *
 * <p>单一职责：只做"字节 → 声音"的播放、音量、打断，以及 WAV 头的识别与描述；
 * 请求、鉴权、协议差异都在各自的 {@link SpeechOutput} 里。</p>
 */
public final class WavAudioPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 驱动漏发终止事件时的兜底；正常结束完全由 LineListener 驱动。 */
    private static final long COMPLETION_GRACE_MS = 5_000L;
    private static final int RIFF_HEADER_LENGTH = 12;

    private final AtomicReference<Playback> activeClip = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final Object playbackLock = new Object();
    private final ClipFactory clipFactory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "herobrine-tts-audio");
        thread.setDaemon(true);
        return thread;
    });

    public WavAudioPlayer() {
        this(AudioSystem::getClip);
    }

    WavAudioPlayer(ClipFactory clipFactory) {
        this.clipFactory = clipFactory;
    }

    @FunctionalInterface
    interface ClipFactory {
        Clip create() throws LineUnavailableException;
    }

    private record Playback(Clip clip, CompletableFuture<Void> finished) { }

    public CompletableFuture<Void> play(byte[] wavBytes, double volume) {
        long ticket = this.generation.get();
        return CompletableFuture.runAsync(() -> playBlocking(wavBytes, volume, ticket), this.executor);
    }

    public boolean isPlaying() {
        Playback playback = this.activeClip.get();
        return playback != null && playback.clip().isRunning();
    }

    public void stop() {
        Playback playback;
        synchronized (this.playbackLock) {
            this.generation.incrementAndGet();
            playback = this.activeClip.getAndSet(null);
        }
        if (playback == null) return;
        // stop/close 即使没有触发驱动回调，也必须释放等待中的任务。
        playback.finished().complete(null);
        closeClip(playback.clip());
    }

    private static void closeClip(Clip clip) {
        try {
            clip.stop();
        } catch (Exception ignored) {
        }
        try {
            clip.close();
        } catch (Exception ignored) {
        }
    }

    /** 是不是可直接播放的 WAV（RIFF....WAVE）。 */
    public static boolean looksLikeWav(byte[] audio) {
        if (audio == null || audio.length < RIFF_HEADER_LENGTH) {
            return false;
        }
        return audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                && audio[8] == 'W' && audio[9] == 'A' && audio[10] == 'V' && audio[11] == 'E';
    }

    /** WAV 格式描述，仅用于日志排错（采样率/声道/位深）。 */
    public static String describeWav(byte[] audio) {
        if (!looksLikeWav(audio)) {
            return "not-wav";
        }
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
            AudioFormat format = stream.getFormat();
            return String.format(Locale.ROOT, "%.0fHz %dch %dbit",
                    format.getSampleRate(), format.getChannels(), format.getSampleSizeInBits());
        } catch (Exception failure) {
            return "wav-unreadable";
        }
    }

    /** 响应体开头的十六进制预览，报错时能看出对方到底回了什么。 */
    static String hexPreview(byte[] audio, int limit) {
        if (audio == null || audio.length == 0) {
            return "(empty)";
        }
        StringBuilder preview = new StringBuilder();
        int count = Math.min(Math.max(1, limit), audio.length);
        for (int index = 0; index < count; index++) {
            preview.append(String.format("%02X", audio[index]));
        }
        return preview.toString();
    }

    /** 先注册终止监听再 start，避免异步启动尚未 isRunning 时被错误关闭。 */
    private void playBlocking(byte[] wavBytes, double volume, long ticket) {
        Clip clip = null;
        Playback playback = null;
        LineListener listener = null;
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wavBytes))) {
            if (ticket != this.generation.get()) return;
            clip = this.clipFactory.create();
            CompletableFuture<Void> finished = new CompletableFuture<>();
            listener = event -> {
                if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                    finished.complete(null);
                }
            };
            clip.addLineListener(listener);
            clip.open(stream);
            applyVolume(clip, volume);
            playback = new Playback(clip, finished);
            synchronized (this.playbackLock) {
                if (ticket != this.generation.get()) return;
                this.activeClip.set(playback);
                clip.start();
            }
            long expectedMillis = Math.max(1L, clip.getMicrosecondLength() / 1000L);
            finished.get(expectedMillis + COMPLETION_GRACE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            LOGGER.warn("TTS 音频播放失败: {}", describe(failure));
        } finally {
            if (clip != null) {
                if (playback != null) this.activeClip.compareAndSet(playback, null);
                try {
                    if (listener != null) clip.removeLineListener(listener);
                } finally {
                    closeClip(clip);
                }
            }
        }
    }

    /** 线性音量(0~1)换算成 MASTER_GAIN 的 dB；不支持该控件时静默跳过。 */
    private static void applyVolume(Clip clip, double volume) {
        FloatControl gain;
        try {
            if (!(clip.getControl(FloatControl.Type.MASTER_GAIN) instanceof FloatControl control)) {
                return;
            }
            gain = control;
        } catch (Exception unsupported) {
            return;
        }
        double safeVolume = Math.max(0.0D, Math.min(1.0D, volume));
        float decibels = safeVolume <= 0.0001D
                ? gain.getMinimum()
                : (float) (20.0D * Math.log10(safeVolume));
        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
