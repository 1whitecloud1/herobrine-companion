package com.whitecloud233.modid.herobrine_companion.destructiongod.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkCodec;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DestructionGodThunderSkyNetPacket {
    public final Vec3 center;
    public final double cloudY;
    public final float radius;
    public final int lifetime;
    public final int seed;

    public DestructionGodThunderSkyNetPacket(Vec3 center, double cloudY, float radius, int lifetime, int seed) {
        this.center = center;
        this.cloudY = cloudY;
        this.radius = radius;
        this.lifetime = lifetime;
        this.seed = seed;
    }

    public DestructionGodThunderSkyNetPacket(FriendlyByteBuf buf) {
        this.center = NetworkCodec.readVec3(buf);
        this.cloudY = buf.readDouble();
        this.radius = buf.readFloat();
        this.lifetime = buf.readVarInt();
        this.seed = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        NetworkCodec.writeVec3(buf, this.center);
        buf.writeDouble(this.cloudY);
        buf.writeFloat(this.radius);
        buf.writeVarInt(this.lifetime);
        buf.writeInt(this.seed);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.handleDestructionGodThunderSkyNet(this));
    }
}
