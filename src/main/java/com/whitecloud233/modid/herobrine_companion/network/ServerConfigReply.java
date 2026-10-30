package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.modid.herobrine_companion.config.ServerConfigUpdate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerConfigReply(int requestId, ServerConfigSnapshot snapshot, boolean canEdit, boolean accepted) {
    public ServerConfigReply(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), ServerConfigCodec.readSnapshot(buffer), buffer.readBoolean(), buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        ServerConfigCodec.writeUpdate(buffer, ServerConfigUpdate.full(snapshot));
        buffer.writeBoolean(canEdit);
        buffer.writeBoolean(accepted);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.applyServerConfig(context.getNetworkManager(), this));
    }
}
