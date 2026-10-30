package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.modid.herobrine_companion.config.ServerConfigService;
import com.whitecloud233.modid.herobrine_companion.config.ServerConfigUpdate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

/** 可选配置通道：空补丁查询当前值，非空补丁只修改显式字段。 */
public record ServerConfigRequest(int requestId, ServerConfigUpdate update) {
    public ServerConfigRequest(FriendlyByteBuf buffer) { this(buffer.readVarInt(), ServerConfigCodec.readUpdate(buffer)); }
    public static ServerConfigRequest query(int id) { return new ServerConfigRequest(id, new ServerConfigUpdate(Map.of())); }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        ServerConfigCodec.writeUpdate(buffer, update);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            var player = context.getSender();
            if (player == null) return;
            ServerConfigService.Result result = update.changes().isEmpty()
                    ? new ServerConfigService.Result(ServerConfigAccess.snapshot(), true, false)
                    : ServerConfigAccess.update(player, update);
            ConfigSyncChannel.reply(player, requestId, result.authorized());
            if (result.changed()) ConfigSyncChannel.broadcast(player.getServer());
        });
    }
}
