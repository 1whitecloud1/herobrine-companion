package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TriggerEternalOathPacket {

    public TriggerEternalOathPacket() {
    }

    public TriggerEternalOathPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        PacketDispatch.assertClient(ctx);
        ctx.enqueueWork(NetworkClientBridge::triggerEternalOath);
        ctx.setPacketHandled(true);
    }
}
