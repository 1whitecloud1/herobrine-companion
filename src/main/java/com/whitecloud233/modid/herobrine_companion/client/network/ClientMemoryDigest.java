package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.RequestHeroMemoryDigestPacket;

/**
 * 客户端长期记忆 digest 缓存(M4 Phase 1):缓存服务端 {@code HeroMemory} 的摘要文本,
 * 供 {@code AIService} 注入聊天 LLM 上下文,让 Herobrine 想起跨会话的事。
 *
 * <p>会话级复用 + 惰性刷新:TTL 内不重复索要;单次在途请求带超时防"服务端无响应卡死"。</p>
 */
public final class ClientMemoryDigest {

    /** 摘要有效时长:30 秒内不重复索要。 */
    private static final long TTL_MS = 30_000L;

    /** 一次在途请求的最长等待:超过视为失败可重发(防止服务端无响应把 inflight 卡死)。 */
    private static final long INFLIGHT_TIMEOUT_MS = 5_000L;

    private static volatile String digest = "";
    private static volatile long fetchedAt = 0L;
    private static volatile long inflightSince = 0L;

    private ClientMemoryDigest() {
    }

    /** 收包落地:更新摘要并清除在途标记。 */
    public static void accept(String newDigest) {
        digest = newDigest == null ? "" : newDigest;
        fetchedAt = System.currentTimeMillis();
        inflightSince = 0L;
    }

    /** 当前缓存的摘要(尚未取到则为空串)。 */
    public static String get() {
        return digest;
    }

    /** 客户端主动清空(如记忆管理器清理后),使下一次聊天重新索要。 */
    public static void reset() {
        digest = "";
        fetchedAt = 0L;
        inflightSince = 0L;
    }

    /** 注入点调用:摘要过期且无在途 / 在途超时,则异步索要一次;不阻塞调用方。 */
    public static void ensureFresh() {
        long now = System.currentTimeMillis();
        boolean inflight = inflightSince != 0L;
        boolean stuck = inflight && now - inflightSince > INFLIGHT_TIMEOUT_MS;
        if (inflight && !stuck) {
            return;
        }
        if (!stuck && now - fetchedAt <= TTL_MS) {
            return;
        }
        inflightSince = now;
        PacketHandler.sendToServer(new RequestHeroMemoryDigestPacket());
    }
}
