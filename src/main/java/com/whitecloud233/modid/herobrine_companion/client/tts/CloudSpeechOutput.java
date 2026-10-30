package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.client.service.SystemProxy;
import com.whitecloud233.modid.herobrine_companion.client.service.HttpClients;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 云端语音合成。
 *
 * <p>支持两种接口形态，按端点自动判别：
 * <ul>
 *   <li><b>OpenAI 兼容</b>：{@code POST {endpoint}}，直接返回 WAV 二进制；</li>
 *   <li><b>MiMo（小米）</b>：{@code POST .../chat/completions}，文本放在 <b>assistant</b> 消息里，
 *       音频以 base64 WAV 放在 {@code choices[0].message.audio.data}。</li>
 * </ul>
 *
 * <p>MiMo 是对外文档唯一明确要求 assistant 角色的 TTS：用 user 会得到
 * {@code messages must contain an assistant role for TTS model}。</p>
 */
public final class CloudSpeechOutput extends AbstractSpeechOutput {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient CLIENT = HttpClients.shared(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60L);
    private static final int ERROR_SNIPPET_LIMIT = 300;

    @Override
    protected String configurationError(TtsOptions options) {
        return options.cloud().isReady() ? null : "云端 TTS 未配置端点或密钥";
    }

    @Override
    protected byte[] requestSpeech(String text, TtsOptions options) {
        TtsOptions.CloudVoice cloud = options.cloud();
        boolean chatStyle = isChatStyleEndpoint(cloud.endpoint());
        HttpRequest request = chatStyle ? buildChatRequest(text, cloud) : buildSpeechRequest(text, cloud, options);

        try {
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(failureHint(cloud, request, response));
            }
            byte[] audio = chatStyle ? extractBase64Audio(response.body()) : response.body();
            if (!WavAudioPlayer.looksLikeWav(audio)) {
                throw new IllegalStateException("云端 TTS 未返回 WAV 音频"
                        + "（OpenAI 兼容端点需支持 response_format=wav；MiMo 需 audio.format=wav）"
                        + "，实际开头: " + WavAudioPlayer.hexPreview(audio, 8));
            }
            LOGGER.debug("云端 TTS 音频就绪: {}", WavAudioPlayer.describeWav(audio));
            return audio;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("云端 TTS 请求被中断", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("云端 TTS 请求失败: " + describe(failure), failure);
        }
    }

    /** 端点指向 chat/completions 时按 chat 形态处理（MiMo 等）。 */
    private static boolean isChatStyleEndpoint(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        String normalized = endpoint.toLowerCase(Locale.ROOT);
        return normalized.contains("/chat/completions") || normalized.contains("/v1/chat");
    }

    /** OpenAI 兼容：{@code POST {endpoint}} + JSON，响应体本身就是音频字节。 */
    private static HttpRequest buildSpeechRequest(String text, TtsOptions.CloudVoice cloud, TtsOptions options) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cloud.model());
        body.addProperty("voice", cloud.voiceName());
        body.addProperty("input", text);
        body.addProperty("speed", Math.round(options.speed() * 100.0D) / 100.0D);
        body.addProperty("response_format", "wav");
        return HttpRequest.newBuilder(URI.create(cloud.endpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cloud.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    /**
     * MiMo 等 chat 形态：文本必须放在 <b>assistant</b> 消息里，并声明 {@code modalities=["audio"]}。
     */
    private static HttpRequest buildChatRequest(String text, TtsOptions.CloudVoice cloud) {
        JsonObject audio = new JsonObject();
        audio.addProperty("voice", cloud.voiceName());
        audio.addProperty("format", "wav");

        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", text);
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonArray modalities = new JsonArray();
        modalities.add("audio");

        JsonObject body = new JsonObject();
        body.addProperty("model", cloud.model());
        body.add("messages", messages);
        body.add("modalities", modalities);
        body.add("audio", audio);

        return HttpRequest.newBuilder(URI.create(cloud.endpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cloud.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    /** 从 chat 响应里取出 {@code choices[0].message.audio.data} 并做 base64 解码。 */
    private static byte[] extractBase64Audio(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            throw new IllegalStateException("云端 TTS 返回空响应");
        }
        String json = new String(responseBody, StandardCharsets.UTF_8);
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (Exception malformed) {
            throw new IllegalStateException("云端 TTS 响应不是合法 JSON，开头: "
                    + json.substring(0, Math.min(json.length(), 120)));
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("云端 TTS 响应结构异常");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.has("error")) {
            throw new IllegalStateException("云端 TTS 返回错误: " + root.get("error"));
        }
        JsonArray choices = root.has("choices") ? root.getAsJsonArray("choices") : null;
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("云端 TTS 响应缺少 choices: "
                    + json.substring(0, Math.min(json.length(), 160)));
        }
        JsonElement messageElement = choices.get(0).getAsJsonObject().get("message");
        if (messageElement == null || !messageElement.isJsonObject()) {
            throw new IllegalStateException("云端 TTS 响应缺少 message 字段");
        }
        JsonObject message = messageElement.getAsJsonObject();
        JsonElement audioElement = message.get("audio");
        if (audioElement == null || !audioElement.isJsonObject()) {
            throw new IllegalStateException("云端 TTS 响应里没有音频（该模型可能不支持语音输出）");
        }
        JsonObject audio = audioElement.getAsJsonObject();
        JsonElement data = audio.get("data");
        if (data == null || data.isJsonNull() || data.getAsString().isBlank()) {
            throw new IllegalStateException("云端 TTS 音频字段为空");
        }
        try {
            return Base64.getDecoder().decode(data.getAsString());
        } catch (IllegalArgumentException invalidBase64) {
            // 有些服务会给 data URI 前缀
            String raw = data.getAsString();
            int comma = raw.indexOf(',');
            if (comma > 0) {
                return Base64.getDecoder().decode(raw.substring(comma + 1));
            }
            throw new IllegalStateException("云端 TTS 音频 base64 解码失败", invalidBase64);
        }
    }

    /** 把常见失败翻译成可操作的提示。 */
    private static String failureHint(TtsOptions.CloudVoice cloud, HttpRequest request, HttpResponse<byte[]> response) {
        int status = response.statusCode();
        String body = snippet(response.body());
        StringBuilder message = new StringBuilder()
                .append("云端 TTS 返回 HTTP ").append(status).append(": ").append(body);
        String lowered = body.toLowerCase(Locale.ROOT);
        if (status == 404) {
            message.append(" —— 端点路径不存在。MiMo 用 https://api.xiaomimimo.com/v1/chat/completions，")
                    .append("OpenAI 兼容用 .../v1/audio/speech。");
        } else if (status == 401 || status == 403) {
            message.append(" —— 密钥无效或没有该模型的权限。");
        } else if (lowered.contains("assistant role")) {
            message.append(" —— 该模型要求文本放在 assistant 消息里（本模组在 chat/completions 端点已自动处理）。");
        } else if (lowered.contains("model")) {
            message.append(" —— 检查模型名是否正确，例如 mimo-v2.5-tts。");
        }
        return message.toString();
    }

    private static String snippet(byte[] body) {
        if (body == null || body.length == 0) {
            return "(empty)";
        }
        String text = new String(body, 0, Math.min(body.length, ERROR_SNIPPET_LIMIT), StandardCharsets.UTF_8)
                .replace('\n', ' ').replace('\r', ' ').trim();
        LOGGER.debug("云端 TTS 错误响应片段: {}", text);
        return text;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
