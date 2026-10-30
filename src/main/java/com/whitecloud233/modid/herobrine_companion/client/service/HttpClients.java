package com.whitecloud233.modid.herobrine_companion.client.service;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全模组共用的 {@link HttpClient} 工厂。
 *
 * <p>原先 11 个类各自 {@code HttpClient.newBuilder()...build()}。这有两层代价：
 * 每个实例自带一条 Selector 线程和一份连接池，等于 11 份线程与连接；更要紧的是
 * <b>漏写 {@code .proxy(SystemProxy.SELECTOR)} 不会报错</b>——只是那个功能在开了代理的机器上
 * 静默连不上（{@code WebLookupService} 与 {@code ModrinthUpdateChecker} 就是这么漏的）。
 * 收敛到一处之后，「必须挂代理」不再依赖每个调用方记得写。</p>
 *
 * <p>按超时秒数缓存：同一超时的调用方共享同一个实例（多半只有 5/8/10 三档），
 * 不同超时之间因为 {@code connectTimeout} 是构建期定死的，只能分开建。</p>
 *
 * <p><b>例外</b>：连本机回环服务（llama.cpp / 本地 TTS）一律走 {@link #direct(int)}，
 * 否则流量会被系统代理接管再绕回来，反而更慢甚至被代理软件拒连。</p>
 */
public final class HttpClients {

    private static final Map<Integer, HttpClient> SHARED = new ConcurrentHashMap<>();
    private static final Map<Integer, HttpClient> DIRECT = new ConcurrentHashMap<>();

    /** 走系统代理（读环境变量 → 注册表）的共享客户端。 */
    public static HttpClient shared(int timeoutSeconds) {
        return SHARED.computeIfAbsent(Math.max(1, timeoutSeconds), seconds -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(seconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(SystemProxy.SELECTOR)
                .build());
    }

    /** 直连（不走任何代理）的共享客户端，用于本机回环服务。 */
    public static HttpClient direct(int timeoutSeconds) {
        return DIRECT.computeIfAbsent(Math.max(1, timeoutSeconds), seconds -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(seconds))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.of(null))
                .build());
    }

    private HttpClients() {
    }
}
