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
        this.startPos = NetworkCodec.readVec3(buf);
        this.endPos = NetworkCodec.readVec3(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        NetworkCodec.writeVec3(buf, this.startPos);
        NetworkCodec.writeVec3(buf, this.endPos);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.handlePaleLightningArc(this));
    }
}
