package com.whitecloud233.modid.herobrine_companion.client.service;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 系统代理支持：给模组内所有外部 HTTP 客户端提供统一的"跟随系统代理"能力。
 *
 * <p><b>为什么不用 {@code -Djava.net.useSystemProxies}</b>：JDK 的 {@code DefaultProxySelector}
 * 在类加载时就固定了是否读系统代理（{@code theProxySelector} 静态字段），而 Minecraft 启动阶段
 * 早已联网，之后再 {@code System.setProperty} 已无效——实测先发一次请求再开关，
 * {@code select()} 仍返回 {@code [DIRECT]}，代理请求 15 秒超时。独立跑 main 时"有效"只是因为
 * 那个 JVM 还没加载过 DefaultProxySelector。所以这里自己读配置源再构造 Proxy。</p>
 *
 * <p><b>为什么做成动态 ProxySelector 而不是动态 HttpClient</b>：{@code HttpClient} 的代理在建
 * 造时固定，且每个实例自带连接池。若按开关重建客户端，会丢掉 keep-alive 连接复用（每次请求
 * 重新 TLS 握手），高频对话代价明显。这里把 {@link #SELECTOR} 交给 {@code HttpClient.Builder.proxy()}，
 * 客户端实例保持不变，每次请求时由 select() 现场决定直连还是走代理，开关与端口变化即时生效。</p>
 *
 * <p>代理来源：① 环境变量 {@code HTTPS_PROXY / ALL_PROXY / HTTP_PROXY}；② Windows 注册表
 * {@code HKCU\...\Internet Settings} 的 {@code ProxyEnable}(需 0x1) + {@code ProxyServer}。
 * 端口全部实时读取，不做任何硬编码。不支持 PAC 自动配置脚本（读不到就直连并告警）。</p>
 */
public final class SystemProxy {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 直连（等价于 Proxy.NO_PROXY）。 */
    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);

    /** 系统代理的重新读取间隔：换端口后最多 60 秒自动生效，不必重启游戏。 */
    private static final long REFRESH_INTERVAL_MS = 60_000L;

    /** 当前解析到的系统代理；null = 没读到（PAC / 未开启 / 解析失败）。 */
    private static volatile Proxy cachedProxy;
    private static volatile long cachedAt = 0L;
    /** 上次生效的代理地址（或 "none"）：只在变化时打日志，避免每次刷新刷屏。 */
    private static volatile String lastLabel = "";

    private SystemProxy() {
    }

    /**
     * 交给 {@code HttpClient.Builder.proxy(...)} 的动态选择器。
     *
     * <p>开关关闭时始终直连（与未加代理时的行为完全一致）；开启时对外部地址返回系统代理，
     * 对回环地址（本地 llama 服务）返回直连。</p>
     */
    public static final ProxySelector SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            if (!LLMConfig.isUseSystemProxy()) {
                return DIRECT;
            }
            if (isLoopback(uri)) {
                return DIRECT;
            }
            Proxy proxy = currentProxy();
            return proxy == null ? DIRECT : List.of(proxy);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            LOGGER.warn("代理连接失败 {} -> {}", uri == null ? "?" : uri.getHost(), describe(ioe));
        }
    };

    /** 当前系统代理（带 TTL 缓存）；读不到返回 null。仅用于日志/诊断。 */
    public static Proxy currentProxy() {
        long now = System.currentTimeMillis();
        Proxy cached = cachedProxy;
        if (cached != null && now - cachedAt < REFRESH_INTERVAL_MS) {
            return cached;
        }
        synchronized (SystemProxy.class) {
            Proxy again = cachedProxy;
            if (again != null && now - cachedAt < REFRESH_INTERVAL_MS) {
                return again;
            }
            Proxy resolved = resolveSystemProxy();
            String label = resolved == null ? "none" : String.valueOf(resolved);
            if (!label.equals(lastLabel)) {
                lastLabel = label;
                if (resolved == null) {
                    LOGGER.warn("已开启[跟随系统代理]，但没读到可用的代理配置，本次请求仍直连。"
                            + "检查：系统代理是否开启 / 是否使用了 PAC 自动配置脚本（暂不支持）。");
                } else {
                    LOGGER.info("已开启[跟随系统代理]，HTTP 请求走: {}", label);
                }
            }
            cachedProxy = resolved;
            cachedAt = System.currentTimeMillis();
            return resolved;
        }
    }

    /** 回环地址：本地 llama 服务，永远直连（代理对它没有意义，多数代理还会直接拒绝）。 */
    private static boolean isLoopback(URI uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return false;
        }
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host);
    }

    /**
     * 读取系统代理：环境变量优先，其次 Windows 注册表。读不到返回 null（调用方回退直连）。
     */
    private static Proxy resolveSystemProxy() {
        for (String key : new String[]{"HTTPS_PROXY", "https_proxy", "ALL_PROXY", "all_proxy",
                "HTTP_PROXY", "http_proxy"}) {
            Proxy parsed = parseProxy(System.getenv(key));
            if (parsed != null) {
                return parsed;
            }
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return null;
        }
        if (!"0x1".equalsIgnoreCase(String.valueOf(regQuery("ProxyEnable")).trim())) {
            return null; // 系统代理开关没打开
        }
        return parseProxy(pickWindowsProxyServer(regQuery("ProxyServer")));
    }

    /** 解析 "127.0.0.1:7897" / "http://user:pass@127.0.0.1:7897" 形式的代理地址。 */
    private static Proxy parseProxy(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        int at = value.lastIndexOf('@');
        if (at >= 0) {
            value = value.substring(at + 1); // 去掉 user:pass@
        }
        value = value.replace("/", "").trim();
        int colon = value.lastIndexOf(':');
        if (colon <= 0) {
            return null;
        }
        String host = value.substring(0, colon).trim();
        if (host.isEmpty()) {
            return null;
        }
        try {
            int port = Integer.parseInt(value.substring(colon + 1).trim());
            if (port <= 0 || port > 65535) {
                return null;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** IE/Windows 的 ProxyServer 可能是 "http=host:port;https=host:port"，或纯 "host:port"。 */
    private static String pickWindowsProxyServer(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String https = null;
        String http = null;
        String plain = null;
        for (String part : raw.split("[;,]")) {
            String piece = part.trim();
            if (piece.isEmpty()) {
                continue;
            }
            int eq = piece.indexOf('=');
            if (eq > 0) {
                String scheme = piece.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String address = piece.substring(eq + 1).trim();
                if ("https".equals(scheme)) {
                    https = address;
                } else if ("http".equals(scheme)) {
                    http = address;
                }
            } else if (plain == null) {
                plain = piece;
            }
        }
        return https != null ? https : (http != null ? http : plain);
    }

    /** 读取 HKCU 下 Internet Settings 的某个值；失败或超时返回 null。 */
    private static String regQuery(String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "/v", valueName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // 形如 "    ProxyServer    REG_SZ    127.0.0.1:7897"
            for (String line : out.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(valueName)) {
                    continue;
                }
                // 注意：ProxyEnable 是 REG_DWORD（"ProxyEnable    REG_DWORD    0x1"），
                // 只认 REG_SZ 会解析失败并静默回退直连。按"值名 + 类型 + 值"通用格式切分。
                String rest = trimmed.substring(valueName.length()).trim();
                int space = rest.indexOf(' ');
                // rest 形如 "REG_SZ    127.0.0.1:7897"：跳过类型 token，取剩下的部分
                return space > 0 ? rest.substring(space + 1).trim() : rest;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
