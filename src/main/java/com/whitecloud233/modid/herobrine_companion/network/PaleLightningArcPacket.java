package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaleLightningArcPacket {
    public final Vec3 startPos;
    public final Vec3 endPos;

    public PaleLightningArcPacket(Vec3 startPos, Vec3 endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    public PaleLightningArcPacket(FriendlyByteBuf buf) {
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeDouble(this.endPos.x);
        buf.writeDouble(this.endPos.y);
        buf.writeDouble(this.endPos.z);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NetworkClientBridge.handlePaleLightningArc(this));
        context.setPacketHandled(true);
    }
}
