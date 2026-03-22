package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PaleLightningPacket {
    public final double x;
    public final double y;
    public final double z;
    public final float width;

    // 服务端发包用的构造函数
    public PaleLightningPacket(double x, double y, double z, float width) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
    }

    // 客户端收包用的解码构造函数
    public PaleLightningPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.width = buf.readFloat();
    }

    // 编码写入网络流 (对应你其他包的 encode)
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(width);
    }

    // 客户端处理逻辑
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // 将逻辑推给客户端处理类，防止服务器崩溃
            ClientPacketHandler.handlePaleLightning(this);
        });
        context.setPacketHandled(true);
    }
}