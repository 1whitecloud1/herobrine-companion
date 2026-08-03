package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncHeroVisitPacket {
    private final boolean visited;

    public SyncHeroVisitPacket(boolean visited) {
        this.visited = visited;
    }

    public SyncHeroVisitPacket(FriendlyByteBuf buf) {
        this.visited = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.visited);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.setVisitedHeroDimension(this.visited));
        context.setPacketHandled(true);
    }
}
