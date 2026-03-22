package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.fight.particles.PaleLightningArcParticle;
import com.whitecloud233.herobrine_companion.client.fight.particles.PaleLightningPillarParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPacketHandler {

    /**
     * 处理苍白雷电柱数据包 (PaleLightningPacket)
     * 用于生成落地雷特效。
     */
    public static void handlePaleLightning(PaleLightningPacket packet, IPayloadContext context) {
        // 1.21.1 网络包默认在网络线程执行，必须投递给主线程处理客户端逻辑
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                // 使用数据包中的原始 x, y, z 作为地面落点
                Vec3 groundPos = new Vec3(packet.x(), packet.y(), packet.z());

                // 生成雷电柱粒子
                mc.particleEngine.add(new PaleLightningPillarParticle(
                        mc.level,
                        packet.x(), packet.y() + 60.0, packet.z(), // 天空起点
                        groundPos,
                        packet.width()
                ));
            }
        });
    }

    /**
     * 处理苍白雷电弧数据包 (PaleLightningArcPacket)
     * 用于生成连接两点的雷电网特效。
     */
    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        // [说明]
        // 这里的 handlePaleLightningArc 是由 PaleLightningArcPacket.java 内部的
        // handle 方法通过 context.enqueueWork() 调用的。
        // 所以，当运行到这里时，已经处于主线程，可以直接安全地调用 Minecraft 逻辑。

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // 获取数据包中的起点和终点
            Vec3 start = packet.startPos;
            Vec3 end = packet.endPos;

            // [补全逻辑]
            // 根据你提供的粒子构造函数：
            // public PaleLightningArcParticle(ClientLevel level, double startX, double startY, double startZ, Vec3 endPos)

            PaleLightningArcParticle arcParticle = new PaleLightningArcParticle(
                    mc.level,
                    start.x, start.y, start.z, // 起点
                    end                        // 终点 (Vec3)
            );

            // 将粒子添加到世界中
            mc.particleEngine.add(arcParticle);
        }
    }
}