package com.whitecloud233.modid.herobrine_companion.destructiongod.network;

import com.whitecloud233.modid.herobrine_companion.client.fight.particles.PaleLightningArcParticle;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.particle.DestructionGodFaultSplitParticle;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.particle.DestructionGodLightningBeamParticle;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.particle.DestructionGodOrbParticle;
import com.whitecloud233.modid.herobrine_companion.destructiongod.client.particle.DestructionGodThunderSkyNetParticle;
import net.minecraft.client.Minecraft;

public final class DestructionGodClientPacketHandler {
    private DestructionGodClientPacketHandler() {
    }

    public static void handleLightning(DestructionGodLightningPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new DestructionGodLightningBeamParticle(
                    mc.level,
                    packet.startPos,
                    packet.endPos,
                    packet.width
            ));
        }
    }

    public static void handleLightningArc(DestructionGodLightningArcPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new PaleLightningArcParticle(
                    mc.level,
                    packet.startPos.x, packet.startPos.y, packet.startPos.z,
                    packet.endPos,
                    packet.width,
                    packet.lifetimeTicks
            ));
        }
    }

    public static void handleOrb(DestructionGodOrbPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new DestructionGodOrbParticle(
                    mc.level,
                    packet.startPos,
                    packet.impactPos,
                    packet.fallTicks,
                    packet.startRadius,
                    packet.maxRadius,
                    packet.apexHeight
            ));
        }
    }

    public static void handleThunderSkyNet(DestructionGodThunderSkyNetPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new DestructionGodThunderSkyNetParticle(
                    mc.level,
                    packet.center,
                    packet.cloudY,
                    packet.radius,
                    packet.lifetime,
                    packet.seed
            ));
        }
    }

    public static void handleFaultSplit(DestructionGodFaultSplitPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.particleEngine.add(new DestructionGodFaultSplitParticle(
                    mc.level,
                    packet.origin,
                    packet.direction,
                    packet.length,
                    packet.terrainHalfWidth,
                    packet.splitDistance,
                    packet.chargeTicks,
                    packet.lifetime
            ));
        }
    }
}

