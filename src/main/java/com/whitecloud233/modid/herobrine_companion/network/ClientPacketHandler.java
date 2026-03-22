package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.fight.particles.PaleLightningArcParticle;
import com.whitecloud233.modid.herobrine_companion.client.fight.particles.PaleLightningPillarParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class ClientPacketHandler {

    public static void handlePaleLightning(PaleLightningPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // 光柱落点
            Vec3 groundPos = new Vec3(packet.x, packet.y, packet.z);
            // 光柱天空起点 (离地 80 格高)
            double skyY = packet.y + 80.0;

            // 直接向客户端世界添加我们刚才写的自定义粒子
            mc.particleEngine.add(new PaleLightningPillarParticle(
                    mc.level,
                    packet.x, skyY, packet.z, // 天空起点
                    groundPos,                // 地面终点
                    packet.width              // 直径
            ));
        }
    }

    // [新增] 处理苍白雷电弧的数据包，完全隔离在客户端执行
    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new PaleLightningArcParticle(
                    mc.level,
                    packet.startPos.x, packet.startPos.y, packet.startPos.z,
                    packet.endPos
            ));
        }
    }
}