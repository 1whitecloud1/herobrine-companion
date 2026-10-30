package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake audio drivers exercise real playback code without network or sound hardware. */
public final class SpeechOutputChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        checkCustomTemplates();
        byte[] wav = wav();
        check("generated WAV recognized", WavAudioPlayer.looksLikeWav(wav));
        check("invalid WAV rejected", !WavAudioPlayer.looksLikeWav(new byte[12]));
        FakeClip clip = new FakeClip(false);
        WavAudioPlayer player = new WavAudioPlayer(() -> clip.proxy);
        CompletableFuture<Void> played = player.play(wav, .9);
        await(clip.started);
        check("listener installed before start", !clip.listeners.isEmpty());
        check("delayed isRunning does not terminate playback", !played.isDone());
        clip.running = true;
        check("running state visible", player.isPlaying());
        clip.emit(LineEvent.Type.STOP);
        played.get(2, TimeUnit.SECONDS);
        check("STOP completes and releases clip", clip.closed && clip.listeners.isEmpty());
        check("no stale playing state", !player.isPlaying());

        FakeClip closeEvent = new FakeClip(false);
        player = new WavAudioPlayer(() -> closeEvent.proxy);
        played = player.play(wav, .9);
        await(closeEvent.started);
        closeEvent.emit(LineEvent.Type.CLOSE);
        played.get(2, TimeUnit.SECONDS);
        check("driver CLOSE also completes playback", closeEvent.closed);

        AtomicInteger created = new AtomicInteger();
        CountDownLatch secondCreated = new CountDownLatch(1);
        FakeClip cancelled = new FakeClip(false);
        player = new WavAudioPlayer(() -> { if (created.incrementAndGet() == 2) secondCreated.countDown(); return cancelled.proxy; });
        played = player.play(wav, .9);
        await(cancelled.started);
        CompletableFuture<Void> queued = player.play(wav, .9);
        player.stop(); // Driver deliberately sends no STOP or CLOSE event.
        played.get(2, TimeUnit.SECONDS);
        queued.get(2, TimeUnit.SECONDS);
        check("stop completes without driver events", cancelled.closed);
        check("stop invalidates queued old audio", created.get() == 1);
        CompletableFuture<Void> next = player.play(wav, .9);
        // First completion waited for cleanup, so the new factory call is the next generation.
        await(secondCreated);
        check("new audio allowed after stop", created.get() == 2);
        player.stop();
        next.get(2, TimeUnit.SECONDS);

        FakeClip opening = new FakeClip(true);
        player = new WavAudioPlayer(() -> opening.proxy);
        played = player.play(wav, .9);
        await(opening.opened);
        player.stop();
        opening.allowOpen.countDown();
        played.get(2, TimeUnit.SECONDS);
        check("stop during open prevents start", opening.started.getCount() == 1 && opening.closed);

        AtomicInteger lateClips = new AtomicInteger();
        WavAudioPlayer latePlayer = new WavAudioPlayer(() -> { lateClips.incrementAndGet(); return new FakeClip(false).proxy; });
        CountDownLatch requested = new CountDownLatch(1), response = new CountDownLatch(1);
        AbstractSpeechOutput output = new AbstractSpeechOutput(latePlayer) {
            @Override protected String configurationError(TtsOptions options) { return null; }
            @Override protected byte[] requestSpeech(String text, TtsOptions options) {
                requested.countDown();
                try { await(response); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return wav;
            }
        };
        TtsOptions options = new TtsOptions(null, .9, 1, 200, true, null, null, null);
        CompletableFuture<Void> spoken = output.speak("hello", options);
        await(requested);
        output.stop();
        response.countDown();
        spoken.get(2, TimeUnit.SECONDS);
        check("late HTTP response cannot restart stopped speech", lateClips.get() == 0);
        AbstractSpeechOutput unavailable = new AbstractSpeechOutput(latePlayer) {
            @Override protected String configurationError(TtsOptions ignored) { return "missing endpoint"; }
            @Override protected byte[] requestSpeech(String text, TtsOptions ignored) { throw new AssertionError("must not request"); }
        };
        boolean failed = false;
        try { unavailable.speak("x", options).join(); } catch (CompletionException expected) {
            failed = "missing endpoint".equals(expected.getCause().getMessage());
        }
        check("configuration error stays exceptional", failed);
        System.out.println("SpeechOutputChecks: " + checks + " checks passed");
    }

    private static void checkCustomTemplates() {
        String template = "{\"text\":\"${text}\",\"voice\":\"${voice}\",\"model\":\"${model}\",\"speed\":${speed},\"speedString\":\"${speed}\",\"unknown\":\"${other}\"}";
        var voice = new TtsOptions.LocalVoice("http://localhost", LocalTtsProtocol.CUSTOM,
                "m\"\t\\x", "v\"\t\\y", "", "", "", template, "");
        String text = "中文\"\\\t\r\n\b\f\u0001${voice}${model}${speed}$5";
        JsonObject body = JsonParser.parseString(LocalSpeechOutput.renderCustomBody(text, voice, 1.234)).getAsJsonObject();
        check("template controls and placeholders in user text survive", text.equals(body.get("text").getAsString()));
        check("voice escaped", voice.voiceName().equals(body.get("voice").getAsString()));
        check("model escaped", voice.model().equals(body.get("model").getAsString()));
        check("unquoted speed stays numeric", body.get("speed").getAsJsonPrimitive().isNumber() && body.get("speed").getAsDouble() == 1.23);
        check("quoted speed remains supported", "1.23".equals(body.get("speedString").getAsString()));
        check("unknown placeholders left alone", "${other}".equals(body.get("unknown").getAsString()));
    }

    private static byte[] wav() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AudioFormat format = new AudioFormat(8_000, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(new byte[1600]), format, 800)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, bytes);
        }
        return bytes.toByteArray();
    }

    private static final class FakeClip {
        final List<LineListener> listeners = new CopyOnWriteArrayList<>();
        final CountDownLatch opened = new CountDownLatch(1), started = new CountDownLatch(1);
        final CountDownLatch allowOpen;
        volatile boolean closed, running;
        final Clip proxy;
        FakeClip(boolean blockOpen) {
            this.allowOpen = new CountDownLatch(blockOpen ? 1 : 0);
            this.proxy = (Clip) Proxy.newProxyInstance(Clip.class.getClassLoader(), new Class<?>[]{Clip.class}, (object, method, args) -> {
                return switch (method.getName()) {
                    case "addLineListener" -> { listeners.add((LineListener) args[0]); yield null; }
                    case "removeLineListener" -> { listeners.remove(args[0]); yield null; }
                    case "open" -> { opened.countDown(); await(allowOpen); yield null; }
                    case "start" -> { started.countDown(); yield null; }
                    case "stop" -> { running = false; yield null; }
                    case "close" -> { closed = true; running = false; yield null; }
                    case "isRunning", "isActive" -> running;
                    case "isOpen" -> !closed;
                    case "getMicrosecondLength" -> 100_000L;
                    case "getControl" -> throw new IllegalArgumentException("unsupported");
                    case "hashCode" -> System.identityHashCode(object);
                    case "equals" -> object == args[0];
                    case "toString" -> "FakeClip";
                    default -> throw new AssertionError("Unexpected Clip call: " + method);
                };
            });
        }
        void emit(LineEvent.Type type) {
            for (LineListener listener : listeners) listener.update(new LineEvent(proxy, type, 0));
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for audio driver");
    }
    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
