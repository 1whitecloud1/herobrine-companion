package com.whitecloud233.modid.herobrine_companion.destructiongod.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkCodec;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DestructionGodLightningPacket {
    public final Vec3 startPos;
    public final Vec3 endPos;
    public final float width;

    public DestructionGodLightningPacket(Vec3 startPos, Vec3 endPos, float width) {
        this.startPos = startPos;
        this.endPos = endPos;
        this.width = width;
    }

    public DestructionGodLightningPacket(FriendlyByteBuf buf) {
        this.startPos = NetworkCodec.readVec3(buf);
        this.endPos = NetworkCodec.readVec3(buf);
        this.width = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        NetworkCodec.writeVec3(buf, this.startPos);
        NetworkCodec.writeVec3(buf, this.endPos);
        buf.writeFloat(this.width);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.handleDestructionGodLightning(this));
    }
}
