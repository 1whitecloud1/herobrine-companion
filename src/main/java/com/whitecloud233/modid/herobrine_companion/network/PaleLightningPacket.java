package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaleLightningPacket {
    public final double x;
    public final double y;
    public final double z;
    public final float width;

    public PaleLightningPacket(double x, double y, double z, float width) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
    }

    public PaleLightningPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.width = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(width);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> NetworkClientBridge.handlePaleLightning(this));
        context.setPacketHandled(true);
    }
}
