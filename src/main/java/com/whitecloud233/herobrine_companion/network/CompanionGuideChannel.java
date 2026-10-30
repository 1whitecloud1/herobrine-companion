package com.whitecloud233.herobrine_companion.network;

import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 同行手册回执的握手门禁 —— 1.21.1 NeoForge 版本。
 *
 * <h2>与 1.20.1 Forge 版的对应关系</h2>
 * 旧版是 {@code OptionalChannel} 的子类，用独立 {@code SimpleChannel} +
 * {@code NetworkRegistry.acceptMissingOr(version)} 协商：老客户端不认这条通道就直接不发。
 * NeoForge 1.21.1 只有一个 {@code PayloadRegistrar}，没有独立通道，等价做法是：
 * <ol>
 *   <li>用 {@link PayloadRegistrar#optional()} 注册——对端缺这条载荷只跳过，不断连；</li>
 *   <li>发送前用 {@link NetworkRegistry#hasChannel} 探测对端是否真的注册了它。</li>
 * </ol>
 * 这与 {@code ConfigSyncChannel} / {@code AgentFeatureChannel} 的处理保持一致：
 * NeoForge 收到<b>未注册</b>的 payload 是致命错误（直接断连），不同于 Forge 的静默丢弃，
 * 所以发送侧必须先探测，判不出来宁可不发。
 */
public final class CompanionGuideChannel {

    private CompanionGuideChannel() {
    }

    public static void register(PayloadRegistrar registrar) {
        // optional()：让缺少这条载荷的旧端照常握手，而不是在协商阶段就把连接判死。
        registrar.optional().playToServer(
                CompanionGuideOpenedPacket.TYPE,
                CompanionGuideOpenedPacket.STREAM_CODEC,
                CompanionGuideOpenedPacket::handle);
    }

    /** 对端（服务端）是否注册了本载荷。 */
    public static boolean supported(Connection connection) {
        return connection != null
                && NetworkRegistry.hasChannel(connection, null, CompanionGuideOpenedPacket.TYPE.id());
    }

    /**
     * 主页面打开成功后通知服务端发放手册。
     *
     * <p>只有 {@link #supported} 为真时才发包：老服务端上既不会发、也不会断线。</p>
     */
    public static void notifyScreenOpened(Connection connection, int heroId) {
        if (supported(connection)) {
            PacketDistributor.sendToServer(new CompanionGuideOpenedPacket(heroId));
        }
    }
}
