package com.whitecloud233.modid.herobrine_companion.fight.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SPacketStartCollapse {

    public SPacketStartCollapse() {
    }

    public SPacketStartCollapse(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(NetworkClientBridge::startCollapse);
        context.setPacketHandled(true);
    }
}
