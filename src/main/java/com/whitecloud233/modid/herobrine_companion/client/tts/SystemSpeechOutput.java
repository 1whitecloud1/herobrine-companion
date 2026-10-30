package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 操作系统自带语音：Windows 用 SAPI（System.Speech）、macOS 用 {@code say}、Linux 用 {@code espeak}/{@code spd-say}。
 *
 * <p>离线、免费、无需密钥，因此作为默认引擎。文本统一走外部进程朗读：
 * 进程输出被丢弃（避免管道写满导致朗读卡住），"打断"就是结束该进程。</p>
 */
public final class SystemSpeechOutput implements SpeechOutput {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    /** {@code say} / {@code espeak} 的默认语速（词/分钟），按倍率换算。 */
    private static final int BASE_WORDS_PER_MINUTE = 175;

    private final AtomicReference<Process> activeProcess = new AtomicReference<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "herobrine-tts-system");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public CompletableFuture<Void> speak(String text, TtsOptions options) {
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("no system speech engine on this platform"));
        }
        return CompletableFuture.runAsync(() -> runBlocking(text, options), this.executor);
    }

    @Override
    public void stop() {
        Process process = this.activeProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    @Override
    public boolean isAvailable() {
        return platformSupportsSystemSpeech();
    }

    /** 当前操作系统是否有可用的系统语音通道（纯平台判断，不需要实例）。 */
    public static boolean platformSupportsSystemSpeech() {
        return isWindows() || isMac() || isLinux();
    }

    private void runBlocking(String text, TtsOptions options) {
        Path workDirectory = null;
        try {
            ProcessBuilder builder;
            if (isWindows()) {
                workDirectory = Files.createTempDirectory("herobrine_tts");
                Path textFile = workDirectory.resolve("speech.txt");
                Files.writeString(textFile, text, StandardCharsets.UTF_8);
                builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-EncodedCommand", encodePowerShell(buildWindowsScript(textFile, options)));
            } else if (isMac()) {
                builder = new ProcessBuilder(buildSayCommand(text, options));
            } else {
                runLinux(text, options);
                return;
            }

            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            this.activeProcess.set(process);
            try {
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    LOGGER.warn("系统语音进程退出码 {}（音色或语速可能不被系统支持）", exitCode);
                }
            } finally {
                this.activeProcess.compareAndSet(process, null);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            throw new IllegalStateException("system tts failed: " + describe(failure), failure);
        } finally {
            deleteQuietly(workDirectory);
        }
    }

    /** macOS：{@code say -v <voice> -r <wpm> <text>}。 */
    private static List<String> buildSayCommand(String text, TtsOptions options) {
        List<String> command = new ArrayList<>();
        command.add("say");
        if (!options.system().voiceName().isEmpty()) {
            command.add("-v");
            command.add(options.system().voiceName());
        }
        command.add("-r");
        command.add(Integer.toString(wordsPerMinute(options.speed())));
        command.add(text);
        return command;
    }

    /** Linux：先试 espeak，缺席再试 spd-say。 */
    private void runLinux(String text, TtsOptions options) throws InterruptedException {
        List<String> espeak = new ArrayList<>();
        espeak.add("espeak");
        if (!options.system().voiceName().isEmpty()) {
            espeak.add("-v");
            espeak.add(options.system().voiceName());
        }
        espeak.add("-s");
        espeak.add(Integer.toString(wordsPerMinute(options.speed())));
        espeak.add(text);
        if (runCommand(espeak)) {
            return;
        }

        List<String> spdSay = new ArrayList<>();
        spdSay.add("spd-say");
        spdSay.add("-w");
        spdSay.add("-r");
        spdSay.add(Integer.toString(ratePercent(options.speed())));
        spdSay.add(text);
        if (!runCommand(spdSay)) {
            throw new IllegalStateException("未找到 espeak 或 spd-say");
        }
    }

    /** 执行一条朗读命令；可执行文件不存在时返回 false（不抛异常，便于逐个回退）。 */
    private boolean runCommand(List<String> command) throws InterruptedException {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
        } catch (IOException missingExecutable) {
            return false;
        }
        this.activeProcess.set(process);
        try {
            process.waitFor();
            return true;
        } finally {
            this.activeProcess.compareAndSet(process, null);
        }
    }

    /**
     * Windows 朗读脚本。文本走临时文件 + 显式 UTF-8 读取，避免中文经命令行参数被本地代码页截坏；
     * 脚本本身用 {@code -EncodedCommand}(UTF-16LE Base64) 传递，绕开所有引号转义问题。
     */
    private static String buildWindowsScript(Path textFile, TtsOptions options) {
        int rate = (int) Math.round((options.speed() - 1.0D) * 10.0D);
        int volume = (int) Math.round(options.volume() * 100.0D);
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'Stop'\n");
        script.append("Add-Type -AssemblyName System.Speech\n");
        script.append("$text = [System.IO.File]::ReadAllText('").append(quotePowerShell(textFile.toString()))
                .append("', [System.Text.Encoding]::UTF8)\n");
        script.append("$speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer\n");
        script.append("$speaker.Rate = ").append(Math.max(-10, Math.min(10, rate))).append('\n');
        script.append("$speaker.Volume = ").append(Math.max(0, Math.min(100, volume))).append('\n');
        if (!options.system().voiceName().isEmpty()) {
            script.append("try { $speaker.SelectVoice('").append(quotePowerShell(options.system().voiceName())).append("') } catch { }\n");
        }
        script.append("$speaker.Speak($text)\n");
        script.append("$speaker.Dispose()\n");
        return script.toString();
    }

    private static String encodePowerShell(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    /** PowerShell 单引号字符串里只有 {@code '} 需要转义（写成两个单引号）。 */
    private static String quotePowerShell(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static int wordsPerMinute(double speed) {
        return Math.max(60, (int) Math.round(BASE_WORDS_PER_MINUTE * speed));
    }

    /** spd-say 的语速是百分比，-100 ~ 100。 */
    private static int ratePercent(double speed) {
        return Math.max(-100, Math.min(100, (int) Math.round((speed - 1.0D) * 100.0D)));
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    private static boolean isMac() {
        return OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    }

    private static boolean isLinux() {
        return OS_NAME.contains("linux") || OS_NAME.contains("nux");
    }
}
