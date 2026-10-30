package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.herobrine_companion.config.ServerConfigUpdate;
import com.whitecloud233.herobrine_companion.config.ServerRule;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ServerConfigCodecChecks {
    private static int checks;

    public static void main(String[] args) {
        for (ServerRule rule : ServerRule.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ServerConfigRequest request = new ServerConfigRequest(10 + rule.wireId(), ServerConfigUpdate.single(rule, Integer.MAX_VALUE));
                request.encode(buffer);
                ServerConfigRequest read = new ServerConfigRequest(buffer);
                check(read.equals(request) && !buffer.isReadable(), "Patch round trip lost raw value or identity");
            } finally { buffer.release(); }
        }
        FriendlyByteBuf query = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerConfigRequest.query(3).encode(query);
            check(new ServerConfigRequest(query).update().changes().isEmpty(), "Read request should be empty patch");
        } finally { query.release(); }
        for (boolean permission : new boolean[]{false, true}) {
            for (boolean accepted : new boolean[]{false, true}) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    ServerConfigReply original = new ServerConfigReply(7, ServerConfigSnapshot.defaults().with(ServerRule.AI_VISION_INTERVAL, 123), permission, accepted);
                    original.encode(buffer);
                    check(new ServerConfigReply(buffer).equals(original) && !buffer.isReadable(), "Reply failed round trip");
                } finally { buffer.release(); }
            }
        }
        reject(buffer -> buffer.writeVarInt(-1), "negative field count");
        reject(buffer -> buffer.writeVarInt(ServerRule.values().length + 1), "excessive field count");
        reject(buffer -> { buffer.writeVarInt(1); buffer.writeVarInt(100); buffer.writeVarInt(1); }, "unknown rule id");
        reject(buffer -> { buffer.writeVarInt(2); buffer.writeVarInt(0); buffer.writeVarInt(1); buffer.writeVarInt(0); buffer.writeVarInt(0); }, "duplicate rule id");
        FriendlyByteBuf incomplete = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerConfigCodec.writeUpdate(incomplete, new ServerConfigUpdate(Map.of()));
            try { ServerConfigCodec.readSnapshot(incomplete); throw new AssertionError("Incomplete snapshot accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        } finally { incomplete.release(); }

        FriendlyByteBuf legacy = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            boolean[] head = {true, false, true, false, true, false, true, false};
            for (boolean value : head) legacy.writeBoolean(value);
            legacy.writeVarInt(127);
            legacy.writeBoolean(true);
            legacy.writeBoolean(false);
            byte[] baseline = bytes(legacy);
            ServerConfigUpdate update = ServerConfigCodec.readLegacy(legacy);
            check(!update.changes().containsKey(ServerRule.PRANKSTER_FAKE_CHEST), "Legacy absent flag invented");
            ServerConfigCodec.writeLegacy(encoded, update);
            check(Arrays.equals(baseline, bytes(encoded)), "Old config wire order changed");
            check(update.changes().get(ServerRule.AI_VISION_INTERVAL) == 127 && update.changes().get(ServerRule.CLEAVE_SKILL) == 1, "Legacy mapping shifted");
        } finally { legacy.release(); encoded.release(); }
        System.out.println("ServerConfigCodecChecks: " + checks + " checks passed");
    }

    private static byte[] bytes(FriendlyByteBuf buffer) { byte[] bytes = new byte[buffer.writerIndex()]; buffer.getBytes(0, bytes); return bytes; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private static void reject(Consumer<FriendlyByteBuf> write, String name) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(buffer);
            try { ServerConfigCodec.readUpdate(buffer); throw new AssertionError("Accepted " + name); }
            catch (IllegalArgumentException expected) { checks++; }
        } finally { buffer.release(); }
    }
}
