package com.whitecloud233.modid.herobrine_companion.client.agent;

import com.whitecloud233.modid.herobrine_companion.network.AgentFeatureChannel;

/**
 * 客户端持有的"服务端能力位"（交接 4.2 / T22）。
 *
 * <p><b>单一职责</b>：只保存服务端在一次握手中声明的能力位，并回答
 * "这个能力现在可用吗"。不发包、不判断 UI、不缓存到磁盘。</p>
 *
 * <p><b>默认值必须是 0（全部不可用）</b>：连接尚未握手、连的是旧服务端、
 * 或通道协商失败时，新工具都不该出现在模型工具清单里——否则玩家点了必然
 * 得到 {@code unknown_tool}。旧工具（主通道里的九个 + 旧 agent_task 场景）
 * 不受本开关影响，保持原行为。</p>
 */
public final class ClientAgentFeatures {

    /** 0 表示"还没确认支持任何扩展能力"。 */
    private static volatile int serverFeatures = 0;

    private ClientAgentFeatures() {
    }

    /** 记录服务端在握手中声明的能力位。 */
    public static void accept(int features) {
        // 只保留本端也实现的位：交集才是真正可用的能力。
        serverFeatures = features & AgentFeatureChannel.ALL_FEATURES;
    }

    /** 服务端声明的能力位（已与本端取交集）。 */
    public static int serverFeatures() {
        return serverFeatures;
    }

    /** 某个扩展能力当前是否可用。 */
    public static boolean supports(int feature) {
        return (serverFeatures & feature) != 0;
    }

    /** 是否有任何扩展能力可用（用于决定是否展示任务控制等新交互）。 */
    public static boolean hasAny() {
        return serverFeatures != 0;
    }

    /** 退出世界 / 断线：回到"未确认"状态，避免把上一个存档的能力带到下一个。 */
    public static void clear() {
        serverFeatures = 0;
    }
}
