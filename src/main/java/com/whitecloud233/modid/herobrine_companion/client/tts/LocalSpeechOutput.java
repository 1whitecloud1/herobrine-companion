package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.util.JsonInputs;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地部署 TTS：把一句话交给本机/局域网里的语音服务，拿回 WAV 播放。
 *
 * <p>三种接口形态由 {@link LocalTtsProtocol} 显式选择，不猜：
 * <ul>
 *   <li>{@code OPENAI}：{@code POST {endpoint}/v1/audio/speech}，openedai-speech 等；</li>
 *   <li>{@code GPT_SOVITS}：{@code GET {endpoint}/tts?...}，api_v2 的参考音频零样本克隆；</li>
 *   <li>{@code CUSTOM}：{@code POST {endpoint}}，请求体由玩家模板决定（${text}/${voice}/${model}/${speed}）。</li>
 * </ul>
 *
 * <p>本地服务通常免密钥，只有填了 Key 才带 {@code Authorization}；回环地址永远直连，
 * 不受「跟随系统代理」影响（见 {@link SystemProxy#SELECTOR}）。</p>
 */
public final class LocalSpeechOutput extends AbstractSpeechOutput {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60L);
    private static final int ERROR_SNIPPET_LIMIT = 200;
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{(text|voice|model|speed)}");

    private final HttpClient client;

    public LocalSpeechOutput() {
        this.client = HttpClients.shared(10);
    }

    @Override
    protected String configurationError(TtsOptions options) {
        return options.local().isReady() ? null : "本地 TTS 未配置端点";
    }

    @Override
    protected byte[] requestSpeech(String text, TtsOptions options) {
        TtsOptions.LocalVoice local = options.local();
        HttpRequest request = switch (local.protocol()) {
            case OPENAI -> buildOpenAiRequest(text, local, options.speed());
            case GPT_SOVITS -> buildGptSovitsRequest(text, local);
            case CUSTOM -> buildCustomRequest(text, local, options.speed());
        };
        try {
            HttpResponse<byte[]> response = this.client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(failureHint(local, request, response));
            }
            byte[] audio = response.body();
            if (!WavAudioPlayer.looksLikeWav(audio)) {
                throw new IllegalStateException("本地 TTS 未返回 WAV 音频（协议选错或服务端配置为 mp3）"
                        + "，实际开头: " + WavAudioPlayer.hexPreview(audio, 8));
            }
            LOGGER.debug("本地 TTS 音频就绪: {}", WavAudioPlayer.describeWav(audio));
            return audio;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("本地 TTS 请求被中断", interrupted);
        } catch (Exception failure) {
            throw new IllegalStateException("本地 TTS 请求失败: " + describe(failure), failure);
        }
    }

    /**
     * 把服务端返回的错误翻译成"下一步该做什么"。
     *
     * <p>本地服务各家的失败形态差别很大，原始错误往往看不出原因：
     * {@code Invalid HTTP request received} 多半是协议选错或端点缺路径；
     * {@code Not Found} 是路由不存在（协议与接口形态不匹配）。</p>
     */
    private static String failureHint(TtsOptions.LocalVoice local, HttpRequest request, HttpResponse<byte[]> response) {
        int status = response.statusCode();
        String body = snippet(response.body());
        StringBuilder message = new StringBuilder()
                .append("本地 TTS 返回 HTTP ").append(status)
                .append("（").append(request.method()).append(' ')
                .append(request.uri().getRawPath()).append("）: ").append(body);
        String lowered = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 400 && lowered.contains("invalid http request")) {
            message.append(" —— 请求行被服务端判为非法：确认「接口形态」选择正确")
                    .append("（GPT-SoVITS 用 GPT-SoVITS，OpenAI 兼容服务用 OpenAI 兼容），")
                    .append("并在端点里带上正确路径（GPT-SoVITS 为 /tts）。");
        } else if (status == 404) {
            message.append(" —— 该端点没有这个路由：GPT-SoVITS 只有 /tts，")
                    .append("OpenAI 兼容服务才是 /v1/audio/speech，请把「接口形态」换对。");
        } else if (status == 405) {
            message.append(" —— 方法不被支持：GPT-SoVITS 用 GET，OpenAI 兼容用 POST，请换「接口形态」。");
        }
        return message.toString();
    }

    /** OpenAI 兼容：{@code {endpoint}/v1/audio/speech}，请求体与云端一致。 */
    private static HttpRequest buildOpenAiRequest(String text, TtsOptions.LocalVoice local, double speed) {
        JsonObject body = new JsonObject();
        body.addProperty("model", local.model());
        body.addProperty("voice", local.voiceName());
        body.addProperty("input", text);
        body.addProperty("speed", Math.round(speed * 100.0D) / 100.0D);
        body.addProperty("response_format", "wav");

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(speechUrl(local.endpoint())))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        applyAuthorization(builder, local.apiKey());
        return builder.build();
    }

    /** GPT-SoVITS api_v2：{@code GET {endpoint}/tts}，参考音频路径必须是服务端可见的路径。 */
    private static HttpRequest buildGptSovitsRequest(String text, TtsOptions.LocalVoice local) {
        StringBuilder url = new StringBuilder(gptSovitsUrl(local.endpoint()));
        appendQuery(url, "text", text, url.indexOf("?") >= 0);
        appendQuery(url, "text_lang", local.referenceLang(), true);
        if (!local.referenceAudio().isEmpty()) {
            appendQuery(url, "ref_audio_path", local.referenceAudio(), true);
        }
        if (!local.referenceText().isEmpty()) {
            appendQuery(url, "prompt_text", local.referenceText(), true);
            appendQuery(url, "prompt_lang", local.referenceLang(), true);
        }
        appendQuery(url, "text_split_method", "cut5", true);
        appendQuery(url, "media_type", "wav", true);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(REQUEST_TIMEOUT)
                .GET();
        applyAuthorization(builder, local.apiKey());
        return builder.build();
    }

    /**
     * 把玩家填的端点规范成 GPT-SoVITS 的 {@code /tts} 地址。
     *
     * <p>端点可能写成服务根（{@code http://127.0.0.1:9880}）或完整地址（{@code .../tts}）。
     * 注意尾部斜杠（{@code http://127.0.0.1:9880/}）**不算**带了路径——否则会发出
     * {@code GET /} 并拿到 404。也不能把查询串直接接在根路径上：uvicorn 会把
     * {@code GET /?text=...} 判为 {@code Invalid HTTP request received}（HTTP 400）。</p>
     */
    private static String gptSovitsUrl(String endpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        int schemeEnd = normalized.indexOf("://");
        int authorityStart = schemeEnd >= 0 ? schemeEnd + 3 : 0;
        int queryStart = normalized.indexOf('?');
        String base = queryStart >= 0 ? normalized.substring(0, queryStart) : normalized;
        String query = queryStart >= 0 ? normalized.substring(queryStart) : "";

        int pathStart = base.indexOf('/', authorityStart);
        boolean hasRealPath = pathStart >= 0 && !base.substring(pathStart).replace("/", "").isEmpty();
        if (hasRealPath) {
            return base + query;
        }
        // 服务根（可能有尾部斜杠）：补 /tts，查询串接在路径之后
        return stripTrailingSlash(base) + "/tts" + query;
    }

    private static String stripTrailingSlash(String value) {
        String stripped = value;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    /** 自定义：模板替换后整段作为 {@code POST {endpoint}} 的 JSON 体。 */
    private static HttpRequest buildCustomRequest(String text, TtsOptions.LocalVoice local, double speed) {
        String body = renderCustomBody(text, local, speed);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(local.endpoint()))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        applyAuthorization(builder, local.apiKey());
        return builder.build();
    }

    static String renderCustomBody(String text, TtsOptions.LocalVoice local, double speed) {
        Map<String, String> values = Map.of(
                "text", JsonInputs.escapeStringContent(text),
                "voice", JsonInputs.escapeStringContent(local.voiceName()),
                "model", JsonInputs.escapeStringContent(local.model()),
                "speed", Double.toString(Math.round(speed * 100.0D) / 100.0D));
        // 模板的数值占位符在替换前不是 JSON；单次匹配也不会再次替换文本中的 ${voice}。
        return TEMPLATE_VARIABLE.matcher(local.customBody())
                .replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
    }

    /** 端点写的是服务根（{@code http://127.0.0.1:8000}）时补上 {@code /v1/audio/speech}。 */
    private static String speechUrl(String endpoint) {
        String normalized = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (normalized.contains("/audio/speech")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/audio/speech";
        }
        return normalized + "/v1/audio/speech";
    }

    private static void applyAuthorization(HttpRequest.Builder builder, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        builder.header("Authorization", "Bearer " + apiKey);
    }

    private static void appendQuery(StringBuilder url, String key, String value, boolean hasQuery) {
        url.append(hasQuery ? '&' : '?').append(key).append('=').append(encodeQueryValue(value));
    }

    /**
     * RFC 3986 百分号编码：{@code URLEncoder.encode} 是表单编码，会把空格编成 {@code +}，
     * 而 GPT-SoVITS 的 {@code prompt_text} 含空格时会被当成字面加号。
     */
    static String encodeQueryValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder encoded = new StringBuilder(value.length() * 3);
        for (byte rawByte : value.getBytes(StandardCharsets.UTF_8)) {
            int code = rawByte & 0xFF;
            if (isUnreserved(code)) {
                encoded.append((char) code);
            } else {
                encoded.append('%')
                        .append(Character.toUpperCase(Character.forDigit((code >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(code & 0xF, 16)));
            }
        }
        return encoded.toString();
    }

    /** RFC 3986 unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"。 */
    private static boolean isUnreserved(int code) {
        return (code >= 'A' && code <= 'Z')
                || (code >= 'a' && code <= 'z')
                || (code >= '0' && code <= '9')
                || code == '-' || code == '.' || code == '_' || code == '~';
    }

    private static String snippet(byte[] body) {
        if (body == null || body.length == 0) {
            return "(empty)";
        }
        String text = new String(body, 0, Math.min(body.length, ERROR_SNIPPET_LIMIT), StandardCharsets.UTF_8)
                .replace('\n', ' ').trim();
        LOGGER.debug("本地 TTS 错误响应片段: {}", text);
        return text;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
