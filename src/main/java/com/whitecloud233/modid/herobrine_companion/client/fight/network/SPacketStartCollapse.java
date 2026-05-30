package com.whitecloud233.modid.herobrine_companion.client.fight.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SPacketStartCollapse {

    public SPacketStartCollapse() {
    }

    public SPacketStartCollapse(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(NetworkClientBridge::startCollapse);
        context.setPacketHandled(true);
    }
}
