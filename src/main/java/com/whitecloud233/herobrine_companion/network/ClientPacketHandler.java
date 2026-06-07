package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.fight.particles.PaleLightningArcParticle;
import com.whitecloud233.herobrine_companion.client.fight.particles.PaleLightningPillarParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPacketHandler {

    public static void handlePaleLightning(PaleLightningPacket packet, IPayloadContext context) {
        Runnable task = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Vec3 groundPos = new Vec3(packet.x(), packet.y(), packet.z());
                mc.particleEngine.add(new PaleLightningPillarParticle(
                        mc.level,
                        packet.x(), packet.y() + 60.0, packet.z(),
                        groundPos,
                        packet.width()
                ));
            }
        };

        if (context != null) {
            context.enqueueWork(task);
        } else {
            task.run();
        }
    }

    public static void handlePaleLightningArc(PaleLightningArcPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 start = packet.startPos;
            Vec3 end = packet.endPos;
            PaleLightningArcParticle arcParticle = new PaleLightningArcParticle(
                    mc.level,
                    start.x, start.y, start.z,
                    end
            );
            mc.particleEngine.add(arcParticle);
        }
    }
}
