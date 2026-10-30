package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.herobrine_companion.config.ServerConfigUpdate;
import com.whitecloud233.herobrine_companion.config.ServerRule;
import net.minecraft.network.FriendlyByteBuf;

import java.util.EnumMap;

/** 只负责配置协议编解码，不读取 Forge 配置或修改世界状态。 */
public final class ServerConfigCodec {
    private ServerConfigCodec() {
    }

    public static ServerConfigUpdate readLegacy(FriendlyByteBuf buffer) {
        EnumMap<ServerRule, Integer> values = new EnumMap<>(ServerRule.class);
        for (int id = 0; id < 12; id++) {
            if (id == 11 && !buffer.isReadable()) break;
            ServerRule rule = ServerRule.fromWireId(id);
            values.put(rule, rule.isToggle() ? (buffer.readBoolean() ? 1 : 0) : buffer.readVarInt());
        }
        return new ServerConfigUpdate(values);
    }

    public static void writeLegacy(FriendlyByteBuf buffer, ServerConfigUpdate update) {
        for (int id = 0; id < 12; id++) {
            ServerRule rule = ServerRule.fromWireId(id);
            Integer value = update.changes().get(rule);
            if (id == 11 && value == null) break;
            if (value == null) throw new IllegalArgumentException("Missing legacy configuration field: " + rule);
            if (rule.isToggle()) buffer.writeBoolean(value != 0);
            else buffer.writeVarInt(value);
        }
    }

    public static ServerConfigUpdate readUpdate(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > ServerRule.values().length) throw new IllegalArgumentException("Invalid configuration field count");
        EnumMap<ServerRule, Integer> values = new EnumMap<>(ServerRule.class);
        for (int i = 0; i < count; i++) {
            ServerRule rule = ServerRule.fromWireId(buffer.readVarInt());
            int value = buffer.readVarInt();
            if (values.put(rule, value) != null) throw new IllegalArgumentException("Duplicate configuration field: " + rule);
        }
        return new ServerConfigUpdate(values);
    }

    public static void writeUpdate(FriendlyByteBuf buffer, ServerConfigUpdate update) {
        buffer.writeVarInt(update.changes().size());
        for (ServerRule rule : ServerRule.values()) {
            Integer value = update.changes().get(rule);
            if (value != null) { buffer.writeVarInt(rule.wireId()); buffer.writeVarInt(value); }
        }
    }

    public static ServerConfigSnapshot readSnapshot(FriendlyByteBuf buffer) {
        ServerConfigUpdate update = readUpdate(buffer);
        if (update.changes().size() != ServerRule.values().length) throw new IllegalArgumentException("Incomplete server configuration snapshot");
        return new ServerConfigSnapshot(update.changes());
    }
}
