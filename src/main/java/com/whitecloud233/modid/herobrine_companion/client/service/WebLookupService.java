package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 联网查找工具的<b>只读执行层</b>（路径 B 的"执行"层）。
 *
 * <p>单一职责：对外部依赖（{@link HttpClient} / HTTP）的唯一接触点——只做"发起只读请求 + 取回纯文本"。
 * 不做任何策略判断（域名白名单判断交给 {@link WebLookupDomainRegistry}）、不做 LLM 交互、不格式化给模型看。</p>
 *
 * <p>安全纪律：<b>任何一次 HTTP 之前都再次用注册表校验最终 host</b>（纵深防御，防调用方传错 URL）。
 * 只允许 http/https；返回内容一律当不可信字节处理，长度封顶后转纯文本。</p>
 */
public final class WebLookupService {

    private static final int MAX_READ_BYTES = 256 * 1024;      // 单次读取字节上限（256KB）
    private static final int MAX_RESULT_CHARS = 3000;          // 返回给模型的总字符上限
    private static final int MAX_SEARCH_RESULTS = 5;           // 一次搜索最多取几条
    private static final int MAX_PER_LINE_CHARS = 240;         // 单行（标题/摘要）截断
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private WebLookupService() {
    }

    /**
     * 在某个<b>允许搜索</b>的域名上执行关键词搜索（MediaWiki API），返回纯文本结果。
     * 失败不抛异常，返回友好错误串。
     *
     * @param host  白名单内且可搜索的域名（由调用方先经注册表判断）
     * @param query 搜索词（长度由调用方校验）
     */
    public static CompletableFuture<String> search(String host, String query) {
        String apiBase = WebLookupDomainRegistry.searchApiForHost(host);
        if (apiBase == null) {
            return CompletableFuture.completedFuture("(该域名不支持搜索)");
        }
        String url = apiBase
                + "?action=query&list=search&srlimit=" + MAX_SEARCH_RESULTS
                + "&format=json&srsearch=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        return getText(url)
                .thenApply(body -> {
                    try {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        JsonArray results = json.getAsJsonObject("query")
                                .getAsJsonArray("search");
                        StringBuilder sb = new StringBuilder();
                        int shown = 0;
                        for (JsonElement element : results) {
                            if (shown >= MAX_SEARCH_RESULTS) {
                                break;
                            }
                            JsonObject item = element.getAsJsonObject();
                            String title = stripTags(item.has("title") ? item.get("title").getAsString() : "");
                            String snippet = stripTags(item.has("snippet") ? item.get("snippet").getAsString() : "");
                            sb.append("- ").append(clip(title)).append(": ").append(clip(snippet)).append('\n');
                            shown++;
                        }
                        if (sb.length() == 0) {
                            return "(没有搜索结果)";
                        }
                        return cap(sb.toString());
                    } catch (Exception ignored) {
                        return "(搜索响应解析失败)";
                    }
                })
                .exceptionally(ignored -> "(搜索失败，网络不可达或超时)");
    }

    /**
     * 抓取某个白名单域名下的页面，剥掉 HTML 后返回纯文本。
     * 执行前会<b>再次</b>校验 URL 的 host 在白名单内（纵深防御）。
     *
     * @param url 必须为 http/https 且 host 在白名单（调用方已校验；此处再校验一次）
     */
    public static CompletableFuture<String> fetch(String url) {
        String host;
        try {
            URI uri = URI.create(url);
            host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null || (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))) {
                return CompletableFuture.completedFuture("(仅允许 http/https，且必须在白名单域名内)");
            }
        } catch (Exception e) {
            return CompletableFuture.completedFuture("(URL 无法解析)");
        }
        if (!WebLookupDomainRegistry.isAllowedHost(host)) {
            return CompletableFuture.completedFuture("(该域名不在白名单内)");
        }
        return getText(url)
                .thenApply(WebLookupService::stripTags)
                .thenApply(text -> text.isBlank() ? "(页面没有可读文本)" : cap(text))
                .exceptionally(ignored -> "(抓取失败，网络不可达或超时)");
    }

    /** 发起 GET，读取字节后封顶解码为 UTF-8 文本。 */
    private static CompletableFuture<String> getText(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/plain, application/json, text/html;q=0.9, */*;q=0.1")
                .header("User-Agent", "HerobrineCompanion/1.0 (read-only whitelisted lookup)")
                .timeout(TIMEOUT)
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    byte[] bytes = response.body();
                    if (bytes == null || bytes.length == 0) {
                        return "";
                    }
                    int length = Math.min(bytes.length, MAX_READ_BYTES);
                    return new String(bytes, 0, length, StandardCharsets.UTF_8);
                });
    }

    /** 剥掉 HTML 标签 + 折叠空白 + 解码常见实体。内容始终按不可信数据处理，不解释任何指令。 */
    static String stripTags(String html) {
        if (html == null) {
            return "";
        }
        String text = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        return text.trim();
    }

    private static String clip(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= MAX_PER_LINE_CHARS ? text : text.substring(0, MAX_PER_LINE_CHARS) + "…";
    }

    private static String cap(String value) {
        return value.length() <= MAX_RESULT_CHARS ? value : value.substring(0, MAX_RESULT_CHARS) + "…";
    }
}
