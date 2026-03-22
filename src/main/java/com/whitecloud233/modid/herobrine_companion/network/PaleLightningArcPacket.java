package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.fight.particles.PaleLightningArcParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
            // 使用 DistExecutor 确保这段代码只在客户端执行，防止物理服务端崩溃
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientLevel level = Minecraft.getInstance().level;
                if (level != null) {
                    Minecraft.getInstance().particleEngine.add(
                            new PaleLightningArcParticle(
                                    level,
                                    this.startPos.x, this.startPos.y, this.startPos.z,
                                    this.endPos
                            )
                    );
                }
            });
        });
        context.setPacketHandled(true);
    }
}
