package com.whitecloud233.modid.herobrine_companion.destructiongod.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkCodec;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DestructionGodFaultSplitPacket {
    public final Vec3 origin;
    public final Vec3 direction;
    public final float length;
    public final int terrainHalfWidth;
    public final int splitDistance;
    public final int chargeTicks;
    public final int lifetime;

    public DestructionGodFaultSplitPacket(Vec3 origin, Vec3 direction, float length, int terrainHalfWidth, int splitDistance, int chargeTicks, int lifetime) {
        this.origin = origin;
        this.direction = direction;
        this.length = length;
        this.terrainHalfWidth = terrainHalfWidth;
        this.splitDistance = splitDistance;
        this.chargeTicks = chargeTicks;
        this.lifetime = lifetime;
    }

    public DestructionGodFaultSplitPacket(FriendlyByteBuf buf) {
        this.origin = NetworkCodec.readVec3(buf);
        this.direction = NetworkCodec.readVec3(buf);
        this.length = buf.readFloat();
        this.terrainHalfWidth = buf.readVarInt();
        this.splitDistance = buf.readVarInt();
        this.chargeTicks = buf.readVarInt();
        this.lifetime = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        NetworkCodec.writeVec3(buf, this.origin);
        NetworkCodec.writeVec3(buf, this.direction);
        buf.writeFloat(this.length);
        buf.writeVarInt(this.terrainHalfWidth);
        buf.writeVarInt(this.splitDistance);
        buf.writeVarInt(this.chargeTicks);
        buf.writeVarInt(this.lifetime);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.handleDestructionGodFaultSplit(this));
    }
}
