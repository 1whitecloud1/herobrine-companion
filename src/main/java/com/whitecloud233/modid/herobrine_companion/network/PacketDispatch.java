package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraftforge.network.NetworkEvent;

/**
 * 包的方向守卫与排队辅助。
 *
 * <p>Forge 单通道下,同一个包的 {@code handle} 在收包侧执行,方向由"谁发给谁"决定。
 * 过去全靠约定(C→S 包读 {@code context.getSender()},S→C 包走 {@code NetworkClientBridge}),
 * 发错方向会静默丢弃。这里把方向变成显式断言,让误发立刻报错而不是无声吞掉。
 */
public final class PacketDispatch {
    private PacketDispatch() {
    }

    /** 当前包的接收侧是否为服务端(即该包由客户端发来、在服务端处理)。 */
    public static boolean isServer(NetworkEvent.Context context) {
        return context.getDirection().getReceptionSide().isServer();
    }

    /** 当前包的接收侧是否为客户端(即该包由服务端发来、在客户端处理)。 */
    public static boolean isClient(NetworkEvent.Context context) {
        return context.getDirection().getReceptionSide().isClient();
    }

    /** 断言该包必须在服务端接收;否则抛异常,把静默丢弃变成显式报错。 */
    public static void assertServer(NetworkEvent.Context context) {
        if (!isServer(context)) {
            throw new IllegalStateException("Packet dispatched to the wrong side: expected SERVER, got "
                    + context.getDirection().getReceptionSide());
        }
    }

    /** 断言该包必须在客户端接收;否则抛异常。 */
    public static void assertClient(NetworkEvent.Context context) {
        if (!isClient(context)) {
            throw new IllegalStateException("Packet dispatched to the wrong side: expected CLIENT, got "
                    + context.getDirection().getReceptionSide());
        }
    }

    /**
     * C→S 包的 handle 标准入口:断言服务端 + 排队到主线程 + 标记已处理。
     * 等价于手写的 {@code enqueueWork + setPacketHandled},但附带方向守卫。
     */
    public static void enqueueServer(NetworkEvent.Context context, Runnable task) {
        assertServer(context);
        context.enqueueWork(task);
        context.setPacketHandled(true);
    }
}
