package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PaleLightningArcPacket {
    public final Vec3 startPos;
    public final Vec3 endPos;

    // 服务端发包时调用的构造函数
    public PaleLightningArcPacket(Vec3 startPos, Vec3 endPos) {
        this.startPos = startPos;
        this.endPos = endPos;
    }

    // 客户端接收时调用的解码构造函数
    public PaleLightningArcPacket(FriendlyByteBuf buf) {
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.endPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    // 编码打包数据
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeDouble(this.endPos.x);
        buf.writeDouble(this.endPos.y);
        buf.writeDouble(this.endPos.z);
    }

    // 处理接收到的数据包
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 将逻辑推给客户端处理类，防止服务器加载该类时崩溃
            ClientPacketHandler.handlePaleLightningArc(this);
        });
        context.setPacketHandled(true);
    }
}