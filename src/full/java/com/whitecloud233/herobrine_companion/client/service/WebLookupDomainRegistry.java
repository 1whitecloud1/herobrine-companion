package com.whitecloud233.herobrine_companion.client.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 联网查找工具的<b>域名白名单策略</b>（路径 B 的"策略"层）。
 *
 * <p>单一职责：只回答两个问题——<b>这个域名允不允许抓</b>、<b>这个域名能不能搜索</b>（能的话 API 基址是什么）。
 * 不碰任何 HTTP、不解析 URL、不做 LLM 交互。对外部依赖（网络）零耦合，纯数据 + 判断。</p>
 *
 * <p>安全定位：白名单 = 硬编码的极小集合。模型永远构造不出白名单之外的地址，
 * 因为 URL 的 host 在 <b>构造请求前</b> 就必须通过 {@link #isAllowedHost(String)}。</p>
 */
public final class WebLookupDomainRegistry {

    /** 允许抓取的域名（含其子域名）。刻意极小：只放可信任的只读知识站。 */
    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "minecraft.wiki",
            "zh.minecraft.wiki",
            "en.wikipedia.org",
            "zh.wikipedia.org"
    );

    /** 允许"搜索"的域名 → MediaWiki search API 基址。只有白名单里且可搜索的域名能走 search 动作。 */
    private static final Map<String, String> SEARCH_APIS = Map.of(
            "minecraft.wiki", "https://minecraft.wiki/api.php",
            "zh.minecraft.wiki", "https://zh.minecraft.wiki/api.php",
            "en.wikipedia.org", "https://en.wikipedia.org/w/api.php",
            "zh.wikipedia.org", "https://zh.wikipedia.org/w/api.php"
    );

    private WebLookupDomainRegistry() {
    }

    /**
     * host 是否允许抓取。精确匹配或该白名单域名的子域名（如 {@code zh.minecraft.wiki} 命中
     * {@code minecraft.wiki}）。host 需已去除端口、小写化。
     */
    public static boolean isAllowedHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        for (String domain : ALLOWED_DOMAINS) {
            if (normalized.equals(domain) || normalized.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /** 该域名是否支持"搜索"动作（在白名单且配置了搜索 API）。 */
    public static boolean isSearchableHost(String host) {
        return host != null && SEARCH_APIS.containsKey(host.trim().toLowerCase(Locale.ROOT));
    }

    /** 该域名的 MediaWiki search API 基址；不可搜索则返回 null。 */
    public static String searchApiForHost(String host) {
        if (host == null) {
            return null;
        }
        return SEARCH_APIS.get(host.trim().toLowerCase(Locale.ROOT));
    }

    /** 可搜索的域名列表（用于工具 schema 的 enum，单一事实来源）。 */
    public static java.util.List<String> searchableHosts() {
        return java.util.List.copyOf(SEARCH_APIS.keySet());
    }

    /** 可搜索的域名列表（用于工具描述，让模型知道能搜哪）。 */
    public static String searchableHostsDescription() {
        return String.join(", ", SEARCH_APIS.keySet());
    }
}
