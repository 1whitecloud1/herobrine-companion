package com.whitecloud233.modid.herobrine_companion.client.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.fight.HeroAfterimage;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import com.whitecloud233.modid.herobrine_companion.fight.particles.ChallengeArenaSliceParticle;
import com.whitecloud233.modid.herobrine_companion.fight.particles.PaleLightningArcParticle;
import com.whitecloud233.modid.herobrine_companion.fight.particles.PaleLightningPillarParticle;
import com.whitecloud233.modid.herobrine_companion.client.render.ChallengeBlackWhiteFlashHandler;
import com.whitecloud233.modid.herobrine_companion.network.ChallengeAfterimagePacket;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 收包后触发粒子/演出效果的客户端处理入口。由 {@code NetworkClientBridge} 经 DistExecutor 转发。
 * 合并了原 {@code network.ClientPacketHandler} 与上帝类里内联的竞技场切片演出。
 */
public final class ClientFxHandler {
    private ClientFxHandler() {
    }

    public static void handlePaleLightning(PaleLightningPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // 光柱落点
            Vec3 groundPos = new Vec3(packet.x, packet.y, packet.z);
            // 光柱天空起点 (离地 80 格高)
            double skyY = packet.y + 80.0;

            mc.particleEngine.add(new PaleLightningPillarParticle(
                    mc.level,
                    packet.x, skyY, packet.z, // 天空起点
                    groundPos,                // 地面终点
                    packet.width              // 直径
            ));
        }
    }

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

    public static void handleChallengeAfterimage(ChallengeAfterimagePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity entity = mc.level.getEntity(packet.entityId);
        if (entity instanceof HeroEntity hero) {
            hero.addChallengeAfterimage(new HeroAfterimage(
                    new Vec3(packet.x, packet.y, packet.z),
                    packet.yRot,
                    packet.maxTickCount,
                    packet.tickCount
            ));
        }
    }

    public static void handleChallengeArenaSlice(SPacketChallengeArenaSlice packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new ChallengeArenaSliceParticle(
                    mc.level,
                    packet.center,
                    packet.capNormal,
                    packet.cutDirection,
                    packet.cutOffset,
                    packet.arenaRadius,
                    packet.fallTicks,
                    packet.holdTicks
            ));
        }
        if (packet.flash) {
            ChallengeBlackWhiteFlashHandler.trigger();
        }
    }
}
