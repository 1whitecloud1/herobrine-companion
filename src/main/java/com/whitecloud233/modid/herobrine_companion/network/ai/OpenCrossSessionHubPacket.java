package com.whitecloud233.modid.herobrine_companion.network.ai;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenCrossSessionHubPacket {
    private final int entityId;

    public OpenCrossSessionHubPacket(int entityId) {
        this.entityId = entityId;
    }

    public OpenCrossSessionHubPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NetworkClientBridge.openCrossSessionHub(this.entityId));
        context.setPacketHandled(true);
    }
}
