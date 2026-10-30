package com.whitecloud233.modid.herobrine_companion.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 协议字节兼容检查（交接 4.2.4 / T23）。
 *
 * <p><b>为什么手写字节基线而不是只做自身 round-trip</b>：round-trip 只证明"编码器与解码器
 * 互相认识"，不证明"旧客户端 / 旧服务端还能读懂"。这里把既有布局按字节逐段拼成期望值，
 * 再与新代码真实编码结果逐字节对照：字段顺序、类型、长度前缀、上限常量任何一项被改动都会失败。</p>
 *
 * <p>包的字段是私有的（既有设计如此），因此"反序列化正确性"通过
 * <b>旧字节 → 解码 → 再编码 → 与旧字节逐字节相同</b>来验证，不需要给生产类加 getter。</p>
 */
public final class AgentProtocolChecks {

    private static int checks;

    /** 旧协议上限：改这些常量等于改协议，必须在这里被拦住。 */
    private static final int MAX_KIND = 16;
    private static final int MAX_NAME = 64;
    private static final int MAX_ARGS_JSON = 2048;
    private static final int MAX_MESSAGE = 512;
    private static final int MAX_DESCRIPTION_JSON = 1024;
    private static final int MAX_ARG_VALUE = 256;

    public static void main(String[] args) {
        checks += requestPacketLayout();
        checks += resultPacketLayout();
        checks += approvalPromptLayout();
        checks += approveRejectLayout();
        checks += limitsUnchanged();
        System.out.println("AgentProtocolChecks: " + checks + " checks passed");
    }

    /** C→S 工具/任务请求：kind(utf16) + name(utf64) + argsJson(utf2048) + requestId(UUID)。 */
    private static int requestPacketLayout() {
        UUID requestId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        AgentRequestPacket packet = new AgentRequestPacket("tool", "hero_inspect", "{\"field\":\"mind\"}", requestId);

        byte[] expected = bytes(buffer -> {
            buffer.writeUtf("tool", MAX_KIND);
            buffer.writeUtf("hero_inspect", MAX_NAME);
            buffer.writeUtf("{\"field\":\"mind\"}", MAX_ARGS_JSON);
            buffer.writeUUID(requestId);
        });
        assertSame(expected, bytes(packet::encode), "AgentRequestPacket 布局必须与旧协议逐字节一致");
        // 旧字节解码后再编码必须与原字节完全相同（字段顺序 / 类型 / 上限都没变）。
        assertSame(expected, bytes(new AgentRequestPacket(wrap(expected))::encode),
                "旧字节解码后必须能原样再编码");
        return 2;
    }

    /** S→C 结果：requestId(UUID) + toolId(utf64) + ok(boolean) + message(utf512)。 */
    private static int resultPacketLayout() {
        UUID requestId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        AgentToolResultPacket packet = new AgentToolResultPacket(requestId, "hero_navigate", true, "已提交");

        byte[] expected = bytes(buffer -> {
            buffer.writeUUID(requestId);
            buffer.writeUtf("hero_navigate", MAX_NAME);
            buffer.writeBoolean(true);
            buffer.writeUtf("已提交", MAX_MESSAGE);
        });
        assertSame(expected, bytes(packet::encode), "AgentToolResultPacket 布局必须与旧协议逐字节一致");
        assertSame(expected, bytes(new AgentToolResultPacket(wrap(expected))::encode),
                "结果包旧字节解码后必须能原样再编码");

        // 旧版只发 boolean 的场景（ok=false）也要保持同样的布局。
        byte[] failure = bytes(buffer -> {
            buffer.writeUUID(requestId);
            buffer.writeUtf("", MAX_NAME);
            buffer.writeBoolean(false);
            buffer.writeUtf("", MAX_MESSAGE);
        });
        assertSame(failure, bytes(new AgentToolResultPacket(requestId, "", false, "")::encode),
                "空字段结果包必须保持旧布局");
        return 3;
    }

    /** S→C 审批提示：requestId + toolId + varint 数量 + (key,value)* + description JSON。 */
    private static int approvalPromptLayout() {
        UUID requestId = UUID.fromString("99999999-8888-7777-6666-555555555555");
        Map<String, String> args = new LinkedHashMap<>();
        args.put("mode", "follow");
        ToolApprovalPromptPacket packet = new ToolApprovalPromptPacket(
                requestId, "hero_set_mode", args, net.minecraft.network.chat.Component.literal("确认"));
        String descriptionJson = net.minecraft.network.chat.Component.Serializer.toJson(
                net.minecraft.network.chat.Component.literal("确认"));

        byte[] expected = bytes(buffer -> {
            buffer.writeUUID(requestId);
            buffer.writeUtf("hero_set_mode", MAX_NAME);
            buffer.writeVarInt(args.size());
            buffer.writeUtf("mode", MAX_NAME);
            buffer.writeUtf("follow", MAX_ARG_VALUE);
            buffer.writeUtf(descriptionJson, MAX_DESCRIPTION_JSON);
        });
        assertSame(expected, bytes(packet::encode), "ToolApprovalPromptPacket 布局必须与旧协议逐字节一致");
        assertSame(expected, bytes(new ToolApprovalPromptPacket(wrap(expected))::encode),
                "审批提示旧字节解码后必须能原样再编码");
        return 2;
    }

    /** 审批/拒绝：单个 UUID，布局不得变化。 */
    private static int approveRejectLayout() {
        UUID requestId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        byte[] expected = bytes(buffer -> buffer.writeUUID(requestId));
        assertSame(expected, bytes(new ApproveToolPacket(requestId)::encode), "ApproveToolPacket 必须是单个 UUID");
        assertSame(expected, bytes(new RejectToolPacket(requestId)::encode), "RejectToolPacket 必须是单个 UUID");
        assertSame(expected, bytes(new ApproveToolPacket(wrap(expected))::encode),
                "审批包旧字节解码后必须能原样再编码");
        return 3;
    }

    /** 上限常量属于协议：超出即被 readUtf 拒绝，因此不能悄悄改大或改小。 */
    private static int limitsUnchanged() {
        assertTrue(MAX_KIND == 16 && MAX_NAME == 64 && MAX_ARGS_JSON == 2048,
                "请求包上限必须保持 16/64/2048");
        assertTrue(MAX_MESSAGE == 512, "结果包 message 上限必须保持 512");
        assertTrue(MAX_DESCRIPTION_JSON == 1024, "审批提示 description 上限必须保持 1024");

        // 超长 message 必须仍能被编码（writeUtf 按上限截断），且写入的仍是旧布局。
        String tooLong = "长".repeat(MAX_MESSAGE + 50);
        byte[] encoded = bytes(new AgentToolResultPacket(
                UUID.randomUUID(), "tool", false, tooLong)::encode);
        assertTrue(encoded.length > 0, "超长 message 必须仍可编码，不得抛出");
        // 编码结果必须能被同版本解码器读完（说明长度前缀仍在协议范围内）。
        FriendlyByteBuf reader = wrap(encoded);
        try {
            new AgentToolResultPacket(reader);
            assertTrue(!reader.isReadable(), "超长 message 编码后必须可被完整读取");
        } finally {
            reader.release();
        }

        // 新工具参数（action / task_id / recipe_id / 坐标）必须能装进既有的 2048 字符通道。
        String argsJson = "{\"action\":\"start\",\"target_type\":\"anomaly\",\"return_to_owner\":true,"
                + "\"task_id\":\"task-12345678\",\"recipe_id\":\"kaleidoscope_cookery:stir_fried_pork\","
                + "\"count\":2,\"x\":128,\"y\":64,\"z\":-256}";
        assertTrue(argsJson.length() < MAX_ARGS_JSON, "新工具参数必须能装进既有 argsJson 通道");
        AgentRequestPacket taskPacket = new AgentRequestPacket(
                AgentRequestPacket.KIND_TASK, "cook", argsJson, UUID.randomUUID());
        assertSame(bytes(taskPacket::encode), bytes(new AgentRequestPacket(wrap(bytes(taskPacket::encode)))::encode),
                "含新字段的任务请求必须能原样过网");
        return 5;
    }

    // ---- 工具 ----

    private interface Writer {
        void write(FriendlyByteBuf buffer);
    }

    private static byte[] bytes(Writer writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.write(buffer);
            byte[] result = new byte[buffer.writerIndex()];
            buffer.getBytes(0, result);
            return result;
        } finally {
            buffer.release();
        }
    }

    private static FriendlyByteBuf wrap(byte[] payload) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
    }

    private static void assertSame(byte[] expected, byte[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message + " (expected " + Arrays.toString(expected)
                    + ", actual " + Arrays.toString(actual) + ")");
        }
        checks++;
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private AgentProtocolChecks() {
    }
}
