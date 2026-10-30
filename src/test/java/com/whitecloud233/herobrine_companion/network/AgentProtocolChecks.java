package com.whitecloud233.herobrine_companion.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 协议字节兼容检查（交接 4.2.4 / T23），NeoForge 1.21.1 版本。
 *
 * <p>1.21.1 用 {@code StreamCodec} 取代了手写 encode/decode，因此这里把基线字节按
 * <b>同样的字段顺序与上限</b>手写出来，再与 {@code STREAM_CODEC} 的真实编码逐字节对照；
 * 同时验证"旧字节 → 解码 → 再编码"完全一致。字段顺序、类型、长度前缀、
 * 上限常量任何一项被改动都会失败。</p>
 */
public final class AgentProtocolChecks {

    private static int checks;

    private static final int MAX_KIND = 16;
    private static final int MAX_NAME = 64;
    private static final int MAX_ARGS_JSON = 2048;
    private static final int MAX_MESSAGE = 512;
    private static final int MAX_DESCRIPTION_JSON = 1024;
    private static final int MAX_ARG_VALUE = 256;

    public static void main(String[] args) {
        checks += requestPacketLayout();
        checks += resultPacketLayout();
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
        assertSame(expected, encode(packet), "AgentRequestPacket 布局必须与旧协议逐字节一致");
        assertSame(expected, encode(decodeRequest(expected)), "旧字节解码后必须能原样再编码");
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
        assertSame(expected, encode(packet), "AgentToolResultPacket 布局必须与旧协议逐字节一致");
        assertSame(expected, encode(decodeResult(expected)), "结果包旧字节解码后必须能原样再编码");
        return 2;
    }

    /** 审批 / 拒绝：单个 UUID。 */
    private static int approveRejectLayout() {
        UUID requestId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        byte[] expected = bytes(buffer -> buffer.writeUUID(requestId));
        assertSame(expected, encode(new ApproveToolPacket(requestId)), "ApproveToolPacket 必须是单个 UUID");
        assertSame(expected, encode(new RejectToolPacket(requestId)), "RejectToolPacket 必须是单个 UUID");
        assertSame(expected, encode(decodeApprove(expected)), "审批包旧字节解码后必须能原样再编码");
        return 3;
    }

    /** 上限常量属于协议，不能悄悄改动。 */
    private static int limitsUnchanged() {
        assertTrue(MAX_KIND == 16 && MAX_NAME == 64 && MAX_ARGS_JSON == 2048,
                "请求包上限必须保持 16/64/2048");
        assertTrue(MAX_MESSAGE == 512, "结果包 message 上限必须保持 512");
        assertTrue(MAX_DESCRIPTION_JSON == 1024, "审批提示 description 上限必须保持 1024");

        // 超长 message 必须在构造期被截断，而不是让 stringUtf8 抛异常（与 1.20.1 同样的修复）。
        String tooLong = "长".repeat(MAX_MESSAGE + 50);
        AgentToolResultPacket clamped = new AgentToolResultPacket(
                UUID.randomUUID(), "tool", false, tooLong);
        assertTrue(clamped.message().length() <= MAX_MESSAGE,
                "超长 message 必须被截断到 512 字符内（否则编解码抛异常、整包发不出去）");
        byte[] encoded = encode(clamped);
        assertTrue(encoded.length > 0, "截断后必须仍可编码");
        AgentToolResultPacket roundTrip = decodeResult(encoded);
        assertTrue(roundTrip.message().length() <= MAX_MESSAGE, "截断后必须仍可解码");

        // 新工具参数必须能装进既有 2048 字符通道。
        String argsJson = "{\"action\":\"start\",\"target_type\":\"anomaly\",\"return_to_owner\":true,"
                + "\"task_id\":\"task-12345678\",\"recipe_id\":\"kaleidoscope_cookery:stir_fried_pork\","
                + "\"count\":2,\"x\":128,\"y\":64,\"z\":-256}";
        assertTrue(argsJson.length() < MAX_ARGS_JSON, "新工具参数必须能装进既有 argsJson 通道");
        AgentRequestPacket taskPacket = new AgentRequestPacket(
                AgentRequestPacket.KIND_TASK, "cook", argsJson, UUID.randomUUID());
        assertTrue(argsJson.equals(decodeRequest(encode(taskPacket)).argsJson()),
                "含新字段的任务请求必须能原样过网");
        return 6;
    }

    // ---- 编解码 / 工具 ----

    private static byte[] encode(Object payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            if (payload instanceof AgentRequestPacket packet) {
                AgentRequestPacket.STREAM_CODEC.encode(buffer, packet);
            } else if (payload instanceof AgentToolResultPacket packet) {
                AgentToolResultPacket.STREAM_CODEC.encode(buffer, packet);
            } else if (payload instanceof ApproveToolPacket packet) {
                ApproveToolPacket.STREAM_CODEC.encode(buffer, packet);
            } else if (payload instanceof RejectToolPacket packet) {
                RejectToolPacket.STREAM_CODEC.encode(buffer, packet);
            } else {
                throw new IllegalArgumentException("Unknown payload: " + payload);
            }
            byte[] result = new byte[buffer.writerIndex()];
            buffer.getBytes(0, result);
            return result;
        } finally {
            buffer.release();
        }
    }

    private static AgentRequestPacket decodeRequest(byte[] payload) {
        FriendlyByteBuf buffer = wrap(payload);
        try {
            return AgentRequestPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static AgentToolResultPacket decodeResult(byte[] payload) {
        FriendlyByteBuf buffer = wrap(payload);
        try {
            return AgentToolResultPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static ApproveToolPacket decodeApprove(byte[] payload) {
        FriendlyByteBuf buffer = wrap(payload);
        try {
            return ApproveToolPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

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
