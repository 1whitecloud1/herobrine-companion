package com.whitecloud233.modid.herobrine_companion.destructiongod.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SPacketWorldRendCinematic {
    private final double x;
    private final double y;
    private final double z;

    public SPacketWorldRendCinematic(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SPacketWorldRendCinematic(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> NetworkClientBridge.handleWorldRendCinematic(this.x, this.y, this.z));
        context.setPacketHandled(true);
    }
}
