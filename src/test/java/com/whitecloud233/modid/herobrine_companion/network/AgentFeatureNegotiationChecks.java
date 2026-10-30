package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentFeatures;

/**
 * 扩展能力协商检查（交接 4.2 / T22）：能力位、交集、旧端回退。
 *
 * <p>跑生产类 {@link AgentFeatureChannel}（能力位常量）与 {@link ClientAgentFeatures}
 * （客户端能力位状态）。这组检查证明"未确认时扩展能力不可用"，而不是只证明常量存在。</p>
 */
public final class AgentFeatureNegotiationChecks {

    public static void main(String[] args) {
        int checks = 0;

        // ---- 能力位：互不重叠、全覆盖 ----
        int[] features = {
                AgentFeatureChannel.FEATURE_TASK_CONTROL,
                AgentFeatureChannel.FEATURE_NAVIGATE,
                AgentFeatureChannel.FEATURE_BLOCK_QUERY,
                AgentFeatureChannel.FEATURE_COOK,
                AgentFeatureChannel.FEATURE_TRANSFER,
                AgentFeatureChannel.FEATURE_INTERACT,
                AgentFeatureChannel.FEATURE_REMEMBER,
                AgentFeatureChannel.FEATURE_BUILD_PREVIEW,
                AgentFeatureChannel.FEATURE_GATHER,
        };
        int union = 0;
        for (int feature : features) {
            checks += check(feature != 0, "能力位不能是 0");
            checks += check((feature & (feature - 1)) == 0, "能力位必须是单比特: " + feature);
            checks += check((union & feature) == 0, "能力位不能重复: " + feature);
            union |= feature;
        }
        checks += check(union == AgentFeatureChannel.ALL_FEATURES, "ALL_FEATURES 必须等于全部位之和");

        // ---- 默认不可用（最重要的一条：旧服务端 / 未握手） ----
        ClientAgentFeatures.clear();
        checks += check(ClientAgentFeatures.serverFeatures() == 0, "初始能力位必须为 0");
        checks += check(!ClientAgentFeatures.hasAny(), "未握手时不得声称有任何扩展能力");
        checks += check(!ClientAgentFeatures.supports(AgentFeatureChannel.FEATURE_NAVIGATE),
                "未握手时 hero_navigate 必须不可用（否则旧服务端只会回 unknown_tool）");
        checks += check(!ClientAgentFeatures.supports(AgentFeatureChannel.FEATURE_COOK),
                "未握手时 hero_cook 必须不可用");

        // ---- 握手后：只接受交集 ----
        ClientAgentFeatures.accept(AgentFeatureChannel.ALL_FEATURES);
        checks += check(ClientAgentFeatures.hasAny(), "握手后应识别出扩展能力");
        for (int feature : features) {
            checks += check(ClientAgentFeatures.supports(feature), "握手后每个能力都应可用: " + feature);
        }

        // 服务端声明了本端不实现的高位：必须被裁掉，不能留下"看似支持"的位。
        int unknownHighBit = 1 << 20;
        ClientAgentFeatures.accept(unknownHighBit);
        checks += check(ClientAgentFeatures.serverFeatures() == 0,
                "本端未实现的能力位必须被裁掉");
        checks += check(!ClientAgentFeatures.hasAny(), "只剩未知位时不得声称有能力可用");

        // 部分支持：只启用声明的那几项。
        ClientAgentFeatures.accept(AgentFeatureChannel.FEATURE_NAVIGATE | AgentFeatureChannel.FEATURE_GATHER);
        checks += check(ClientAgentFeatures.supports(AgentFeatureChannel.FEATURE_NAVIGATE),
                "交集内能力应可用");
        checks += check(ClientAgentFeatures.supports(AgentFeatureChannel.FEATURE_GATHER),
                "交集内能力应可用");
        checks += check(!ClientAgentFeatures.supports(AgentFeatureChannel.FEATURE_COOK),
                "未声明的能力必须不可用");

        // ---- 退出世界：回到未确认，避免跨存档串能力 ----
        ClientAgentFeatures.clear();
        checks += check(ClientAgentFeatures.serverFeatures() == 0, "退出后必须清空能力位");
        checks += check(!ClientAgentFeatures.hasAny(), "退出后不得残留上一个存档的能力");

        // ---- 握手包本身必须是两个窄字段（UUID + varint 位）且能往返 ----
        checks += packetRoundTrip();

        System.out.println("AgentFeatureNegotiationChecks: " + checks + " checks passed");
    }

    private static int packetRoundTrip() {
        int checks = 0;
        java.util.UUID requestId = java.util.UUID.randomUUID();
        AgentFeatureReply reply = new AgentFeatureReply(requestId, AgentFeatureChannel.ALL_FEATURES);
        io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(raw);
        try {
            reply.encode(buffer);
            byte[] bytes = new byte[buffer.writerIndex()];
            buffer.getBytes(0, bytes);
            // 16 字节 UUID + 1~2 字节 varint 位，远小于任何协议上限。
            checks += check(bytes.length <= 18, "能力回执必须足够窄（实际 " + bytes.length + " 字节）");

            net.minecraft.network.FriendlyByteBuf reader =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
            try {
                AgentFeatureReply decoded = new AgentFeatureReply(reader);
                checks += check(requestId.equals(decoded.requestId()), "能力回执必须还原 requestId");
                checks += check(decoded.features() == AgentFeatureChannel.ALL_FEATURES,
                        "能力回执必须还原能力位");
            } finally {
                reader.release();
            }
        } finally {
            buffer.release();
        }

        AgentFeatureRequest request = new AgentFeatureRequest(requestId);
        net.minecraft.network.FriendlyByteBuf requestBuffer =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            request.encode(requestBuffer);
            byte[] bytes = new byte[requestBuffer.writerIndex()];
            requestBuffer.getBytes(0, bytes);
            checks += check(bytes.length == 16, "能力查询必须是单个 UUID（实际 " + bytes.length + " 字节）");
            net.minecraft.network.FriendlyByteBuf reader =
                    new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
            try {
                checks += check(requestId.equals(new AgentFeatureRequest(reader).requestId()),
                        "能力查询必须还原 requestId");
            } finally {
                reader.release();
            }
        } finally {
            requestBuffer.release();
        }
        return checks;
    }

    private static int check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        return 1;
    }

    private AgentFeatureNegotiationChecks() {
    }
}
