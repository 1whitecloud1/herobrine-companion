package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 枚举"这台机器上真正装了哪些 TTS 音色"，供设置页挑选。
 *
 * <p>系统音色的性别无法直接查询，因此这里只负责"列出名字"，性别交给
 * {@link VoiceGender#infer(String)} 按名字推断，推断不出就标注未知。</p>
 *
 * <p>结果按平台缓存；查询走异步，绝不在渲染线程里等进程。</p>
 */
public final class VoiceCatalog {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long QUERY_TIMEOUT_SECONDS = 8L;
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    private static volatile List<VoiceOption> cachedSystemVoices;
    private static volatile boolean loading;

    private VoiceCatalog() {
    }

    /** 已缓存的系统音色（未查询过返回空表，调用方先 {@link #loadSystemVoices()}）。 */
    public static List<VoiceOption> cachedSystemVoices() {
        List<VoiceOption> cached = cachedSystemVoices;
        return cached == null ? List.of() : cached;
    }

    /**
     * 异步查询系统音色并写入缓存；已完成或正在查询时直接返回。
     */
    public static CompletableFuture<List<VoiceOption>> loadSystemVoices() {
        List<VoiceOption> cached = cachedSystemVoices;
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (loading) {
            return CompletableFuture.completedFuture(cachedSystemVoices());
        }
        loading = true;
        return CompletableFuture.supplyAsync(VoiceCatalog::querySystemVoices)
                .whenComplete((voices, failure) -> {
                    loading = false;
                    if (failure != null) {
                        LOGGER.warn("查询系统音色失败: {}", failure.getMessage());
                        return;
                    }
                    cachedSystemVoices = voices == null ? List.of() : List.copyOf(voices);
                });
    }

    /** 清空缓存（玩家可能刚装了系统语音包，设置页可触发重查）。 */
    public static void invalidate() {
        cachedSystemVoices = null;
    }

    private static List<VoiceOption> querySystemVoices() {
        try {
            if (isWindows()) {
                return queryWindowsVoices();
            }
            if (isMac()) {
                return queryMacVoices();
            }
            if (isLinux()) {
                return queryLinuxVoices();
            }
        } catch (Exception failure) {
            LOGGER.warn("枚举系统音色时出错: {}", failure.getMessage());
        }
        return List.of();
    }

    /** Windows：SAPI {@code GetInstalledVoices()}，输出 "Name|Gender" 行。 */
    private static List<VoiceOption> queryWindowsVoices() throws IOException, InterruptedException {
        StringBuilder script = new StringBuilder();
        script.append("$ErrorActionPreference = 'Stop'\n");
        script.append("Add-Type -AssemblyName System.Speech\n");
        script.append("$speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer\n");
        script.append("$speaker.GetInstalledVoices() | ForEach-Object {\n");
        script.append("  if ($_.Enabled) {\n");
        script.append("    $info = $_.VoiceInfo\n");
        script.append("    Write-Output ($info.Name + '|' + $info.Gender)\n");
        script.append("  }\n");
        script.append("}\n");
        script.append("$speaker.Dispose()\n");

        String encoded = Base64.getEncoder().encodeToString(script.toString().getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
        builder.redirectErrorStream(true);
        String output = runAndRead(builder);
        return parseWindowsVoices(output);
    }

    private static List<VoiceOption> parseWindowsVoices(String output) {
        List<VoiceOption> voices = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            int separator = trimmed.lastIndexOf('|');
            if (separator <= 0) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            String genderText = trimmed.substring(separator + 1).trim();
            if (name.isEmpty() || name.contains("|")) {
                continue;
            }
            voices.add(new VoiceOption(name, shortenWindowsVoiceName(name),
                    genderFromSapi(genderText, name)));
        }
        return voices;
    }

    /** {@code Microsoft Huihui Desktop} → {@code Huihui}；{@code Microsoft Huihui - Chinese (Simplified, PRC)} 同理。 */
    private static String shortenWindowsVoiceName(String name) {
        String shortName = name;
        int dash = shortName.indexOf(" - ");
        if (dash > 0) {
            shortName = shortName.substring(0, dash);
        }
        if (shortName.toLowerCase(Locale.ROOT).startsWith("microsoft ")) {
            shortName = shortName.substring("microsoft ".length());
        }
        if (shortName.toLowerCase(Locale.ROOT).endsWith(" desktop")) {
            shortName = shortName.substring(0, shortName.length() - " desktop".length());
        }
        return shortName.trim();
    }

    /** SAPI 的 Gender 字段可信度较高：Female / Male / NotSet。 */
    private static VoiceGender genderFromSapi(String genderText, String voiceName) {
        String normalized = genderText == null ? "" : genderText.toLowerCase(Locale.ROOT);
        if (normalized.contains("female")) {
            return VoiceGender.FEMALE;
        }
        if (normalized.contains("male")) {
            return VoiceGender.MALE;
        }
        return VoiceGender.infer(voiceName);
    }

    /** macOS：{@code say -v ?} 输出形如 {@code Samantha            en_US    # Hello! ...}。 */
    private static List<VoiceOption> queryMacVoices() throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder("say", "-v", "?");
        builder.redirectErrorStream(true);
        String output = runAndRead(builder);
        List<VoiceOption> voices = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int comment = trimmed.indexOf('#');
            String head = comment >= 0 ? trimmed.substring(0, comment).trim() : trimmed;
            String[] parts = head.split("\\s{2,}");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            String name = parts[0].trim();
            voices.add(new VoiceOption(name, name, VoiceGender.infer(name)));
        }
        return voices;
    }

    /** Linux：espeak 的 {@code --voices} 输出；espeak 缺席时用 spd-say 的音色列表。 */
    private static List<VoiceOption> queryLinuxVoices() throws IOException, InterruptedException {
        List<VoiceOption> voices = new ArrayList<>();
        try {
            ProcessBuilder builder = new ProcessBuilder("espeak", "--voices");
            builder.redirectErrorStream(true);
            String output = runAndRead(builder);
            // 形如：5  zh  ...  ...  cmn  Chinese (Mandarin)
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).startsWith("pty")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 4) {
                    continue;
                }
                String language = parts[1];
                String name = parts[parts.length - 2];
                String fullName = name + " (" + language + ")";
                voices.add(new VoiceOption(fullName, fullName, VoiceGender.infer(name)));
            }
            return voices;
        } catch (IOException espeakMissing) {
            // 继续尝试 spd-say
        }

        ProcessBuilder builder = new ProcessBuilder("spd-say", "-L");
        builder.redirectErrorStream(true);
        String output = runAndRead(builder);
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                voices.add(new VoiceOption(trimmed, trimmed, VoiceGender.infer(trimmed)));
            }
        }
        return voices;
    }

    private static String runAndRead(ProcessBuilder builder) throws IOException, InterruptedException {
        Process process = builder.start();
        try {
            boolean finished = process.waitFor(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
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
