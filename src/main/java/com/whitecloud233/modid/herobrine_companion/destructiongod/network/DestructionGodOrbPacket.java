package com.whitecloud233.modid.herobrine_companion.destructiongod.network;

import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DestructionGodOrbPacket {
    public final Vec3 startPos;
    public final Vec3 impactPos;
    public final int fallTicks;
    public final float startRadius;
    public final float maxRadius;
    public final float apexHeight;

    public DestructionGodOrbPacket(Vec3 startPos, Vec3 impactPos, int fallTicks, float startRadius, float maxRadius, float apexHeight) {
        this.startPos = startPos;
        this.impactPos = impactPos;
        this.fallTicks = fallTicks;
        this.startRadius = startRadius;
        this.maxRadius = maxRadius;
        this.apexHeight = apexHeight;
    }

    public DestructionGodOrbPacket(FriendlyByteBuf buf) {
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.impactPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.fallTicks = buf.readInt();
        this.startRadius = buf.readFloat();
        this.maxRadius = buf.readFloat();
        this.apexHeight = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeDouble(this.impactPos.x);
        buf.writeDouble(this.impactPos.y);
        buf.writeDouble(this.impactPos.z);
        buf.writeInt(this.fallTicks);
        buf.writeFloat(this.startRadius);
        buf.writeFloat(this.maxRadius);
        buf.writeFloat(this.apexHeight);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> NetworkClientBridge.handleDestructionGodOrb(this));
        context.setPacketHandled(true);
    }
}
