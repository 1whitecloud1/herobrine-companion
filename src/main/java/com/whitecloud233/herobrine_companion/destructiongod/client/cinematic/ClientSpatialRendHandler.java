package com.whitecloud233.herobrine_companion.destructiongod.client.cinematic;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.projectile.CleaveBladeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public class ClientSpatialRendHandler {
    private static float monochromePulse;
    private static float whiteFlashPulse;
    private static float shakeStrength;
    private static float faultSplitShakeStrength;
    private static int worldRendCinematicTicks;
    private static int worldRendSilenceTicks;
    private static int worldRendThunderDelay;
    private static int faultSplitShakeTicks;
    private static Vec3 worldRendThunderPos = Vec3.ZERO;
    private static boolean soundPausedForWorldRend;

    public static void startWorldRendCinematic(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        worldRendCinematicTicks = 34;
        worldRendSilenceTicks = 6;
        worldRendThunderDelay = 7;
        worldRendThunderPos = new Vec3(x, y, z);
        monochromePulse = Math.max(monochromePulse, 0.95F);
        whiteFlashPulse = Math.max(whiteFlashPulse, 1.0F);
        shakeStrength = Math.max(shakeStrength, 1.85F);
        if (minecraft.level != null && !soundPausedForWorldRend) {
            minecraft.getSoundManager().pause();
            soundPausedForWorldRend = true;
        }
    }

    public static void startFaultSplitShake(float strength, int durationTicks) {
        faultSplitShakeStrength = Math.max(faultSplitShakeStrength, strength);
        faultSplitShakeTicks = Math.max(faultSplitShakeTicks, durationTicks);
        shakeStrength = Math.max(shakeStrength, strength * 0.85F);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            reset();
            return;
        }

        monochromePulse *= 0.84F;
        whiteFlashPulse *= 0.68F;
        shakeStrength *= 0.78F;
        tickFaultSplitShake(minecraft);

        tickWorldRendCinematic(minecraft);

        Vec3 eye = minecraft.player.getEyePosition();
        AABB searchBox = minecraft.player.getBoundingBox().inflate(18.0D, 8.0D, 18.0D);
        float strongest = 0.0F;

        for (CleaveBladeEntity blade : minecraft.level.getEntitiesOfClass(CleaveBladeEntity.class, searchBox)) {
            Vec3 start = new Vec3(blade.xo, blade.yo, blade.zo);
            Vec3 end = blade.position();
            Vec3 motion = end.subtract(start);
            if (motion.lengthSqr() < 1.0E-4D) {
                motion = blade.getDeltaMovement();
                end = start.add(motion);
            }
            if (motion.lengthSqr() < 1.0E-4D) {
                continue;
            }

            Vec3 liftedStart = start.add(0.0D, 0.9D, 0.0D);
            Vec3 liftedEnd = end.add(0.0D, 0.9D, 0.0D);
            Vec3 closest = closestPointOnSegment(eye, liftedStart, liftedEnd);
            double distance = eye.distanceTo(closest);
            float proximity = (float) Mth.clamp(1.0D - distance / 12.0D, 0.0D, 1.0D);
            if (proximity <= 0.0F) {
                continue;
            }

            Vec3 toSlash = closest.subtract(eye);
            float frontFactor = 0.75F;
            if (toSlash.lengthSqr() > 1.0E-4D) {
                frontFactor = (float) Mth.clamp(0.65D + minecraft.player.getViewVector(1.0F).dot(toSlash.normalize()) * 0.35D, 0.45D, 1.0D);
            }

            strongest = Math.max(strongest, proximity * frontFactor);
        }

        if (strongest > 0.0F) {
            whiteFlashPulse = Math.max(whiteFlashPulse, 0.20F + strongest * 0.80F);
            monochromePulse = Math.max(monochromePulse, 0.16F + strongest * 0.60F);
            shakeStrength = Math.max(shakeStrength, 0.22F + strongest * 1.05F);
        }
    }

    private static void tickFaultSplitShake(Minecraft minecraft) {
        if (faultSplitShakeTicks <= 0) {
            faultSplitShakeStrength *= 0.72F;
            return;
        }

        faultSplitShakeTicks--;
        float time = minecraft.level == null ? 0.0F : minecraft.level.getGameTime();
        float pulse = 0.72F + Math.abs(Mth.sin(time * 0.95F)) * 0.48F;
        shakeStrength = Math.max(shakeStrength, faultSplitShakeStrength * pulse);
        faultSplitShakeStrength *= 0.965F;
    }

    private static void tickWorldRendCinematic(Minecraft minecraft) {
        if (worldRendCinematicTicks > 0) {
            worldRendCinematicTicks--;
            float flicker = (worldRendCinematicTicks % 4 < 2) ? 1.0F : 0.35F;
            monochromePulse = Math.max(monochromePulse, 0.35F + flicker * 0.45F);
            whiteFlashPulse = Math.max(whiteFlashPulse, flicker * 0.70F);
            shakeStrength = Math.max(shakeStrength, 0.45F + flicker * 0.85F);
        }

        if (worldRendSilenceTicks > 0) {
            worldRendSilenceTicks--;
            if (worldRendSilenceTicks == 0 && soundPausedForWorldRend) {
                minecraft.getSoundManager().resume();
                soundPausedForWorldRend = false;
            }
        }

        if (worldRendThunderDelay > 0) {
            worldRendThunderDelay--;
            if (worldRendThunderDelay == 0 && minecraft.level != null) {
                minecraft.level.playSound(minecraft.player, worldRendThunderPos.x, worldRendThunderPos.y, worldRendThunderPos.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.0F, 0.62F);
                minecraft.level.playSound(minecraft.player, worldRendThunderPos.x, worldRendThunderPos.y, worldRendThunderPos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.2F, 0.42F);
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeStrength <= 0.01F) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        float time = (float) ((minecraft.level.getGameTime() + event.getPartialTick()) * 2.35D);
        event.setYaw(event.getYaw() + Mth.sin(time * 1.13F) * shakeStrength * 1.45F);
        event.setPitch(event.getPitch() + Mth.cos(time * 1.47F) * shakeStrength * 0.95F);
        event.setRoll(event.getRoll() + Mth.sin(time * 1.92F) * shakeStrength * 1.75F);
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (monochromePulse <= 0.01F && whiteFlashPulse <= 0.01F) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getGuiGraphics().guiWidth();
        int height = event.getGuiGraphics().guiHeight();

        int monochromeAlpha = Mth.clamp((int) (monochromePulse * 150.0F), 0, 170);
        int whiteAlpha = Mth.clamp((int) (whiteFlashPulse * 190.0F), 0, 220);
        if (monochromeAlpha > 0) {
            graphics.fill(0, 0, width, height, (monochromeAlpha << 24) | 0xD8D8D8);
        }
        if (whiteAlpha > 0) {
            graphics.fill(0, 0, width, height, (whiteAlpha << 24) | 0xFFFFFF);
        }

        int vignetteAlpha = Mth.clamp((int) (monochromePulse * 95.0F), 0, 120);
        if (vignetteAlpha > 0) {
            int sideWidth = Math.max(24, width / 7);
            int topHeight = Math.max(18, height / 8);
            int vignetteColor = (vignetteAlpha << 24);
            graphics.fill(0, 0, width, topHeight, vignetteColor);
            graphics.fill(0, height - topHeight, width, height, vignetteColor);
            graphics.fill(0, 0, sideWidth, height, vignetteColor);
            graphics.fill(width - sideWidth, 0, width, height, vignetteColor);
        }
    }

    @SubscribeEvent
    public static void onClientLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onClientLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    private static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (soundPausedForWorldRend) {
            minecraft.getSoundManager().resume();
        }
        monochromePulse = 0.0F;
        whiteFlashPulse = 0.0F;
        shakeStrength = 0.0F;
        faultSplitShakeStrength = 0.0F;
        worldRendCinematicTicks = 0;
        worldRendSilenceTicks = 0;
        worldRendThunderDelay = 0;
        faultSplitShakeTicks = 0;
        worldRendThunderPos = Vec3.ZERO;
        soundPausedForWorldRend = false;
    }

    private static Vec3 closestPointOnSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSq = segment.lengthSqr();
        if (lengthSq < 1.0E-4D) {
            return start;
        }
        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSq, 0.0D, 1.0D);
        return start.add(segment.scale(t));
    }
}


