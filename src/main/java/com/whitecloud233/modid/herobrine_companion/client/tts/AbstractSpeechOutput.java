package com.whitecloud233.modid.herobrine_companion.client.tts;

import java.util.concurrent.CompletableFuture;

/** HTTP 合成引擎的公共播放生命周期；协议、配置和响应解码由子类负责。 */
abstract class AbstractSpeechOutput implements SpeechOutput {
    private final WavAudioPlayer player;
    private long generation;

    protected AbstractSpeechOutput() { this(new WavAudioPlayer()); }

    AbstractSpeechOutput(WavAudioPlayer player) { this.player = player; }

    protected abstract String configurationError(TtsOptions options);
    protected abstract byte[] requestSpeech(String text, TtsOptions options);

    @Override
    public CompletableFuture<Void> speak(String text, TtsOptions options) {
        String error = configurationError(options);
        if (error != null) return CompletableFuture.failedFuture(new IllegalStateException(error));
        long ticket;
        synchronized (this) { ticket = this.generation; }
        return CompletableFuture.supplyAsync(() -> requestSpeech(text, options)).thenCompose(audio -> {
            synchronized (this) {
                return ticket == this.generation ? this.player.play(audio, options.volume())
                        : CompletableFuture.completedFuture(null);
            }
        });
    }

    @Override
    public synchronized void stop() {
        this.generation++;
        this.player.stop();
    }

    /** 设置页使用的播放状态，不属于 SpeechOutput 的公共合成契约。 */
    public boolean isPlaying() { return this.player.isPlaying(); }

    @Override
    public boolean isAvailable() { return true; }
}
